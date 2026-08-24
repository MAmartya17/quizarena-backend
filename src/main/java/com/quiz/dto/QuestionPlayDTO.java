package com.quiz.dto;
import com.quiz.entity.Question;
import lombok.AllArgsConstructor; import lombok.Data;

@Data @AllArgsConstructor
public class QuestionPlayDTO {
    private Long id;
    private String text;
    private String optionA;
    private String optionB;
    private String optionC;
    private String optionD;
    private Integer points;

    public static QuestionPlayDTO from(Question q) {
        return new QuestionPlayDTO(q.getId(), q.getText(),
                q.getOptionA(), q.getOptionB(), q.getOptionC(), q.getOptionD(),
                q.getPoints());
    }
}