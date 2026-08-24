package com.quiz.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "knowledge_slides")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class KnowledgeSlide {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Link to the existing Question entity */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", unique = true)
    private Question question;

    /** Slide title */
    @Column(nullable = false)
    private String title;

    /** Markdown content of the knowledge slide */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    /** true = content is grounded in an uploaded source document,
     *  false = AI-generated explanatory content (no source doc) */
    @Builder.Default
    private Boolean sourceGrounded = false;

    /** Source reference, e.g. "Computer Networks.pdf — Page 18". Null if not source-grounded. */
    private String sourceReference;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
