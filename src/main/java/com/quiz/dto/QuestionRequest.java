package com.quiz.dto;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class QuestionRequest {
    @NotBlank private String text;
    @NotBlank private String optionA;
    @NotBlank private String optionB;
    @NotBlank private String optionC;
    @NotBlank private String optionD;
    @NotBlank @Pattern(regexp = "[ABCD]") private String correctOption;
    @Min(1) private Integer points = 1;
}