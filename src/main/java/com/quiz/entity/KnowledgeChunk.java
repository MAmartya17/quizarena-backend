package com.quiz.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "knowledge_chunks")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class KnowledgeChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_id")
    private KnowledgeSource source;

    /** The chunk text content */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    /** Ordering index within the source document */
    @Column(nullable = false)
    private Integer chunkIndex;

    /** Page number in the original PDF (null if unknown or text paste) */
    private Integer pageNumber;

    /** Detected section heading, if any */
    private String sectionHeading;

    /** Character offset in the full extracted text */
    private Integer startCharOffset;
    private Integer endCharOffset;

    /** Token count for this chunk (approximate) */
    private Integer tokenCount;

    /**
     * Embedding vector stored via pgvector.
     * We store as a String in the format "[0.1,0.2,...]" and use native queries
     * for vector operations to avoid complex JPA/pgvector type mapping.
     */
    @Column(columnDefinition = "TEXT")
    private String embedding;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
