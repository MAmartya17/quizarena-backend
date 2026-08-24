package com.quiz.dto;

import com.quiz.entity.Quiz;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class QuizSummaryDTO {
    private Long id;
    private String title;
    private String description;
    private String category;
    private Integer durationMinutes;
    private Integer questionCount;
    private String creatorName;
    private Long creatorId;
    private Double avgRating;
    private Integer ratingCount;
    private boolean locked;   // NEW: true if a live/upcoming contest is using this quiz

    public static QuizSummaryDTO from(Quiz q) {
        return new QuizSummaryDTO(
                q.getId(),
                q.getTitle(),
                q.getDescription(),
                q.getCategory(),
                q.getDurationMinutes(),
                q.getQuestions() == null ? 0 : q.getQuestions().size(),
                q.getCreator().getName(),
                q.getCreator().getId(),
                q.getAvgRating() == null ? 0.0 : q.getAvgRating(),
                q.getRatingCount() == null ? 0 : q.getRatingCount(),
                false   // default unlocked; controller sets the real value
        );
    }
}