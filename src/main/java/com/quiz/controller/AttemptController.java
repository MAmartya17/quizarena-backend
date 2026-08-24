package com.quiz.controller;

import com.quiz.dto.AttemptResultResponse;
import com.quiz.dto.SubmitAttemptRequest;
import com.quiz.entity.QuizAttempt;
import com.quiz.security.UserPrincipal;
import com.quiz.service.AttemptService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/attempts")
@RequiredArgsConstructor
public class AttemptController {

    private final AttemptService attemptService;

    @PostMapping("/{quizId}")
    public ResponseEntity<AttemptResultResponse> submit(@PathVariable Long quizId,
                                                        @RequestBody SubmitAttemptRequest req,
                                                        @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(attemptService.submit(quizId, req, principal.getUser()));
    }

    @GetMapping("/me")
    public List<Map<String, Object>> myHistory(@AuthenticationPrincipal UserPrincipal principal) {
        return attemptService.userHistory(principal.getId()).stream()
                .map(this::toRow).toList();
    }

    private Map<String, Object> toRow(QuizAttempt a) {
        return Map.of(
                "attemptId", a.getId(),
                "quizId",    a.getQuiz().getId(),
                "quizTitle", a.getQuiz().getTitle(),
                "score",     a.getScore(),
                "maxScore",  a.getMaxScore(),
                "correctCount", a.getCorrectCount(),
                "totalQuestions", a.getTotalQuestions(),
                "attemptedAt", a.getAttemptedAt()
        );
    }
}