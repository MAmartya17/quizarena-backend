package com.quiz.dto;
import lombok.AllArgsConstructor; import lombok.Data;

@Data @AllArgsConstructor
public class AttemptResultResponse {
    private Long attemptId;
    private Integer score;
    private Integer maxScore;
    private Integer correctCount;
    private Integer totalQuestions;
    private Integer bestScore;
}