package com.quiz.service;

import com.quiz.dto.LeaderboardEntryDTO;
import com.quiz.exception.ResourceNotFoundException;
import com.quiz.repository.QuizAttemptRepository;
import com.quiz.repository.QuizRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LeaderboardService {

    private final QuizAttemptRepository attemptRepository;
    private final QuizRepository quizRepository;

    public List<LeaderboardEntryDTO> quizLeaderboard(Long quizId, int limit) {
        quizRepository.findById(quizId)
                .orElseThrow(() -> new ResourceNotFoundException("Quiz not found"));
        List<LeaderboardEntryDTO> entries =
                attemptRepository.findQuizLeaderboard(quizId, PageRequest.of(0, limit));
        return assignRanks(entries);
    }

    public List<LeaderboardEntryDTO> contestLeaderboard(Long contestId, int limit) {
        List<LeaderboardEntryDTO> entries =
                attemptRepository.findContestLeaderboard(contestId, PageRequest.of(0, limit));
        return assignRanks(entries);
    }

    private List<LeaderboardEntryDTO> assignRanks(List<LeaderboardEntryDTO> entries) {
        int rank = 1;
        for (LeaderboardEntryDTO e : entries) {
            e.setRank(rank++);
            if (e.getMaxScore() != null && e.getMaxScore() > 0) {
                e.setPercentage((int) Math.round(e.getBestScore() * 100.0 / e.getMaxScore()));
            } else {
                e.setPercentage(0);
            }
        }
        return entries;
    }
}