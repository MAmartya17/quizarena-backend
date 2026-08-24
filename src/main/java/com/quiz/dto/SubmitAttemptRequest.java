package com.quiz.dto;
import lombok.Data;
import java.util.Map;

@Data
public class SubmitAttemptRequest {
    /** Map of questionId -> selected option ("A"|"B"|"C"|"D") */
    private Map<Long, String> answers;
}