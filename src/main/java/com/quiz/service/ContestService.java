package com.quiz.service;

import com.quiz.dto.ContestSubmitResponse;
import com.quiz.entity.*;
import com.quiz.exception.ResourceNotFoundException;
import com.quiz.repository.ContestRepository;
import com.quiz.repository.QuizAttemptRepository;
import com.quiz.repository.QuizRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ContestService {

    private final ContestRepository contestRepository;
    private final QuizRepository quizRepository;
    private final QuizAttemptRepository attemptRepository;

    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no 0/O/1/I
    private final SecureRandom random = new SecureRandom();

    @Transactional
    public Contest createContest(Long quizId, String title, Instant startAt, Instant endAt, User host) {
        if (endAt.isBefore(startAt) || endAt.equals(startAt)) {
            throw new IllegalArgumentException("End time must be after start time");
        }
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new ResourceNotFoundException("Quiz not found"));
        if (quiz.getQuestions() == null || quiz.getQuestions().isEmpty()) {
            throw new IllegalStateException("Cannot create a contest for a quiz with no questions");
        }
        Contest contest = Contest.builder()
                .quiz(quiz).host(host).title(title)
                .startAt(startAt).endAt(endAt)
                .code(generateUniqueCode())
                .build();
        return contestRepository.save(contest);
    }

    public Contest getByCode(String code) {
        return contestRepository.findByCode(code.toUpperCase())
                .orElseThrow(() -> new ResourceNotFoundException("Contest not found: " + code));
    }

    public List<Contest> listByHost(Long hostId) {
        return contestRepository.findByHostIdOrderByCreatedAtDesc(hostId);
    }

    /** Returns the quiz so the controller can build a play view (no answers leaked). */
    public Quiz playableQuiz(String code) {
        Contest contest = getByCode(code);
        if (contest.getStatus() == ContestStatus.SCHEDULED) {
            throw new IllegalStateException("This contest has not started yet");
        }
        return contest.getQuiz();
    }

    @Transactional
    public ContestSubmitResponse submit(String code, Map<Long, String> answers, User user) {
        Contest contest = getByCode(code);
        ContestStatus status = contest.getStatus();

        if (status == ContestStatus.SCHEDULED) {
            throw new IllegalStateException("This contest has not started yet");
        }

        Quiz quiz = contest.getQuiz();
        int score = 0, maxScore = 0, correct = 0;
        for (Question q : quiz.getQuestions()) {
            maxScore += q.getPoints();
            String picked = answers == null ? null : answers.get(q.getId());
            if (picked != null && picked.equalsIgnoreCase(q.getCorrectOption())) {
                score += q.getPoints();
                correct++;
            }
        }

        boolean counted = (status == ContestStatus.ACTIVE);
        // Only ACTIVE submissions are persisted to the contest leaderboard.
        // ENDED submissions are scored for self-assessment (practice) but NOT saved.
        if (counted) {
            QuizAttempt attempt = QuizAttempt.builder()
                    .user(user).quiz(quiz).contest(contest)
                    .score(score).maxScore(maxScore)
                    .correctCount(correct)
                    .totalQuestions(quiz.getQuestions().size())
                    .build();
            attemptRepository.save(attempt);
        }

        int pct = maxScore > 0 ? (int) Math.round(score * 100.0 / maxScore) : 0;
        return new ContestSubmitResponse(score, maxScore, correct,
                quiz.getQuestions().size(), pct, counted, status.name());
    }

    private String generateUniqueCode() {
        String code;
        do {
            StringBuilder sb = new StringBuilder(6);
            for (int i = 0; i < 6; i++) {
                sb.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
            }
            code = sb.toString();
        } while (contestRepository.findByCode(code).isPresent());
        return code;
    }
}