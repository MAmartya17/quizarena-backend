package com.quiz.service;

import com.quiz.dto.QuestionRequest;
import com.quiz.dto.QuizRequest;
import com.quiz.entity.Question;
import com.quiz.entity.Quiz;
import com.quiz.entity.User;
import com.quiz.exception.ResourceNotFoundException;
import com.quiz.repository.ContestRepository;
import com.quiz.repository.QuestionRepository;
import com.quiz.repository.QuizRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

@Service
@RequiredArgsConstructor
public class QuizService {

    private final QuizRepository quizRepository;
    private final QuestionRepository questionRepository;
    private final ContestRepository contestRepository;   // NEW

    /** IDs of quizzes locked by a live/upcoming contest. Used to flag (not hide) them. */
    @Transactional(readOnly = true)
    public Set<Long> lockedQuizIds() {
        return new HashSet<>(
                contestRepository.findQuizIdsWithUnfinishedContests(Instant.now()));
    }

    @Transactional(readOnly = true)
    public List<Quiz> listAll() { return quizRepository.findAll(); }

    @Transactional(readOnly = true)
    public List<Quiz> listByCategory(String cat) { return quizRepository.findByCategoryIgnoreCase(cat); }

    @Transactional(readOnly = true)
    public List<Quiz> listByCreator(Long creatorId) { return quizRepository.findByCreatorId(creatorId); }

    @Transactional(readOnly = true)
    public Quiz getById(Long id) {
        return quizRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Quiz not found: " + id));
    }

    /** Used by the play endpoint: blocks direct play while a contest is unfinished. */
    @Transactional(readOnly = true)
    public Quiz getPlayableById(Long id) {
        Quiz quiz = getById(id);
        if (contestRepository.isQuizLockedByContest(id, Instant.now())) {
            throw new IllegalStateException("This quiz is locked — it's part of a live contest");
        }
        return quiz;
    }

    @Transactional
    public Quiz create(QuizRequest req, User creator) {
        Quiz quiz = Quiz.builder()
                .title(req.getTitle())
                .description(req.getDescription())
                .category(req.getCategory())
                .durationMinutes(req.getDurationMinutes())
                .creator(creator)
                .build();
        if (req.getQuestions() != null) {
            for (QuestionRequest qr : req.getQuestions()) {
                quiz.getQuestions().add(toEntity(qr, quiz));
            }
        }
        return quizRepository.save(quiz);
    }

    @Transactional
    public Question addQuestion(Long quizId, QuestionRequest qr, User actor) {
        Quiz quiz = getById(quizId);
        ensureOwner(quiz, actor);
        Question q = toEntity(qr, quiz);
        return questionRepository.save(q);
    }

    @Transactional
    public void deleteQuiz(Long quizId, User actor) {
        Quiz quiz = getById(quizId);
        ensureOwner(quiz, actor);
        quizRepository.delete(quiz);
    }

    private void ensureOwner(Quiz quiz, User actor) {
        if (!quiz.getCreator().getId().equals(actor.getId())
                && actor.getRole() != User.Role.ADMIN) {
            throw new AccessDeniedException("Only the creator can modify this quiz");
        }
    }

    private Question toEntity(QuestionRequest qr, Quiz quiz) {
        return Question.builder()
                .text(qr.getText())
                .optionA(qr.getOptionA()).optionB(qr.getOptionB())
                .optionC(qr.getOptionC()).optionD(qr.getOptionD())
                .correctOption(qr.getCorrectOption().toUpperCase())
                .points(qr.getPoints() == null ? 1 : qr.getPoints())
                .quiz(quiz)
                .build();
    }
}