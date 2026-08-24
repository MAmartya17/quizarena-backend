package com.quiz.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ContestSubmitResponse {
    private int score;
    private int maxScore;
    private int correctCount;
    private int totalQuestions;
    private int percentage;
    private boolean countedOnLeaderboard; // true if ACTIVE, false if practice (ENDED)
    private String contestStatus;         // ACTIVE / ENDED
}