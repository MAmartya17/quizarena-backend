package com.quiz.controller;

import com.quiz.dto.*;
import com.quiz.entity.Contest;
import com.quiz.entity.Quiz;
import com.quiz.security.UserPrincipal;
import com.quiz.service.ContestService;
import com.quiz.service.LeaderboardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/contests")
@RequiredArgsConstructor
public class ContestController {

    private final ContestService contestService;
    private final LeaderboardService leaderboardService;

    @PostMapping
    public ResponseEntity<ContestResponse> create(@Valid @RequestBody ContestRequest req,
                                                  @AuthenticationPrincipal UserPrincipal principal) {
        Contest c = contestService.createContest(
                req.getQuizId(), req.getTitle(), req.getStartAt(), req.getEndAt(),
                principal.getUser());
        return ResponseEntity.ok(ContestResponse.from(c));
    }

    @GetMapping("/mine")
    public List<ContestResponse> myContests(@AuthenticationPrincipal UserPrincipal principal) {
        return contestService.listByHost(principal.getUser().getId())
                .stream().map(ContestResponse::from).toList();
    }

    @GetMapping("/{code}")
    public ContestResponse getByCode(@PathVariable String code) {
        return ContestResponse.from(contestService.getByCode(code));
    }

    /** Play view — returns questions WITHOUT correct answers (anti-cheat). */
    @GetMapping("/{code}/play")
    public ResponseEntity<?> play(@PathVariable String code) {
        Quiz q = contestService.playableQuiz(code);
        List<QuestionPlayDTO> qs = q.getQuestions().stream().map(QuestionPlayDTO::from).toList();
        return ResponseEntity.ok(Map.of(
                "quizTitle", q.getTitle(),
                "durationMinutes", q.getDurationMinutes(),
                "questions", qs
        ));
    }

    @PostMapping("/{code}/submit")
    public ResponseEntity<ContestSubmitResponse> submit(@PathVariable String code,
                                                        @RequestBody ContestSubmitRequest req,
                                                        @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(
                contestService.submit(code, req.getAnswers(), principal.getUser()));
    }

    @GetMapping("/{code}/leaderboard")
    public List<LeaderboardEntryDTO> leaderboard(@PathVariable String code,
                                                 @RequestParam(defaultValue = "20") int limit) {
        Contest c = contestService.getByCode(code);
        return leaderboardService.contestLeaderboard(c.getId(), Math.min(limit, 100));
    }
}