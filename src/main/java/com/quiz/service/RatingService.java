package com.quiz.service;

import com.quiz.entity.Quiz;
import com.quiz.entity.QuizRating;
import com.quiz.entity.User;
import com.quiz.exception.ResourceNotFoundException;
import com.quiz.repository.QuizAttemptRepository;
import com.quiz.repository.QuizRatingRepository;
import com.quiz.repository.QuizRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RatingService {

    private final QuizRepository quizRepository;
    private final QuizRatingRepository ratingRepository;
    private final QuizAttemptRepository attemptRepository;

    @Transactional
    public Quiz rateQuiz(Long quizId, int stars, User user) {
        if (stars < 1 || stars > 5) {
            throw new IllegalArgumentException("Stars must be between 1 and 5");
        }

        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new ResourceNotFoundException("Quiz not found"));

        boolean attempted = !attemptRepository.findUserAttemptsForQuiz(user.getId(), quizId).isEmpty();
        if (!attempted) {
            throw new AccessDeniedException("You must attempt the quiz before rating it");
        }

        QuizRating rating = ratingRepository.findByUserIdAndQuizId(user.getId(), quizId)
                .orElseGet(() -> QuizRating.builder().user(user).quiz(quiz).build());

        rating.setStars(stars);
        ratingRepository.save(rating);

        Double avg = ratingRepository.averageForQuiz(quizId);
        long count = ratingRepository.countByQuizId(quizId);

        quiz.setAvgRating(avg == null ? 0.0 : Math.round(avg * 10.0) / 10.0);
        quiz.setRatingCount((int) count);
        quizRepository.save(quiz);

        return quiz;
    }
}