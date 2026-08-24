package com.quiz.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "quizzes")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Quiz {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private Integer durationMinutes;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creator_id")
    private User creator;

    @OneToMany(mappedBy = "quiz", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    @Builder.Default
    private List<Question> questions = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    // NEW: Rating aggregates - nullable to avoid issues with existing rows
    private Double avgRating;

    private Integer ratingCount;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        if (avgRating == null) avgRating = 0.0;
        if (ratingCount == null) ratingCount = 0;
    }
}