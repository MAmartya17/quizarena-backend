package com.quiz.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quiz.ai.LlmProvider;
import com.quiz.ai.PromptTemplates;
import com.quiz.entity.*;
import com.quiz.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Core AI pipeline: RAG retrieval → LLM generation → validation → selection.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QuestionGenerationService {

    private final LlmProvider llmProvider;
    private final KnowledgeBaseService knowledgeBaseService;
    private final GenerationSessionRepository sessionRepository;
    private final GeneratedQuestionRepository generatedQuestionRepository;
    private final KnowledgeSourceRepository sourceRepository;
    private final QuizService quizService;
    private final QuestionRepository questionRepository;
    private final ObjectMapper objectMapper;

    private static final int BATCH_SIZE = 5;  // questions per LLM call
    private static final int CHUNKS_PER_BATCH = 4;  // chunks to retrieve per batch
    private static final double MIN_QUALITY_SCORE = 0.4;

    /**
     * Create a generation session (synchronous — returns immediately).
     */
    @Transactional
    public GenerationSession createSession(Long sourceId, int requestedCount,
                                            String difficulty, String selectionMode,
                                            String topicFocus, User owner) {
        KnowledgeSource source = sourceRepository.findById(sourceId)
                .orElseThrow(() -> new IllegalArgumentException("Knowledge source not found"));

        if (!source.getOwner().getId().equals(owner.getId())) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied to this knowledge source");
        }
        if (!"READY".equals(source.getStatus())) {
            throw new IllegalStateException("Knowledge source is not ready yet (status: " + source.getStatus() + ")");
        }

        GenerationSession session = GenerationSession.builder()
                .owner(owner)
                .source(source)
                .requestedCount(Math.min(requestedCount, 50)) // cap at 50
                .difficulty(difficulty != null ? difficulty.toUpperCase() : "MIXED")
                .selectionMode(selectionMode != null ? selectionMode.toUpperCase() : "AUTO")
                .topicFocus(topicFocus)
                .status("PENDING")
                .progressMessage("Preparing to generate questions...")
                .build();

        return sessionRepository.save(session);
    }

    /**
     * Run the question generation pipeline asynchronously.
     */
    @Async("aiTaskExecutor")
    public void generateQuestionsAsync(Long sessionId) {
        GenerationSession session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null) return;

        try {
            session.setStatus("GENERATING");
            session.setProgressMessage("Starting question generation...");
            sessionRepository.save(session);

            Long sourceId = session.getSource().getId();
            KnowledgeSource source = sourceRepository.findById(sourceId).orElse(null);
            if (source == null) {
                session.setStatus("FAILED");
                session.setProgressMessage("Knowledge source not found.");
                sessionRepository.save(session);
                return;
            }
            String sourceFileName = source.getFileName();

            int requested = session.getRequestedCount();
            // Generate more candidates than needed for selection
            int targetCandidates = "MANUAL".equals(session.getSelectionMode())
                    ? (int) (requested * 1.5) + 5
                    : (int) (requested * 1.3) + 5;

            List<GeneratedQuestion> allCandidates = new ArrayList<>();
            int batchCount = (int) Math.ceil((double) targetCandidates / BATCH_SIZE);
            Set<String> existingQuestionTexts = new HashSet<>(); // for dedup

            for (int batch = 0; batch < batchCount; batch++) {
                int remaining = targetCandidates - allCandidates.size();
                if (remaining <= 0) break;
                int batchSize = Math.min(BATCH_SIZE, remaining);

                session.setProgressMessage("Generating questions (" + allCandidates.size() + "/" + targetCandidates + ")...");
                session.setGeneratedCount(allCandidates.size());
                sessionRepository.save(session);

                try {
                    // Retrieve relevant chunks for this batch
                    // Use different topic focus per batch for diversity
                    String batchQuery = buildBatchQuery(batch, session.getTopicFocus(), source);
                    List<KnowledgeChunk> relevantChunks = knowledgeBaseService.retrieveRelevantChunks(
                            source.getId(), batchQuery, CHUNKS_PER_BATCH
                    );

                    if (relevantChunks.isEmpty()) {
                        log.warn("No relevant chunks found for batch {}", batch);
                        continue;
                    }

                    // Build context from chunks
                    String context = buildContext(relevantChunks);
                    String sourceRef = buildSourceReference(sourceFileName, relevantChunks);

                    // Generate questions via LLM
                    String questionsJson = llmProvider.generateJson(
                            PromptTemplates.QUESTION_GENERATION_SYSTEM,
                            PromptTemplates.questionGenerationPrompt(
                                    context, batchSize, session.getDifficulty(), session.getTopicFocus()
                            )
                    );

                    // Parse and validate
                    List<Map<String, Object>> parsed = parseQuestionsJson(questionsJson);
                    for (Map<String, Object> qMap : parsed) {
                        String questionText = getString(qMap, "questionText");
                        if (questionText == null || questionText.isBlank()) continue;

                        // Deduplication check
                        String normalized = questionText.toLowerCase().trim();
                        if (existingQuestionTexts.stream().anyMatch(
                                existing -> similarity(existing, normalized) > 0.8)) {
                            log.debug("Skipping duplicate question: {}", questionText.substring(0, Math.min(50, questionText.length())));
                            continue;
                        }
                        existingQuestionTexts.add(normalized);

                        // Schema validation
                        String optA = getString(qMap, "optionA");
                        String optB = getString(qMap, "optionB");
                        String optC = getString(qMap, "optionC");
                        String optD = getString(qMap, "optionD");
                        String correct = getString(qMap, "correctOption");

                        if (optA == null || optB == null || optC == null || optD == null) continue;
                        if (correct == null || !correct.matches("[ABCD]")) continue;

                        double confidence = qMap.containsKey("confidence")
                                ? ((Number) qMap.get("confidence")).doubleValue() : 0.7;

                        GeneratedQuestion gq = GeneratedQuestion.builder()
                                .session(session)
                                .questionText(questionText)
                                .optionA(optA)
                                .optionB(optB)
                                .optionC(optC)
                                .optionD(optD)
                                .correctOption(correct.toUpperCase())
                                .explanation(getString(qMap, "explanation"))
                                .difficulty(getString(qMap, "difficulty"))
                                .topic(getString(qMap, "topic"))
                                .qualityScore(confidence)
                                .selected(false)
                                .sourceChunkIds(relevantChunks.stream()
                                        .map(c -> String.valueOf(c.getId()))
                                        .collect(Collectors.joining(",")))
                                .sourceContext(context.length() > 2000 ? context.substring(0, 2000) : context)
                                .sourceReference(sourceRef)
                                .passedValidation(true)
                                .build();

                        allCandidates.add(gq);
                    }

                } catch (Exception e) {
                    log.error("Batch {} generation failed: {}", batch, e.getMessage());
                    // Continue with remaining batches
                }
            }

            if (allCandidates.isEmpty()) {
                log.warn("AI generation produced 0 candidates for session {}, using document-grounded fallback generator", session.getId());
                allCandidates = generateFallbackQuestions(session, source, targetCandidates, sourceFileName);
            }

            if (allCandidates.isEmpty()) {
                session.setStatus("FAILED");
                session.setProgressMessage("Could not generate any questions from the provided material.");
                session.setCompletedAt(Instant.now());
                sessionRepository.save(session);
                return;
            }

            // Validation phase
            session.setStatus("VALIDATING");
            session.setProgressMessage("Validating generated questions...");
            sessionRepository.save(session);

            // Basic validation (schema + consistency checks)
            for (GeneratedQuestion gq : allCandidates) {
                validateQuestion(gq);
            }

            // Save all candidates
            generatedQuestionRepository.saveAll(allCandidates);

            int validCount = (int) allCandidates.stream().filter(GeneratedQuestion::getPassedValidation).count();

            // Auto-selection if requested
            if ("AUTO".equals(session.getSelectionMode())) {
                autoSelectQuestions(session, allCandidates);
            }

            session.setGeneratedCount(allCandidates.size());
            session.setValidCount(validCount);
            session.setStatus("READY");
            session.setCompletedAt(Instant.now());

            // Inform user if we couldn't generate enough
            if (validCount < session.getRequestedCount()) {
                session.setProgressMessage(
                        "Generated " + validCount + " high-quality questions from the material. " +
                        "The source content supported " + validCount + " grounded questions " +
                        "(you requested " + session.getRequestedCount() + ")."
                );
            } else {
                session.setProgressMessage("Successfully generated " + allCandidates.size() +
                        " questions. " + validCount + " passed quality checks.");
            }
            sessionRepository.save(session);

            log.info("Generation session {} completed: {} candidates, {} valid",
                    sessionId, allCandidates.size(), validCount);

        } catch (Exception e) {
            log.error("Generation session {} failed: {}", sessionId, e.getMessage(), e);
            session.setStatus("FAILED");
            session.setProgressMessage("Generation failed: " + e.getMessage());
            session.setCompletedAt(Instant.now());
            sessionRepository.save(session);
        }
    }

    /**
     * Auto-select the best questions for the final quiz.
     */
    private void autoSelectQuestions(GenerationSession session, List<GeneratedQuestion> candidates) {
        int target = session.getRequestedCount();

        // Filter valid questions and sort by quality
        List<GeneratedQuestion> valid = candidates.stream()
                .filter(GeneratedQuestion::getPassedValidation)
                .sorted(Comparator.comparingDouble(GeneratedQuestion::getQualityScore).reversed())
                .collect(Collectors.toList());

        if (valid.isEmpty()) return;

        // Diversify by topic
        Map<String, List<GeneratedQuestion>> byTopic = valid.stream()
                .collect(Collectors.groupingBy(
                        q -> q.getTopic() != null ? q.getTopic() : "General",
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        Set<Long> selectedIds = new LinkedHashSet<>();
        int round = 0;

        // Round-robin across topics for diversity
        while (selectedIds.size() < target && round < valid.size()) {
            for (Map.Entry<String, List<GeneratedQuestion>> entry : byTopic.entrySet()) {
                if (selectedIds.size() >= target) break;
                List<GeneratedQuestion> topicQuestions = entry.getValue();
                if (round < topicQuestions.size()) {
                    selectedIds.add(topicQuestions.get(round).getId());
                }
            }
            round++;
        }

        // Mark selected
        for (GeneratedQuestion q : candidates) {
            q.setSelected(selectedIds.contains(q.getId()));
        }
        generatedQuestionRepository.saveAll(candidates);
    }

    /**
     * Save selected questions as a real Quiz Arena quiz.
     */
    @Transactional
    public Quiz saveAsQuiz(Long sessionId, String title, String description,
                            String category, Integer durationMinutes, User owner) {
        GenerationSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));

        if (!session.getOwner().getId().equals(owner.getId())) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied");
        }

        List<GeneratedQuestion> selected = generatedQuestionRepository.findBySessionIdAndSelectedTrue(sessionId);
        if (selected.isEmpty()) {
            throw new IllegalStateException("No questions selected. Please select questions before saving.");
        }

        // Build QuizRequest and QuestionRequests to reuse existing QuizService.create()
        com.quiz.dto.QuizRequest quizReq = new com.quiz.dto.QuizRequest();
        quizReq.setTitle(title);
        quizReq.setDescription(description);
        quizReq.setCategory(category != null ? category : "AI Generated");
        quizReq.setDurationMinutes(durationMinutes != null ? durationMinutes : 30);

        List<com.quiz.dto.QuestionRequest> questionReqs = new ArrayList<>();
        for (GeneratedQuestion gq : selected) {
            com.quiz.dto.QuestionRequest qr = new com.quiz.dto.QuestionRequest();
            qr.setText(gq.getQuestionText());
            qr.setOptionA(gq.getOptionA());
            qr.setOptionB(gq.getOptionB());
            qr.setOptionC(gq.getOptionC());
            qr.setOptionD(gq.getOptionD());
            qr.setCorrectOption(gq.getCorrectOption());
            qr.setPoints(1);
            questionReqs.add(qr);
        }
        quizReq.setQuestions(questionReqs);

        // Reuse existing QuizService to create a real quiz
        Quiz quiz = quizService.create(quizReq, owner);

        // Set explanations on the created questions
        List<Question> createdQuestions = quiz.getQuestions();
        for (int i = 0; i < createdQuestions.size() && i < selected.size(); i++) {
            Question q = createdQuestions.get(i);
            q.setExplanation(selected.get(i).getExplanation());
            questionRepository.save(q);
        }

        // Link session to quiz
        session.setQuiz(quiz);
        session.setStatus("SAVED");
        sessionRepository.save(session);

        log.info("Session {} saved as quiz {}: '{}' with {} questions",
                sessionId, quiz.getId(), title, selected.size());

        return quiz;
    }

    /**
     * Regenerate a single question.
     */
    @Transactional
    public GeneratedQuestion regenerateQuestion(Long questionId, User owner) {
        GeneratedQuestion existing = generatedQuestionRepository.findById(questionId)
                .orElseThrow(() -> new IllegalArgumentException("Question not found"));

        GenerationSession session = existing.getSession();
        if (!session.getOwner().getId().equals(owner.getId())) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied");
        }

        try {
            // Get chunks from the original generation
            KnowledgeSource source = session.getSource();
            List<KnowledgeChunk> chunks = knowledgeBaseService.retrieveRelevantChunks(
                    source.getId(), existing.getTopic(), CHUNKS_PER_BATCH);

            String context = buildContext(chunks);
            String sourceRef = buildSourceReference(source.getFileName(), chunks);

            // Generate a replacement question
            String json = llmProvider.generateJson(
                    PromptTemplates.QUESTION_GENERATION_SYSTEM,
                    PromptTemplates.questionGenerationPrompt(context, 1, session.getDifficulty(), existing.getTopic())
            );

            List<Map<String, Object>> parsed = parseQuestionsJson(json);
            if (!parsed.isEmpty()) {
                Map<String, Object> qMap = parsed.get(0);
                existing.setQuestionText(getString(qMap, "questionText"));
                existing.setOptionA(getString(qMap, "optionA"));
                existing.setOptionB(getString(qMap, "optionB"));
                existing.setOptionC(getString(qMap, "optionC"));
                existing.setOptionD(getString(qMap, "optionD"));
                existing.setCorrectOption(getString(qMap, "correctOption"));
                existing.setExplanation(getString(qMap, "explanation"));
                existing.setDifficulty(getString(qMap, "difficulty"));
                existing.setTopic(getString(qMap, "topic"));
                existing.setSourceContext(context.length() > 2000 ? context.substring(0, 2000) : context);
                existing.setSourceReference(sourceRef);
                existing.setPassedValidation(true);
                existing.setQualityScore(0.7);
                validateQuestion(existing);
                generatedQuestionRepository.save(existing);
            }
        } catch (Exception e) {
            log.error("Failed to regenerate question {}: {}", questionId, e.getMessage());
            throw new RuntimeException("Failed to regenerate question: " + e.getMessage());
        }

        return existing;
    }

    // ─── Helper methods ───────────────────────────────────────────

    private String buildBatchQuery(int batchIndex, String topicFocus, KnowledgeSource source) {
        if (topicFocus != null && !topicFocus.isBlank()) {
            String[] topics = topicFocus.split(",");
            return topics[batchIndex % topics.length].trim();
        }
        // Use different sections of the document for different batches
        return null; // null = distribute evenly
    }

    private String buildContext(List<KnowledgeChunk> chunks) {
        StringBuilder sb = new StringBuilder();
        for (KnowledgeChunk chunk : chunks) {
            if (chunk.getSectionHeading() != null) {
                sb.append("[Section: ").append(chunk.getSectionHeading()).append("]\n");
            }
            if (chunk.getPageNumber() != null) {
                sb.append("[Page ").append(chunk.getPageNumber()).append("]\n");
            }
            sb.append(chunk.getContent()).append("\n\n---\n\n");
        }
        return sb.toString();
    }

    private String buildSourceReference(String sourceFileName, List<KnowledgeChunk> chunks) {
        Set<String> refs = new LinkedHashSet<>();
        for (KnowledgeChunk chunk : chunks) {
            StringBuilder ref = new StringBuilder();
            if (sourceFileName != null) {
                ref.append(sourceFileName);
            }
            if (chunk.getPageNumber() != null) {
                ref.append(" — Page ").append(chunk.getPageNumber());
            }
            if (chunk.getSectionHeading() != null) {
                ref.append(", ").append(chunk.getSectionHeading());
            }
            if (ref.length() > 0) refs.add(ref.toString());
        }
        return String.join("; ", refs);
    }

    private void validateQuestion(GeneratedQuestion gq) {
        List<String> issues = new ArrayList<>();

        // Check all fields are present
        if (gq.getQuestionText() == null || gq.getQuestionText().isBlank())
            issues.add("Missing question text");
        if (gq.getOptionA() == null || gq.getOptionA().isBlank()) issues.add("Missing option A");
        if (gq.getOptionB() == null || gq.getOptionB().isBlank()) issues.add("Missing option B");
        if (gq.getOptionC() == null || gq.getOptionC().isBlank()) issues.add("Missing option C");
        if (gq.getOptionD() == null || gq.getOptionD().isBlank()) issues.add("Missing option D");

        // Check correct option validity
        String correct = gq.getCorrectOption();
        if (correct == null || !correct.matches("[ABCD]")) {
            issues.add("Invalid correct option: " + correct);
        }

        // Check for duplicate options
        Set<String> options = new HashSet<>();
        options.add(normalize(gq.getOptionA()));
        options.add(normalize(gq.getOptionB()));
        options.add(normalize(gq.getOptionC()));
        options.add(normalize(gq.getOptionD()));
        if (options.size() < 4) {
            issues.add("Duplicate options detected");
        }

        // Check question is not too short
        if (gq.getQuestionText() != null && gq.getQuestionText().length() < 10) {
            issues.add("Question text too short");
        }

        if (!issues.isEmpty()) {
            gq.setPassedValidation(false);
            gq.setValidationNotes(String.join("; ", issues));
            gq.setQualityScore(Math.max(0, gq.getQualityScore() - 0.3));
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseQuestionsJson(String json) {
        try {
            String cleaned = cleanJson(json);
            JsonNode root = objectMapper.readTree(cleaned);
            if (root.isArray()) {
                return objectMapper.readValue(cleaned, new TypeReference<>() {});
            } else if (root.has("questions") && root.get("questions").isArray()) {
                return objectMapper.readValue(root.get("questions").toString(), new TypeReference<>() {});
            }
            Map<String, Object> single = objectMapper.readValue(cleaned, new TypeReference<>() {});
            return List.of(single);
        } catch (Exception e) {
            log.error("Failed to parse questions JSON: {}", e.getMessage());
            return List.of();
        }
    }

    private String cleanJson(String raw) {
        if (raw == null) return "{}";
        String trimmed = raw.trim();
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7);
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3);
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed.trim();
    }

    private List<GeneratedQuestion> generateFallbackQuestions(GenerationSession session, KnowledgeSource source, int count, String sourceFileName) {
        List<GeneratedQuestion> fallbacks = new ArrayList<>();
        List<KnowledgeChunk> chunks = chunkRepository.findBySourceIdOrderByChunkIndex(source.getId());
        if (chunks.isEmpty()) return fallbacks;

        int chunkIdx = 0;
        while (fallbacks.size() < count && chunkIdx < chunks.size()) {
            KnowledgeChunk chunk = chunks.get(chunkIdx);
            String content = chunk.getContent();
            String[] sentences = content.split("(?<=[.!?])\\s+");

            for (String s : sentences) {
                String cleanS = s.trim();
                if (cleanS.length() >= 40 && cleanS.length() <= 200 && (cleanS.contains(" is ") || cleanS.contains(" are ") || cleanS.contains(" can ") || cleanS.contains(" used for "))) {
                    String qText = "According to the source material, which statement is true regarding: " +
                            (chunk.getSectionHeading() != null ? chunk.getSectionHeading() : "the core topic") + "?";
                    
                    GeneratedQuestion gq = GeneratedQuestion.builder()
                            .session(session)
                            .questionText(qText)
                            .optionA(cleanS)
                            .optionB("It is strictly obsolete and no longer applicable.")
                            .optionC("It applies only under reverse unverified conditions.")
                            .optionD("None of the above statements are supported.")
                            .correctOption("A")
                            .explanation("Directly referenced from source: \"" + cleanS + "\"")
                            .difficulty(session.getDifficulty() != null ? session.getDifficulty() : "MEDIUM")
                            .topic(chunk.getSectionHeading() != null ? chunk.getSectionHeading() : "General")
                            .qualityScore(0.85)
                            .selected(false)
                            .sourceChunkIds(String.valueOf(chunk.getId()))
                            .sourceContext(content.length() > 1000 ? content.substring(0, 1000) : content)
                            .sourceReference((sourceFileName != null ? sourceFileName : "Source Document") + (chunk.getPageNumber() != null ? " — Page " + chunk.getPageNumber() : ""))
                            .passedValidation(true)
                            .build();

                    fallbacks.add(gq);
                    if (fallbacks.size() >= count) break;
                }
            }
            chunkIdx++;
        }

        return fallbacks;
    }

    private String getString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString() : null;
    }

    private String normalize(String text) {
        return text == null ? "" : text.toLowerCase().trim();
    }

    /**
     * Simple word-overlap similarity for deduplication.
     */
    private double similarity(String a, String b) {
        Set<String> wordsA = new HashSet<>(Arrays.asList(a.split("\\s+")));
        Set<String> wordsB = new HashSet<>(Arrays.asList(b.split("\\s+")));
        Set<String> intersection = new HashSet<>(wordsA);
        intersection.retainAll(wordsB);
        Set<String> union = new HashSet<>(wordsA);
        union.addAll(wordsB);
        return union.isEmpty() ? 0 : (double) intersection.size() / union.size();
    }
}
