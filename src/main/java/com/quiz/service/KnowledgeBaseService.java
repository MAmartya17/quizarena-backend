package com.quiz.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quiz.ai.EmbeddingProvider;
import com.quiz.ai.LlmProvider;
import com.quiz.ai.PromptTemplates;
import com.quiz.entity.KnowledgeChunk;
import com.quiz.entity.KnowledgeSource;
import com.quiz.entity.User;
import com.quiz.repository.KnowledgeChunkRepository;
import com.quiz.repository.KnowledgeSourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.*;

/**
 * Orchestrates: parse → clean → chunk → embed → store.
 * Handles both PDF uploads and text pastes.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KnowledgeBaseService {

    private final DocumentProcessingService docService;
    private final KnowledgeSourceRepository sourceRepository;
    private final KnowledgeChunkRepository chunkRepository;
    private final EmbeddingProvider embeddingProvider;
    private final LlmProvider llmProvider;
    private final ObjectMapper objectMapper;

    private static final long MAX_FILE_SIZE = 20 * 1024 * 1024; // 20 MB
    private static final int MAX_TEXT_LENGTH = 50_000;

    /**
     * Create a knowledge source from a PDF upload.
     * Returns the source immediately; processing happens async.
     */
    @Transactional
    public KnowledgeSource createFromPdf(MultipartFile file, User owner) {
        // Validate
        if (file.isEmpty()) throw new IllegalArgumentException("File is empty");
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File too large. Maximum size is 20 MB.");
        }
        String fileName = file.getOriginalFilename();
        if (fileName == null || !fileName.toLowerCase().endsWith(".pdf")) {
            throw new IllegalArgumentException("Only PDF files are supported.");
        }

        KnowledgeSource source = KnowledgeSource.builder()
                .owner(owner)
                .fileName(fileName)
                .sourceType("PDF")
                .fileSizeBytes(file.getSize())
                .status("PROCESSING")
                .build();
        source = sourceRepository.save(source);

        return source;
    }

    /**
     * Create a knowledge source from pasted text.
     */
    @Transactional
    public KnowledgeSource createFromText(String text, String title, User owner) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Text content is required.");
        }
        if (text.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("Text too long. Maximum length is " + MAX_TEXT_LENGTH + " characters.");
        }

        KnowledgeSource source = KnowledgeSource.builder()
                .owner(owner)
                .fileName(title != null ? title : "Pasted Text")
                .sourceType("TEXT")
                .rawText(text)
                .fileSizeBytes((long) text.length())
                .status("PROCESSING")
                .build();
        source = sourceRepository.save(source);

        return source;
    }

    /**
     * Process a knowledge source asynchronously.
     * Extracts text, chunks, embeds, and detects topics.
     */
    @Async("aiTaskExecutor")
    public void processSourceAsync(Long sourceId, byte[] fileBytes) {
        KnowledgeSource source = sourceRepository.findById(sourceId).orElse(null);
        if (source == null) return;

        try {
            log.info("Processing knowledge source {}: {}", sourceId, source.getFileName());
            String fullText;
            Map<Integer, String> pageTexts = null;

            if ("PDF".equals(source.getSourceType())) {
                // Extract text from PDF
                InputStream pdfStream = new ByteArrayInputStream(fileBytes);
                pageTexts = docService.extractTextFromPdf(pdfStream);

                if (pageTexts.isEmpty()) {
                    source.setStatus("FAILED");
                    source.setErrorMessage("No text could be extracted from this PDF. It may be a scanned/image-only document.");
                    sourceRepository.save(source);
                    return;
                }

                source.setTotalPages(pageTexts.size());
                fullText = String.join("\n\n", pageTexts.values());
            } else {
                fullText = source.getRawText();
            }

            // Clean text
            fullText = docService.cleanText(fullText);
            source.setRawText(fullText);

            if (fullText.length() < 100) {
                source.setStatus("FAILED");
                source.setErrorMessage("Not enough usable text content found (minimum 100 characters required).");
                sourceRepository.save(source);
                return;
            }

            // Chunk text
            List<DocumentProcessingService.ChunkInfo> chunkInfos = docService.chunkText(fullText, pageTexts);
            if (chunkInfos.isEmpty()) {
                source.setStatus("FAILED");
                source.setErrorMessage("Could not create text chunks from the provided content.");
                sourceRepository.save(source);
                return;
            }

            source.setTotalChunks(chunkInfos.size());

            // Create chunk entities and generate embeddings
            List<KnowledgeChunk> chunks = new ArrayList<>();
            for (DocumentProcessingService.ChunkInfo info : chunkInfos) {
                float[] embedding = embeddingProvider.embed(info.content());
                String embeddingStr = embeddingToString(embedding);

                KnowledgeChunk chunk = KnowledgeChunk.builder()
                        .source(source)
                        .content(info.content())
                        .chunkIndex(info.chunkIndex())
                        .pageNumber(info.pageNumber())
                        .sectionHeading(info.sectionHeading())
                        .startCharOffset(info.startCharOffset())
                        .endCharOffset(info.endCharOffset())
                        .tokenCount(info.tokenCount())
                        .embedding(embeddingStr)
                        .build();
                chunks.add(chunk);
            }
            chunkRepository.saveAll(chunks);

            // Extract topics
            try {
                String topicsJson = llmProvider.generateJson(
                        PromptTemplates.TOPIC_EXTRACTION_SYSTEM,
                        PromptTemplates.topicExtractionPrompt(fullText)
                );
                source.setDetectedTopics(topicsJson);
            } catch (Exception e) {
                log.warn("Topic extraction failed for source {}: {}", sourceId, e.getMessage());
                // Non-critical — continue without topics
            }

            source.setStatus("READY");
            sourceRepository.save(source);
            log.info("Knowledge source {} processed successfully: {} chunks created", sourceId, chunks.size());

        } catch (Exception e) {
            log.error("Failed to process knowledge source {}: {}", sourceId, e.getMessage(), e);
            source.setStatus("FAILED");
            source.setErrorMessage("Processing failed: " + e.getMessage());
            sourceRepository.save(source);
        }
    }

    /**
     * Retrieve the most relevant chunks for a query using keyword matching.
     * (Simple retrieval — pgvector similarity would require native SQL queries)
     */
    public List<KnowledgeChunk> retrieveRelevantChunks(Long sourceId, String query, int topK) {
        List<KnowledgeChunk> allChunks = chunkRepository.findBySourceIdOrderByChunkIndex(sourceId);

        if (query == null || query.isBlank()) {
            // Return evenly distributed chunks
            return distributeChunks(allChunks, topK);
        }

        // Score chunks by keyword relevance
        String[] queryTerms = query.toLowerCase().split("\\s+");
        List<ScoredChunk> scoredChunks = new ArrayList<>();

        for (KnowledgeChunk chunk : allChunks) {
            String contentLower = chunk.getContent().toLowerCase();
            double score = 0;
            for (String term : queryTerms) {
                if (term.length() > 2 && contentLower.contains(term)) {
                    score += 1.0;
                    // Bonus for exact phrase matches
                    int idx = contentLower.indexOf(term);
                    while (idx >= 0) {
                        score += 0.5;
                        idx = contentLower.indexOf(term, idx + 1);
                    }
                }
            }
            if (score > 0) {
                scoredChunks.add(new ScoredChunk(chunk, score));
            }
        }

        // Sort by score descending
        scoredChunks.sort(Comparator.comparingDouble(ScoredChunk::score).reversed());

        if (scoredChunks.size() >= topK) {
            return scoredChunks.subList(0, topK).stream().map(ScoredChunk::chunk).toList();
        }

        // If not enough matching chunks, fill with distributed chunks
        Set<Long> usedIds = scoredChunks.stream().map(sc -> sc.chunk().getId()).collect(java.util.stream.Collectors.toSet());
        List<KnowledgeChunk> result = new ArrayList<>(scoredChunks.stream().map(ScoredChunk::chunk).toList());
        for (KnowledgeChunk chunk : allChunks) {
            if (result.size() >= topK) break;
            if (!usedIds.contains(chunk.getId())) {
                result.add(chunk);
            }
        }
        return result;
    }

    /**
     * Distribute chunks evenly across the document.
     */
    private List<KnowledgeChunk> distributeChunks(List<KnowledgeChunk> allChunks, int count) {
        if (allChunks.size() <= count) return allChunks;
        List<KnowledgeChunk> result = new ArrayList<>();
        double step = (double) allChunks.size() / count;
        for (int i = 0; i < count; i++) {
            int idx = (int) (i * step);
            result.add(allChunks.get(idx));
        }
        return result;
    }

    private String embeddingToString(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(String.format("%.6f", embedding[i]));
        }
        sb.append("]");
        return sb.toString();
    }

    private record ScoredChunk(KnowledgeChunk chunk, double score) {}
}
