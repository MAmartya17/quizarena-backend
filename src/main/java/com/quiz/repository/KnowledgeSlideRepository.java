package com.quiz.repository;

import com.quiz.entity.KnowledgeSlide;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KnowledgeSlideRepository extends JpaRepository<KnowledgeSlide, Long> {
    Optional<KnowledgeSlide> findByQuestionId(Long questionId);
    List<KnowledgeSlide> findByQuestionQuizId(Long quizId);
    boolean existsByQuestionId(Long questionId);
}
