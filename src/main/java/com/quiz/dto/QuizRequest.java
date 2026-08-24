package com.quiz.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.List;

@Data
public class QuizRequest {
    @NotBlank private String title;
    private String description;
    private String category;
    private Integer durationMinutes;
    @Valid private List<QuestionRequest> questions;
}