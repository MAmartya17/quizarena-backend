package com.quiz.controller;

import com.quiz.entity.Quiz;
import com.quiz.security.UserPrincipal;
import com.quiz.service.RatingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ratings")
@RequiredArgsConstructor
public class RatingController {

    private final RatingService ratingService;

    @PostMapping("/{quizId}")
    public ResponseEntity<?> rate(@PathVariable Long quizId,
                                  @RequestBody Map<String, Integer> body,
                                  @AuthenticationPrincipal UserPrincipal principal) {
        int stars = body.getOrDefault("stars", 0);
        Quiz updated = ratingService.rateQuiz(quizId, stars, principal.getUser());
        return ResponseEntity.ok(Map.of(
                "avgRating", updated.getAvgRating(),
                "ratingCount", updated.getRatingCount()
        ));
    }
}