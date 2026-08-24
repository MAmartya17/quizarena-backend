package com.quiz.dto;

import lombok.Data;
import java.util.Map;

@Data
public class ContestSubmitRequest {
    // questionId -> picked option ("A"/"B"/"C"/"D")
    private Map<Long, String> answers;
}