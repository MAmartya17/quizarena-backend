package com.quiz.entity;

public enum ContestStatus {
    SCHEDULED,  // before startAt
    ACTIVE,     // between startAt and endAt — submissions count
    ENDED       // after endAt — viewable & practiceable, but not scored on leaderboard
}