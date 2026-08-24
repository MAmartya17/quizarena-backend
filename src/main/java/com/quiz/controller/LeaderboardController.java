package com.quiz.controller;

import com.quiz.dto.LeaderboardEntryDTO;
import com.quiz.service.LeaderboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/quizzes")
@RequiredArgsConstructor
public class LeaderboardController {

    private final LeaderboardService leaderboardService;

    @GetMapping("/{quizId}/leaderboard")
    public List<LeaderboardEntryDTO> quizLeaderboard(
            @PathVariable Long quizId,
            @RequestParam(defaultValue = "20") int limit) {
        return leaderboardService.quizLeaderboard(quizId, Math.min(limit, 100));
    }
}