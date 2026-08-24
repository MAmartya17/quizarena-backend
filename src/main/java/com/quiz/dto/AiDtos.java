package com.quiz.dto;

import com.quiz.entity.GeneratedQuestion;
import com.quiz.entity.GenerationSession;
import com.quiz.entity.KnowledgeSlide;
import com.quiz.entity.KnowledgeSource;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * DTOs for the AI quiz generation endpoints.
 */
public final class AiDtos {

    private AiDtos() {}

    // ── Request DTOs ──────────────────────────────────────────────

    @Data
    public static class TextSourceRequest {
        private String text;
        private String title;
    }

    @Data
    public static class GenerateRequest {
        private Long sourceId;
        private Integer questionCount = 10;
        private String difficulty = "MIXED";
        private String selectionMode = "AUTO";
        private String topicFocus;
    }

    @Data
    public static class SaveQuizRequest {
        private String title;
        private String description;
        private String category;
        private Integer durationMinutes = 30;
    }

    @Data
    public static class UpdateQuestionRequest {
        private String questionText;
        private String optionA;
        private String optionB;
        private String optionC;
        private String optionD;
        private String correctOption;
        private String explanation;
        private String difficulty;
    }

    @Data
    public static class SelectQuestionsRequest {
        private List<Long> selectedIds;
    }

    @Data
    public static class UpdateSlideRequest {
        private String title;
        private String content;
    }

    // ── Response DTOs ─────────────────────────────────────────────

    @Data @AllArgsConstructor @NoArgsConstructor
    public static class KnowledgeSourceResponse {
        private Long id;
        private String fileName;
        private String sourceType;
        private Long fileSizeBytes;
        private Integer totalPages;
        private Integer totalChunks;
        private String status;
        private String errorMessage;
        private String detectedTopics;
        private Instant createdAt;

        public static KnowledgeSourceResponse from(KnowledgeSource s) {
            return new KnowledgeSourceResponse(
                    s.getId(), s.getFileName(), s.getSourceType(),
                    s.getFileSizeBytes(), s.getTotalPages(), s.getTotalChunks(),
                    s.getStatus(), s.getErrorMessage(), s.getDetectedTopics(),
                    s.getCreatedAt()
            );
        }
    }

    @Data @AllArgsConstructor @NoArgsConstructor
    public static class GenerationSessionResponse {
        private Long id;
        private Long sourceId;
        private String sourceFileName;
        private Long quizId;
        private Integer requestedCount;
        private String difficulty;
        private String selectionMode;
        private String status;
        private String progressMessage;
        private Integer generatedCount;
        private Integer validCount;
        private Instant createdAt;
        private Instant completedAt;

        public static GenerationSessionResponse from(GenerationSession s) {
            return new GenerationSessionResponse(
                    s.getId(), s.getSource().getId(), s.getSource().getFileName(),
                    s.getQuiz() != null ? s.getQuiz().getId() : null,
                    s.getRequestedCount(), s.getDifficulty(), s.getSelectionMode(),
                    s.getStatus(), s.getProgressMessage(),
                    s.getGeneratedCount(), s.getValidCount(),
                    s.getCreatedAt(), s.getCompletedAt()
            );
        }
    }

    @Data @AllArgsConstructor @NoArgsConstructor
    public static class GeneratedQuestionResponse {
        private Long id;
        private String questionText;
        private String optionA;
        private String optionB;
        private String optionC;
        private String optionD;
        private String correctOption;
        private String explanation;
        private String difficulty;
        private String topic;
        private Double qualityScore;
        private Boolean selected;
        private String sourceReference;
        private String sourceContext;
        private Boolean passedValidation;
        private String validationNotes;

        public static GeneratedQuestionResponse from(GeneratedQuestion q) {
            return new GeneratedQuestionResponse(
                    q.getId(), q.getQuestionText(),
                    q.getOptionA(), q.getOptionB(), q.getOptionC(), q.getOptionD(),
                    q.getCorrectOption(), q.getExplanation(),
                    q.getDifficulty(), q.getTopic(), q.getQualityScore(),
                    q.getSelected(), q.getSourceReference(),
                    q.getSourceContext(), q.getPassedValidation(),
                    q.getValidationNotes()
            );
        }
    }

    @Data @AllArgsConstructor @NoArgsConstructor
    public static class KnowledgeSlideResponse {
        private Long id;
        private Long questionId;
        private String title;
        private String content;
        private Boolean sourceGrounded;
        private String sourceReference;
        private Instant createdAt;
        private Instant updatedAt;

        public static KnowledgeSlideResponse from(KnowledgeSlide s) {
            return new KnowledgeSlideResponse(
                    s.getId(), s.getQuestion().getId(),
                    s.getTitle(), s.getContent(),
                    s.getSourceGrounded(), s.getSourceReference(),
                    s.getCreatedAt(), s.getUpdatedAt()
            );
        }
    }
}
