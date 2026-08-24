package com.quiz.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "generated_questions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class GeneratedQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id")
    private GenerationSession session;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String questionText;

    @Column(nullable = false)
    private String optionA;

    @Column(nullable = false)
    private String optionB;

    @Column(nullable = false)
    private String optionC;

    @Column(nullable = false)
    private String optionD;

    /** A, B, C, or D */
    @Column(nullable = false, length = 1)
    private String correctOption;

    @Column(columnDefinition = "TEXT")
    private String explanation;

    /** EASY, MEDIUM, HARD */
    @Column(length = 20)
    private String difficulty;

    /** Detected topic/section this question relates to */
    private String topic;

    /** Quality score from 0.0 to 1.0 */
    @Builder.Default
    private Double qualityScore = 0.0;

    /** Whether the teacher selected this question for the final quiz */
    @Builder.Default
    private Boolean selected = false;

    /** Comma-separated chunk IDs used to generate this question */
    private String sourceChunkIds;

    /** Verbatim source text that was used as context */
    @Column(columnDefinition = "TEXT")
    private String sourceContext;

    /** Human-readable source reference, e.g. "Page 12, Section 3.2" */
    private String sourceReference;

    /** Whether this question passed validation checks */
    @Builder.Default
    private Boolean passedValidation = true;

    /** Notes from validation (issues found, warnings) */
    @Column(columnDefinition = "TEXT")
    private String validationNotes;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
