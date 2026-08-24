package com.quiz.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quiz.ai.LlmProvider;
import com.quiz.ai.PromptTemplates;
import com.quiz.entity.*;
import com.quiz.exception.ResourceNotFoundException;
import com.quiz.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates and manages knowledge slides for quiz questions.
 * Works for both AI-generated and manually created questions.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KnowledgeSlideService {

    private final LlmProvider llmProvider;
    private final KnowledgeSlideRepository slideRepository;
    private final QuestionRepository questionRepository;
    private final QuizRepository quizRepository;
    private final GeneratedQuestionRepository generatedQuestionRepository;
    private final KnowledgeChunkRepository chunkRepository;
    private final ObjectMapper objectMapper;

    /**
     * Generate a knowledge slide for a single question.
     */
    @Transactional
    public KnowledgeSlide generateForQuestion(Long questionId, User owner) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new ResourceNotFoundException("Question not found"));

        Quiz quiz = question.getQuiz();
        if (!quiz.getCreator().getId().equals(owner.getId())) {
            throw new org.springframework.security.access.AccessDeniedException("Only the quiz creator can generate knowledge slides");
        }

        // Delete existing slide if any
        slideRepository.findByQuestionId(questionId).ifPresent(slideRepository::delete);

        // Determine if we have source context (AI-generated question)
        String sourceContext = null;
        String sourceReference = null;
        boolean hasSource = false;

        // Try to find the generated question that created this
        List<GeneratedQuestion> generatedMatches = findMatchingGeneratedQuestion(question);
        if (!generatedMatches.isEmpty()) {
            GeneratedQuestion gq = generatedMatches.get(0);
            sourceContext = gq.getSourceContext();
            sourceReference = gq.getSourceReference();
            hasSource = sourceContext != null && !sourceContext.isBlank();
        }

        // Get the correct answer text
        String correctAnswerText = switch (question.getCorrectOption()) {
            case "A" -> question.getOptionA();
            case "B" -> question.getOptionB();
            case "C" -> question.getOptionC();
            case "D" -> question.getOptionD();
            default -> "";
        };

        try {
            String json = llmProvider.generateJson(
                    PromptTemplates.KNOWLEDGE_SLIDE_SYSTEM,
                    PromptTemplates.knowledgeSlidePrompt(
                            question.getText(),
                            correctAnswerText,
                            question.getExplanation(),
                            sourceContext,
                            hasSource
                    )
            );

            JsonNode root = objectMapper.readTree(json);
            String title = root.has("title") ? root.get("title").asText() : question.getText();
            String content = root.has("content") ? root.get("content").asText() : json;

            KnowledgeSlide slide = KnowledgeSlide.builder()
                    .question(question)
                    .title(title)
                    .content(content)
                    .sourceGrounded(hasSource)
                    .sourceReference(sourceReference)
                    .build();

            slide = slideRepository.save(slide);
            log.info("Generated knowledge slide {} for question {}", slide.getId(), questionId);
            return slide;

        } catch (Exception e) {
            log.error("Failed to generate knowledge slide for question {}: {}", questionId, e.getMessage());
            throw new RuntimeException("Failed to generate knowledge slide: " + e.getMessage());
        }
    }

    /**
     * Generate knowledge slides for all questions in a quiz.
     */
    @Transactional
    public List<KnowledgeSlide> generateForQuiz(Long quizId, User owner) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new ResourceNotFoundException("Quiz not found"));

        if (!quiz.getCreator().getId().equals(owner.getId())) {
            throw new org.springframework.security.access.AccessDeniedException("Only the quiz creator can generate knowledge slides");
        }

        List<KnowledgeSlide> slides = new ArrayList<>();
        for (Question question : quiz.getQuestions()) {
            try {
                KnowledgeSlide slide = generateForQuestion(question.getId(), owner);
                slides.add(slide);
            } catch (Exception e) {
                log.warn("Failed to generate slide for question {} in quiz {}: {}",
                        question.getId(), quizId, e.getMessage());
                // Continue with remaining questions
            }
        }

        log.info("Generated {} knowledge slides for quiz {}", slides.size(), quizId);
        return slides;
    }

    /**
     * Update/edit an existing knowledge slide.
     */
    @Transactional
    public KnowledgeSlide updateSlide(Long slideId, String title, String content, User owner) {
        KnowledgeSlide slide = slideRepository.findById(slideId)
                .orElseThrow(() -> new ResourceNotFoundException("Knowledge slide not found"));

        Quiz quiz = slide.getQuestion().getQuiz();
        if (!quiz.getCreator().getId().equals(owner.getId())) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied");
        }

        if (title != null && !title.isBlank()) slide.setTitle(title);
        if (content != null && !content.isBlank()) slide.setContent(content);

        return slideRepository.save(slide);
    }

    /**
     * Delete a knowledge slide.
     */
    @Transactional
    public void deleteSlide(Long slideId, User owner) {
        KnowledgeSlide slide = slideRepository.findById(slideId)
                .orElseThrow(() -> new ResourceNotFoundException("Knowledge slide not found"));

        Quiz quiz = slide.getQuestion().getQuiz();
        if (!quiz.getCreator().getId().equals(owner.getId())) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied");
        }

        slideRepository.delete(slide);
    }

    /**
     * Get knowledge slide for a question.
     */
    @Transactional(readOnly = true)
    public KnowledgeSlide getForQuestion(Long questionId) {
        return slideRepository.findByQuestionId(questionId).orElse(null);
    }

    /**
     * Get all knowledge slides for a quiz.
     */
    @Transactional(readOnly = true)
    public List<KnowledgeSlide> getForQuiz(Long quizId) {
        return slideRepository.findByQuestionQuizId(quizId);
    }

    /**
     * Try to find the GeneratedQuestion that corresponds to this Question.
     * Match by question text similarity.
     */
    private List<GeneratedQuestion> findMatchingGeneratedQuestion(Question question) {
        // This is a best-effort match — find generated questions with same text
        try {
            List<GeneratedQuestion> all = generatedQuestionRepository.findAll();
            return all.stream()
                    .filter(gq -> gq.getQuestionText() != null
                            && gq.getQuestionText().equals(question.getText()))
                    .toList();
        } catch (Exception e) {
            return List.of();
        }
    }
}
