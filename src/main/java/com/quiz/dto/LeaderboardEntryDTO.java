package com.quiz.dto;

import lombok.Data;

@Data
public class LeaderboardEntryDTO {
    private Long userId;
    private String name;
    private String pictureUrl;
    private Integer bestScore;
    private Integer maxScore;
    private Integer rank;        // set in service after query
    private Integer percentage;  // set in service after query

    // This exact 5-arg constructor is what the JPQL "SELECT new ..." calls.
    public LeaderboardEntryDTO(Long userId, String name, String pictureUrl,
                               Integer bestScore, Integer maxScore) {
        this.userId = userId;
        this.name = name;
        this.pictureUrl = pictureUrl;
        this.bestScore = bestScore;
        this.maxScore = maxScore;
    }
}