package com.quiz.repository;

import com.quiz.entity.QuizRating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface QuizRatingRepository extends JpaRepository<QuizRating, Long> {
    Optional<QuizRating> findByUserIdAndQuizId(Long userId, Long quizId);
    long countByQuizId(Long quizId);

    @Query("select avg(r.stars) from QuizRating r where r.quiz.id = :quizId")
    Double averageForQuiz(Long quizId);
}