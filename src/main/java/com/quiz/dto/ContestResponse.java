package com.quiz.dto;

import com.quiz.entity.Contest;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

@Data
@AllArgsConstructor
public class ContestResponse {
    private Long id;
    private String code;
    private String title;
    private Long quizId;
    private String quizTitle;
    private String hostName;
    private Instant startAt;
    private Instant endAt;
    private String status;          // SCHEDULED / ACTIVE / ENDED
    private Integer questionCount;
    private Integer durationMinutes;

    public static ContestResponse from(Contest c) {
        return new ContestResponse(
                c.getId(), c.getCode(), c.getTitle(),
                c.getQuiz().getId(), c.getQuiz().getTitle(),
                c.getHost().getName(),
                c.getStartAt(), c.getEndAt(),
                c.getStatus().name(),
                c.getQuiz().getQuestions() == null ? 0 : c.getQuiz().getQuestions().size(),
                c.getQuiz().getDurationMinutes()
        );
    }
}