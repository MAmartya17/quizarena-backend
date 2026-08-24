package com.quiz.repository;

import com.quiz.entity.KnowledgeSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KnowledgeSourceRepository extends JpaRepository<KnowledgeSource, Long> {
    List<KnowledgeSource> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);
}
