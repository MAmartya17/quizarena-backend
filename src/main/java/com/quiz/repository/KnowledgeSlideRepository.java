package com.quiz.repository;

import com.quiz.entity.KnowledgeSlide;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface KnowledgeSlideRepository extends JpaRepository<KnowledgeSlide, Long> {
    Optional<KnowledgeSlide> findByQuestionId(Long questionId);
    List<KnowledgeSlide> findByQuestionQuizId(Long quizId);
    boolean existsByQuestionId(Long questionId);

    @Modifying
    @Transactional
    @Query("delete from KnowledgeSlide s where s.question.quiz.id = :quizId")
    void deleteByQuestionQuizId(Long quizId);
}
