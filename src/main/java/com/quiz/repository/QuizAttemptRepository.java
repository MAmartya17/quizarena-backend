package com.quiz.repository;

import com.quiz.dto.LeaderboardEntryDTO;
import com.quiz.entity.QuizAttempt;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {
    List<QuizAttempt> findByUserIdOrderByAttemptedAtDesc(Long userId);

    @Query("select a from QuizAttempt a where a.user.id = :userId and a.quiz.id = :quizId order by a.score desc")
    List<QuizAttempt> findUserAttemptsForQuiz(Long userId, Long quizId);

    @Query("select a from QuizAttempt a where a.user.id = :userId and a.quiz.id = :quizId order by a.score desc limit 1")
    Optional<QuizAttempt> findBestAttempt(Long userId, Long quizId);

    // ==== NEW: quiz leaderboard — best score per user, excludes contest attempts ====
    @Query("""
        SELECT new com.quiz.dto.LeaderboardEntryDTO(
            u.id, u.name, u.pictureUrl, MAX(a.score), MAX(a.maxScore))
        FROM QuizAttempt a JOIN a.user u
        WHERE a.quiz.id = :quizId AND a.contest IS NULL
        GROUP BY u.id, u.name, u.pictureUrl
        ORDER BY MAX(a.score) DESC
    """)
    List<LeaderboardEntryDTO> findQuizLeaderboard(Long quizId, Pageable pageable);

    // ==== NEW: contest leaderboard — best score per user for one contest ====
    @Query("""
        SELECT new com.quiz.dto.LeaderboardEntryDTO(
            u.id, u.name, u.pictureUrl, MAX(a.score), MAX(a.maxScore))
        FROM QuizAttempt a JOIN a.user u
        WHERE a.contest.id = :contestId
        GROUP BY u.id, u.name, u.pictureUrl
        ORDER BY MAX(a.score) DESC
    """)
    List<LeaderboardEntryDTO> findContestLeaderboard(Long contestId, Pageable pageable);
    // ========================================================================
}