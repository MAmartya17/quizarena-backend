package com.quiz.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "questions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 2000)
    private String text;

    @Column(nullable = false) private String optionA;
    @Column(nullable = false) private String optionB;
    @Column(nullable = false) private String optionC;
    @Column(nullable = false) private String optionD;

    /** 'A' | 'B' | 'C' | 'D' — never sent to client when taking quiz */
    @Column(nullable = false, length = 1)
    @JsonIgnore
    private String correctOption;

    @Builder.Default
    private Integer points = 1;

    /** Optional explanation for the correct answer — used by AI generation and knowledge slides */
    @Column(columnDefinition = "TEXT")
    private String explanation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quiz_id")
    @JsonIgnore
    private Quiz quiz;
}