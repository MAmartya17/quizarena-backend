package com.quiz.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "contests")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Contest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quiz_id")
    private Quiz quiz;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "host_id")
    private User host;

    @Column(nullable = false, unique = true, length = 8)
    private String code;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private Instant startAt;

    @Column(nullable = false)
    private Instant endAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() { createdAt = Instant.now(); }

    /** Status computed dynamically from the current time — no scheduled job needed. */
    @Transient
    public ContestStatus getStatus() {
        Instant now = Instant.now();
        if (now.isBefore(startAt)) return ContestStatus.SCHEDULED;
        if (now.isAfter(endAt))    return ContestStatus.ENDED;
        return ContestStatus.ACTIVE;
    }
}