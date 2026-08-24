package com.quiz.repository;

import com.quiz.entity.Contest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ContestRepository extends JpaRepository<Contest, Long> {

    Optional<Contest> findByCode(String code);

    List<Contest> findByHostIdOrderByCreatedAtDesc(Long hostId);

    // ==== NEW: IDs of quizzes that currently have an unfinished contest (endAt in the future) ====
    @Query("SELECT c.quiz.id FROM Contest c WHERE c.endAt > :now")
    List<Long> findQuizIdsWithUnfinishedContests(Instant now);

    // ==== NEW: is THIS quiz locked by an unfinished contest right now? ====
    @Query("""
        SELECT COUNT(c) > 0 FROM Contest c
        WHERE c.quiz.id = :quizId AND c.endAt > :now
    """)
    boolean isQuizLockedByContest(Long quizId, Instant now);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("delete from Contest c where c.quiz.id = :quizId")
    void deleteByQuizId(Long quizId);
}