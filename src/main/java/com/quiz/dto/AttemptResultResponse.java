package com.quiz.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AttemptResultResponse {
    private Long attemptId;
    private Integer score;
    private Integer maxScore;
    private Integer correctCount;
    private Integer totalQuestions;
    private Integer bestScore;
    private List<QuestionResultDTO> questionResults;
    private String performanceTier;
    private String improvementSummary;
    private List<String> weakAreas;
    private List<String> masteredAreas;
}