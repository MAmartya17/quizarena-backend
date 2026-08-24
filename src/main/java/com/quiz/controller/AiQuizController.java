package com.quiz.controller;

import com.quiz.dto.AiDtos;
import com.quiz.dto.AiDtos.*;
import com.quiz.dto.QuizSummaryDTO;
import com.quiz.entity.*;
import com.quiz.repository.*;
import com.quiz.security.UserPrincipal;
import com.quiz.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * REST controller for AI quiz generation features.
 * All endpoints are authenticated (covered by SecurityConfig .anyRequest().authenticated()).
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Slf4j
public class AiQuizController {

    private final KnowledgeBaseService knowledgeBaseService;
    private final QuestionGenerationService questionGenerationService;
    private final KnowledgeSlideService knowledgeSlideService;
    private final KnowledgeSourceRepository sourceRepository;
    private final GenerationSessionRepository sessionRepository;
    private final GeneratedQuestionRepository generatedQuestionRepository;

    // ═══════════════════════════════════════════════════════════
    // KNOWLEDGE SOURCES
    // ═══════════════════════════════════════════════════════════

    /** Upload a PDF and create a knowledge source. */
    @PostMapping("/upload")
    public ResponseEntity<KnowledgeSourceResponse> uploadPdf(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserPrincipal principal) {
        try {
            byte[] fileBytes = file.getBytes();
            KnowledgeSource source = knowledgeBaseService.createFromPdf(file, principal.getUser());
            // Trigger async processing
            knowledgeBaseService.processSourceAsync(source.getId(), fileBytes);
            return ResponseEntity.ok(KnowledgeSourceResponse.from(source));
        } catch (Exception e) {
            log.error("Upload failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(null);
        }
    }

    /** Create a knowledge source from pasted text. */
    @PostMapping("/upload/text")
    public ResponseEntity<KnowledgeSourceResponse> uploadText(
            @RequestBody TextSourceRequest req,
            @AuthenticationPrincipal UserPrincipal principal) {
        KnowledgeSource source = knowledgeBaseService.createFromText(
                req.getText(), req.getTitle(), principal.getUser());
        // Process text synchronously (much faster than PDF)
        knowledgeBaseService.processSourceAsync(source.getId(), null);
        return ResponseEntity.ok(KnowledgeSourceResponse.from(source));
    }

    /** List the current user's knowledge sources. */
    @GetMapping("/sources")
    public List<KnowledgeSourceResponse> listSources(
            @AuthenticationPrincipal UserPrincipal principal) {
        return sourceRepository.findByOwnerIdOrderByCreatedAtDesc(principal.getId())
                .stream().map(KnowledgeSourceResponse::from).toList();
    }

    /** Get a specific knowledge source with details. */
    @GetMapping("/sources/{id}")
    public ResponseEntity<KnowledgeSourceResponse> getSource(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        KnowledgeSource source = sourceRepository.findById(id)
                .orElseThrow(() -> new com.quiz.exception.ResourceNotFoundException("Source not found"));
        ensureOwner(source.getOwner(), principal);
        return ResponseEntity.ok(KnowledgeSourceResponse.from(source));
    }

    /** Poll processing status of a knowledge source. */
    @GetMapping("/sources/{id}/status")
    public ResponseEntity<Map<String, Object>> getSourceStatus(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        KnowledgeSource source = sourceRepository.findById(id)
                .orElseThrow(() -> new com.quiz.exception.ResourceNotFoundException("Source not found"));
        ensureOwner(source.getOwner(), principal);
        return ResponseEntity.ok(Map.of(
                "status", source.getStatus(),
                "totalChunks", source.getTotalChunks() != null ? source.getTotalChunks() : 0,
                "totalPages", source.getTotalPages() != null ? source.getTotalPages() : 0,
                "errorMessage", source.getErrorMessage() != null ? source.getErrorMessage() : ""
        ));
    }

    // ═══════════════════════════════════════════════════════════
    // GENERATION SESSIONS
    // ═══════════════════════════════════════════════════════════

    /** Start a new question generation session. */
    @PostMapping("/generate")
    public ResponseEntity<GenerationSessionResponse> startGeneration(
            @RequestBody GenerateRequest req,
            @AuthenticationPrincipal UserPrincipal principal) {
        GenerationSession session = questionGenerationService.createSession(
                req.getSourceId(), req.getQuestionCount(),
                req.getDifficulty(), req.getSelectionMode(),
                req.getTopicFocus(), principal.getUser()
        );
        // Trigger async generation
        questionGenerationService.generateQuestionsAsync(session.getId());
        return ResponseEntity.ok(GenerationSessionResponse.from(session));
    }

    /** Get generation session status and progress. */
    @GetMapping("/sessions/{id}")
    public ResponseEntity<GenerationSessionResponse> getSession(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        GenerationSession session = sessionRepository.findById(id)
                .orElseThrow(() -> new com.quiz.exception.ResourceNotFoundException("Session not found"));
        ensureOwner(session.getOwner(), principal);
        return ResponseEntity.ok(GenerationSessionResponse.from(session));
    }

    /** Get generated question candidates for a session. */
    @GetMapping("/sessions/{id}/questions")
    public List<GeneratedQuestionResponse> getSessionQuestions(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        GenerationSession session = sessionRepository.findById(id)
                .orElseThrow(() -> new com.quiz.exception.ResourceNotFoundException("Session not found"));
        ensureOwner(session.getOwner(), principal);
        return generatedQuestionRepository.findBySessionIdOrderByCreatedAt(id)
                .stream().map(GeneratedQuestionResponse::from).toList();
    }

    /** Edit a generated question. */
    @PutMapping("/sessions/{sessionId}/questions/{questionId}")
    public ResponseEntity<GeneratedQuestionResponse> updateQuestion(
            @PathVariable Long sessionId, @PathVariable Long questionId,
            @RequestBody UpdateQuestionRequest req,
            @AuthenticationPrincipal UserPrincipal principal) {
        GenerationSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new com.quiz.exception.ResourceNotFoundException("Session not found"));
        ensureOwner(session.getOwner(), principal);

        GeneratedQuestion q = generatedQuestionRepository.findById(questionId)
                .orElseThrow(() -> new com.quiz.exception.ResourceNotFoundException("Question not found"));

        if (req.getQuestionText() != null) q.setQuestionText(req.getQuestionText());
        if (req.getOptionA() != null) q.setOptionA(req.getOptionA());
        if (req.getOptionB() != null) q.setOptionB(req.getOptionB());
        if (req.getOptionC() != null) q.setOptionC(req.getOptionC());
        if (req.getOptionD() != null) q.setOptionD(req.getOptionD());
        if (req.getCorrectOption() != null) q.setCorrectOption(req.getCorrectOption());
        if (req.getExplanation() != null) q.setExplanation(req.getExplanation());
        if (req.getDifficulty() != null) q.setDifficulty(req.getDifficulty());

        generatedQuestionRepository.save(q);
        return ResponseEntity.ok(GeneratedQuestionResponse.from(q));
    }

