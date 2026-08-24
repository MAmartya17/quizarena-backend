package com.quiz.service;

import com.quiz.dto.AiDtos;
import com.quiz.dto.AttemptResultResponse;
import com.quiz.dto.QuestionResultDTO;
import com.quiz.dto.SubmitAttemptRequest;
import com.quiz.entity.KnowledgeSlide;
import com.quiz.entity.Question;
import com.quiz.entity.Quiz;
import com.quiz.entity.QuizAttempt;
import com.quiz.entity.User;
import com.quiz.repository.KnowledgeSlideRepository;
import com.quiz.repository.QuizAttemptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AttemptService {

    private final QuizService quizService;
    private final QuizAttemptRepository attemptRepository;
    private final KnowledgeSlideRepository knowledgeSlideRepository;

    @Transactional
    public AttemptResultResponse submit(Long quizId, SubmitAttemptRequest req, User user) {
        Quiz quiz = quizService.getById(quizId);
        Map<Long, String> answers = req.getAnswers() == null ? Map.of() : req.getAnswers();

        int score = 0, max = 0, correct = 0;
        List<QuestionResultDTO> questionResults = new ArrayList<>();
        List<String> weakAreas = new ArrayList<>();
        List<String> masteredAreas = new ArrayList<>();

        for (Question q : quiz.getQuestions()) {
            int qPoints = q.getPoints() != null ? q.getPoints() : 1;
            max += qPoints;
            String chosen = answers.get(q.getId());
            boolean isCorrect = chosen != null && chosen.trim().equalsIgnoreCase(q.getCorrectOption().trim());

            if (isCorrect) {
                score += qPoints;
                correct += 1;
                masteredAreas.add(q.getText());
            } else {
                weakAreas.add(q.getText());
            }

            // Find associated knowledge slide if exists
            KnowledgeSlide slide = knowledgeSlideRepository.findByQuestionId(q.getId()).orElse(null);
            AiDtos.KnowledgeSlideResponse slideDto = (slide != null)
                    ? AiDtos.KnowledgeSlideResponse.from(slide)
                    : null;

            questionResults.add(QuestionResultDTO.builder()
                    .questionId(q.getId())
                    .text(q.getText())
                    .optionA(q.getOptionA())
                    .optionB(q.getOptionB())
                    .optionC(q.getOptionC())
                    .optionD(q.getOptionD())
                    .userOption(chosen)
                    .correctOption(q.getCorrectOption())
                    .isCorrect(isCorrect)
                    .points(isCorrect ? qPoints : 0)
                    .maxPoints(qPoints)
                    .explanation(q.getExplanation())
                    .knowledgeSlide(slideDto)
                    .build());
        }

        QuizAttempt attempt = QuizAttempt.builder()
                .user(user).quiz(quiz)
                .score(score).maxScore(max)
                .correctCount(correct).totalQuestions(quiz.getQuestions().size())
                .build();
        attempt = attemptRepository.save(attempt);

        int best = attemptRepository.findBestAttempt(user.getId(), quizId)
                .map(QuizAttempt::getScore).orElse(score);

        int totalQ = quiz.getQuestions().size();
        int wrongCount = totalQ - correct;
        int percentage = max > 0 ? (int) Math.round(((double) score / max) * 100) : 0;

        String tier;
        String summary;
        if (percentage >= 95) {
            tier = "Mastery Level";
            summary = "🌟 Outstanding performance! You demonstrated comprehensive mastery across all topics in this quiz. Check the knowledge slides below for key revision takeaways.";
        } else if (percentage >= 75) {
            tier = "Proficient";
            summary = "🎉 Great job! You scored " + percentage + "%. You have a strong grasp of the material. Review the " + wrongCount + " knowledge slide(s) below for the questions you missed to achieve 100% mastery.";
        } else if (percentage >= 50) {
            tier = "Developing Understanding";
            summary = "📈 Good effort! You scored " + percentage + "%. We identified " + wrongCount + " key concept area(s) where you can improve. Read through the targeted knowledge slides below before your next attempt.";
        } else {
            tier = "Needs Core Review";
            summary = "💡 Room for improvement! You scored " + percentage + "%. To level up, study each of the " + wrongCount + " knowledge slides and detailed explanations below to master the core concepts.";
        }

        return AttemptResultResponse.builder()
                .attemptId(attempt.getId())
                .score(score)
                .maxScore(max)
                .correctCount(correct)
                .totalQuestions(totalQ)
                .bestScore(best)
                .questionResults(questionResults)
                .performanceTier(tier)
                .improvementSummary(summary)
                .weakAreas(weakAreas)
                .masteredAreas(masteredAreas)
                .build();
    }

    @Transactional(readOnly = true)
    public List<QuizAttempt> userHistory(Long userId) {
        return attemptRepository.findByUserIdOrderByAttemptedAtDesc(userId);
    }
}