package com.quiz.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "quiz_attempts")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class QuizAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quiz_id")
    private Quiz quiz;

    @Column(nullable = false)
    private Integer score;

    @Column(nullable = false)
    private Integer maxScore;

    @Column(nullable = false)
    private Integer correctCount;

    @Column(nullable = false)
    private Integer totalQuestions;

    @Column(nullable = false, updatable = false)
    private Instant attemptedAt;

    // ==== NEW: link to a contest. NULL for regular attempts. ====
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contest_id")
    private Contest contest;
    // ============================================================

    @PrePersist
    void onCreate() { attemptedAt = Instant.now(); }
}