package com.quiz.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "generation_sessions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class GenerationSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id")
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_id")
    private KnowledgeSource source;

    /** The Quiz created from this session. Null until teacher saves. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quiz_id")
    private Quiz quiz;

    /** Number of questions the teacher requested, e.g. 20 */
    @Column(nullable = false)
    private Integer requestedCount;

    /** EASY, MEDIUM, HARD, MIXED */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String difficulty = "MIXED";

    /** AUTO or MANUAL */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String selectionMode = "AUTO";

    /** Optional comma-separated topic focus list */
    @Column(columnDefinition = "TEXT")
    private String topicFocus;

    /** PENDING, GENERATING, VALIDATING, READY, SAVED, FAILED */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING";

    /** Human-readable progress for the UI, e.g. "Generating questions (12/20)..." */
    private String progressMessage;

    /** How many questions have been generated so far */
    @Builder.Default
    private Integer generatedCount = 0;

    /** How many passed validation */
    @Builder.Default
    private Integer validCount = 0;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<GeneratedQuestion> generatedQuestions = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant completedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