    /** Regenerate a single question. */
    @PostMapping("/sessions/{sessionId}/questions/{questionId}/regenerate")
    public ResponseEntity<GeneratedQuestionResponse> regenerateQuestion(
            @PathVariable Long sessionId, @PathVariable Long questionId,
            @AuthenticationPrincipal UserPrincipal principal) {
        GeneratedQuestion q = questionGenerationService.regenerateQuestion(questionId, principal.getUser());
        return ResponseEntity.ok(GeneratedQuestionResponse.from(q));
    }

    /** Update question selection (list of selected question IDs). */
    @PutMapping("/sessions/{id}/select")
    public ResponseEntity<Map<String, Object>> updateSelection(
            @PathVariable Long id,
            @RequestBody SelectQuestionsRequest req,
            @AuthenticationPrincipal UserPrincipal principal) {
        GenerationSession session = sessionRepository.findById(id)
                .orElseThrow(() -> new com.quiz.exception.ResourceNotFoundException("Session not found"));
        ensureOwner(session.getOwner(), principal);

        List<GeneratedQuestion> questions = generatedQuestionRepository.findBySessionIdOrderByCreatedAt(id);
        for (GeneratedQuestion q : questions) {
            q.setSelected(req.getSelectedIds().contains(q.getId()));
        }
        generatedQuestionRepository.saveAll(questions);

        long selectedCount = questions.stream().filter(GeneratedQuestion::getSelected).count();
        return ResponseEntity.ok(Map.of("selectedCount", selectedCount));
    }

