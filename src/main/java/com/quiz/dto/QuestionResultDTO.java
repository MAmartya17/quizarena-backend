package com.quiz.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class QuestionResultDTO {
    private Long questionId;
    private String text;
    private String optionA;
    private String optionB;
    private String optionC;
    private String optionD;
    private String userOption;
    private String correctOption;
    private Boolean isCorrect;
    private Integer points;
    private Integer maxPoints;
    private String explanation;
    private AiDtos.KnowledgeSlideResponse knowledgeSlide;
}
