package com.quiz.repository;

import com.quiz.entity.GenerationSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GenerationSessionRepository extends JpaRepository<GenerationSession, Long> {
    List<GenerationSession> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);
    List<GenerationSession> findByQuizId(Long quizId);
}
