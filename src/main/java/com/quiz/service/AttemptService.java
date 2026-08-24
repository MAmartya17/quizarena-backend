package com.quiz.service;

import com.quiz.dto.AttemptResultResponse;
import com.quiz.dto.SubmitAttemptRequest;
import com.quiz.entity.Question;
import com.quiz.entity.Quiz;
import com.quiz.entity.QuizAttempt;
import com.quiz.entity.User;
import com.quiz.repository.QuizAttemptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AttemptService {

    private final QuizService quizService;
    private final QuizAttemptRepository attemptRepository;

    @Transactional
    public AttemptResultResponse submit(Long quizId, SubmitAttemptRequest req, User user) {
        Quiz quiz = quizService.getById(quizId);
        Map<Long, String> answers = req.getAnswers() == null ? Map.of() : req.getAnswers();

        int score = 0, max = 0, correct = 0;
        for (Question q : quiz.getQuestions()) {
            max += q.getPoints();
            String chosen = answers.get(q.getId());
            if (chosen != null && chosen.equalsIgnoreCase(q.getCorrectOption())) {
                score   += q.getPoints();
                correct += 1;
            }
        }

        QuizAttempt attempt = QuizAttempt.builder()
                .user(user).quiz(quiz)
                .score(score).maxScore(max)
                .correctCount(correct).totalQuestions(quiz.getQuestions().size())
                .build();
        attempt = attemptRepository.save(attempt);

        int best = attemptRepository.findBestAttempt(user.getId(), quizId)
                .map(QuizAttempt::getScore).orElse(score);

        return new AttemptResultResponse(attempt.getId(), score, max, correct,
                quiz.getQuestions().size(), best);
    }

    @Transactional(readOnly = true)
    public List<QuizAttempt> userHistory(Long userId) {
        return attemptRepository.findByUserIdOrderByAttemptedAtDesc(userId);
    }
}