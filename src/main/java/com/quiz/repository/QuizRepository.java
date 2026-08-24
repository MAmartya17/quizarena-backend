package com.quiz.repository;

import com.quiz.entity.Quiz;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuizRepository extends JpaRepository<Quiz, Long> {
    List<Quiz> findByCreatorId(Long creatorId);
    List<Quiz> findByCategoryIgnoreCase(String category);
}