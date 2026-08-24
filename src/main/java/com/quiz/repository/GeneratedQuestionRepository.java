package com.quiz.repository;

import com.quiz.entity.GeneratedQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GeneratedQuestionRepository extends JpaRepository<GeneratedQuestion, Long> {
    List<GeneratedQuestion> findBySessionIdOrderByCreatedAt(Long sessionId);
    List<GeneratedQuestion> findBySessionIdAndSelectedTrue(Long sessionId);
    long countBySessionIdAndPassedValidationTrue(Long sessionId);
}