    /** Save selected questions as a real Quiz Arena quiz. */
    @PostMapping("/sessions/{id}/save")
    public ResponseEntity<QuizSummaryDTO> saveAsQuiz(
            @PathVariable Long id,
            @RequestBody SaveQuizRequest req,
            @AuthenticationPrincipal UserPrincipal principal) {
        Quiz quiz = questionGenerationService.saveAsQuiz(
                id, req.getTitle(), req.getDescription(),
                req.getCategory(), req.getDurationMinutes(),
                principal.getUser()
        );
        return ResponseEntity.ok(QuizSummaryDTO.from(quiz));
    }

    // ═══════════════════════════════════════════════════════════
    // KNOWLEDGE SLIDES
    // ═══════════════════════════════════════════════════════════

    /** Generate a knowledge slide for a single question. */
    @PostMapping("/knowledge-slide/question/{questionId}")
    public ResponseEntity<KnowledgeSlideResponse> generateSlide(
            @PathVariable Long questionId,
            @AuthenticationPrincipal UserPrincipal principal) {
        KnowledgeSlide slide = knowledgeSlideService.generateForQuestion(questionId, principal.getUser());
        return ResponseEntity.ok(KnowledgeSlideResponse.from(slide));
    }

    /** Generate knowledge slides for all questions in a quiz. */
    @PostMapping("/knowledge-slide/quiz/{quizId}")
    public ResponseEntity<List<KnowledgeSlideResponse>> generateSlidesForQuiz(
            @PathVariable Long quizId,
            @AuthenticationPrincipal UserPrincipal principal) {
        List<KnowledgeSlide> slides = knowledgeSlideService.generateForQuiz(quizId, principal.getUser());
        return ResponseEntity.ok(slides.stream().map(KnowledgeSlideResponse::from).toList());
    }

    /** Get all knowledge slides for a quiz. */
    @GetMapping("/knowledge-slide/quiz/{quizId}")
    public ResponseEntity<List<KnowledgeSlideResponse>> getSlidesForQuiz(
            @PathVariable Long quizId) {
        List<KnowledgeSlide> slides = knowledgeSlideService.getForQuiz(quizId);
        return ResponseEntity.ok(slides.stream().map(KnowledgeSlideResponse::from).toList());
    }

    /** Get knowledge slide for a question. */
    @GetMapping("/knowledge-slide/question/{questionId}")
    public ResponseEntity<?> getSlide(@PathVariable Long questionId) {
        KnowledgeSlide slide = knowledgeSlideService.getForQuestion(questionId);
        if (slide == null) {
            return ResponseEntity.ok(Map.of("exists", false));
        }
        return ResponseEntity.ok(KnowledgeSlideResponse.from(slide));
    }

    /** Update a knowledge slide. */
    @PutMapping("/knowledge-slide/{slideId}")
    public ResponseEntity<KnowledgeSlideResponse> updateSlide(
            @PathVariable Long slideId,
            @RequestBody UpdateSlideRequest req,
            @AuthenticationPrincipal UserPrincipal principal) {
        KnowledgeSlide slide = knowledgeSlideService.updateSlide(
                slideId, req.getTitle(), req.getContent(), principal.getUser());
        return ResponseEntity.ok(KnowledgeSlideResponse.from(slide));
    }

    /** Delete a knowledge slide. */
    @DeleteMapping("/knowledge-slide/{slideId}")
    public ResponseEntity<?> deleteSlide(
            @PathVariable Long slideId,
            @AuthenticationPrincipal UserPrincipal principal) {
        knowledgeSlideService.deleteSlide(slideId, principal.getUser());
        return ResponseEntity.ok(Map.of("deleted", true));
    }

    // ─── Utility ──────────────────────────────────────────────

    private void ensureOwner(User resourceOwner, UserPrincipal principal) {
        if (!resourceOwner.getId().equals(principal.getId())) {
            throw new AccessDeniedException("Access denied");
        }
    }
}
