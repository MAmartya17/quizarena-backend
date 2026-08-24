package com.quiz.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.Instant;

@Data
public class ContestRequest {
    @NotNull  private Long quizId;
    @NotBlank private String title;
    @NotNull  private Instant startAt;
    @NotNull  private Instant endAt;
}