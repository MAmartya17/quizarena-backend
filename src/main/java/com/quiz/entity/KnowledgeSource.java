package com.quiz.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "knowledge_sources")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class KnowledgeSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id")
    private User owner;

    /** Original file name, e.g. "Computer Networks.pdf". Null for text paste. */
    private String fileName;

    /** PDF, TEXT, PASTE */
    @Column(nullable = false, length = 20)
    private String sourceType;

    /** Full extracted text content */
    @Column(columnDefinition = "TEXT")
    private String rawText;

    private Long fileSizeBytes;

    /** Total pages for PDFs, null for text */
    private Integer totalPages;

    private Integer totalChunks;

    /** UPLOADING, PROCESSING, READY, FAILED */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "UPLOADING";

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    /** Detected topics/sections as JSON array */
    @Column(columnDefinition = "TEXT")
    private String detectedTopics;

    @OneToMany(mappedBy = "source", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<KnowledgeChunk> chunks = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
