package com.quiz.controller;

import com.quiz.dto.*;
import com.quiz.entity.Question;
import com.quiz.entity.Quiz;
import com.quiz.security.UserPrincipal;
import com.quiz.service.QuizService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/quizzes")
@RequiredArgsConstructor
public class QuizController {

    private final QuizService quizService;

    /** Public list — no auth required (used for browsing). Contest-locked quizzes
     *  are still shown, but flagged with locked=true so the UI can grey them out. */
    @GetMapping("/public")
    public List<QuizSummaryDTO> listPublic(@RequestParam(required = false) String category,
                                           @RequestParam(required = false, defaultValue = "recent") String sort) {
        List<Quiz> quizzes = (category == null || category.isBlank())
                ? quizService.listAll() : quizService.listByCategory(category);

        if ("rating".equalsIgnoreCase(sort)) {
            quizzes = quizzes.stream()
                    .sorted(Comparator.comparingDouble((Quiz q) ->
                            q.getAvgRating() == null ? 0.0 : q.getAvgRating()).reversed())
                    .toList();
        }

        Set<Long> locked = quizService.lockedQuizIds();   // NEW
        return quizzes.stream().map(q -> {
            QuizSummaryDTO dto = QuizSummaryDTO.from(q);
            dto.setLocked(locked.contains(q.getId()));     // NEW
            return dto;
        }).toList();
    }

    /** Quiz detail with play-mode questions (no correct answers leaked).
     *  Blocks direct play while the quiz is locked by a live contest. */
    @GetMapping("/{id}/play")
    public ResponseEntity<?> playView(@PathVariable Long id) {
        Quiz q = quizService.getPlayableById(id);          // CHANGED: was getById(id)
        List<QuestionPlayDTO> qs = q.getQuestions().stream().map(QuestionPlayDTO::from).toList();
        return ResponseEntity.ok(java.util.Map.of(
                "id", q.getId(), "title", q.getTitle(),
                "description", q.getDescription(), "category", q.getCategory(),
                "durationMinutes", q.getDurationMinutes(), "questions", qs
        ));
    }

    @PostMapping
    public ResponseEntity<QuizSummaryDTO> create(@Valid @RequestBody QuizRequest req,
                                                 @AuthenticationPrincipal UserPrincipal principal) {
        Quiz created = quizService.create(req, principal.getUser());
        return ResponseEntity.ok(QuizSummaryDTO.from(created));
    }

    @PostMapping("/{id}/questions")
    public ResponseEntity<Question> addQuestion(@PathVariable Long id,
                                                @Valid @RequestBody QuestionRequest req,
                                                @AuthenticationPrincipal UserPrincipal principal) {
        Question saved = quizService.addQuestion(id, req, principal.getUser());
        return ResponseEntity.ok(saved);
    }

    /** "My Quizzes" — owner sees ALL their quizzes, including contest-locked ones. */
    @GetMapping("/mine")
    public List<QuizSummaryDTO> myQuizzes(@AuthenticationPrincipal UserPrincipal principal) {
        return quizService.listByCreator(principal.getUser().getId())
                .stream().map(QuizSummaryDTO::from).toList();
    }
}