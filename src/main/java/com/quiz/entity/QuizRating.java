package com.quiz.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "quiz_ratings",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "quiz_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class QuizRating {

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
    private Integer stars;

    @Column(nullable = false, updatable = false)
    private Instant ratedAt;

    @PrePersist
    void onCreate() { ratedAt = Instant.now(); }
}