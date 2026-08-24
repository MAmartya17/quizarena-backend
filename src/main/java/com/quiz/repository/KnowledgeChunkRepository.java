package com.quiz.repository;

import com.quiz.entity.KnowledgeChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface KnowledgeChunkRepository extends JpaRepository<KnowledgeChunk, Long> {

    List<KnowledgeChunk> findBySourceIdOrderByChunkIndex(Long sourceId);

    @Query("SELECT c FROM KnowledgeChunk c WHERE c.source.id = :sourceId ORDER BY c.chunkIndex")
    List<KnowledgeChunk> findAllBySourceId(Long sourceId);

    long countBySourceId(Long sourceId);
}
