package com.quiz.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles document parsing, text extraction, cleaning, and chunking.
 */
@Service
@Slf4j
public class DocumentProcessingService {

    private static final int TARGET_CHUNK_TOKENS = 500;
    private static final int OVERLAP_TOKENS = 50;
    private static final int MIN_CHUNK_LENGTH = 100; // characters
    private static final double APPROX_CHARS_PER_TOKEN = 4.0;

    /**
     * Extract text from a PDF input stream. Returns a map of page number → text.
     */
    public Map<Integer, String> extractTextFromPdf(InputStream pdfStream) {
        Map<Integer, String> pageTexts = new LinkedHashMap<>();
        try {
            byte[] bytes = pdfStream.readAllBytes();
            try (PDDocument document = Loader.loadPDF(bytes)) {
                PDFTextStripper stripper = new PDFTextStripper();
                int totalPages = document.getNumberOfPages();
                log.info("PDF has {} pages", totalPages);

                for (int i = 1; i <= totalPages; i++) {
                    stripper.setStartPage(i);
                    stripper.setEndPage(i);
                    String text = stripper.getText(document);
                    if (text != null && !text.isBlank()) {
                        pageTexts.put(i, text.trim());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to extract text from PDF: {}", e.getMessage());
            throw new RuntimeException("Failed to process PDF: " + e.getMessage(), e);
        }
        return pageTexts;
    }

    /**
     * Get total page count from a PDF.
     */
    public int getPdfPageCount(InputStream pdfStream) {
        try {
            byte[] bytes = pdfStream.readAllBytes();
            try (PDDocument document = Loader.loadPDF(bytes)) {
                return document.getNumberOfPages();
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to read PDF page count", e);
        }
    }

    /**
     * Clean extracted text — normalize whitespace, remove control chars, etc.
     */
    public String cleanText(String rawText) {
        if (rawText == null) return "";
        // Remove control characters except newlines and tabs
        String cleaned = rawText.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "");
        // Normalize multiple spaces
        cleaned = cleaned.replaceAll("[ \\t]+", " ");
        // Normalize newlines
        cleaned = cleaned.replaceAll("\\r\\n", "\n").replaceAll("\\r", "\n");
        cleaned = cleaned.replaceAll("\\n{3,}", "\n\n");
        return cleaned.trim();
    }

    /**
     * Chunk text into semantically meaningful pieces.
     * Respects paragraph and heading boundaries where possible.
     * Returns list of ChunkInfo records with metadata.
     */
    public List<ChunkInfo> chunkText(String fullText, Map<Integer, String> pageTexts) {
        List<ChunkInfo> chunks = new ArrayList<>();
        if (fullText == null || fullText.isBlank()) return chunks;

        // Split into paragraphs / lines
        String[] rawBlocks = fullText.split("\\n\\n+");
        List<String> paragraphs = new ArrayList<>();
        for (String block : rawBlocks) {
            String trimmed = block.trim();
            if (trimmed.isEmpty()) continue;
            // If a single block is huge (> 2000 chars), split by lines or sentences
            if (trimmed.length() > 2000) {
                String[] lines = trimmed.split("\\n+");
                StringBuilder sub = new StringBuilder();
                for (String line : lines) {
                    if (sub.length() + line.length() > 1800 && sub.length() > 0) {
                        paragraphs.add(sub.toString().trim());
                        sub.setLength(0);
                    }
                    if (sub.length() > 0) sub.append("\n");
                    sub.append(line);
                }
                if (sub.length() > 0) {
                    paragraphs.add(sub.toString().trim());
                }
            } else {
                paragraphs.add(trimmed);
            }
        }

        StringBuilder currentChunk = new StringBuilder();
        int currentStartOffset = 0;
        int chunkIndex = 0;
        String currentHeading = null;

        for (String paragraph : paragraphs) {
            if (paragraph.isEmpty()) continue;

            // Detect if this paragraph looks like a heading
            String detectedHeading = detectHeading(paragraph);
            if (detectedHeading != null) {
                // Save current chunk before starting new section
                if (currentChunk.length() >= MIN_CHUNK_LENGTH) {
                    chunks.add(createChunkInfo(
                            currentChunk.toString().trim(), chunkIndex++,
                            currentStartOffset, currentHeading, fullText, pageTexts
                    ));
                    // Overlap: keep last bit
                    String overlap = getOverlapText(currentChunk.toString());
                    currentChunk = new StringBuilder(overlap);
                    currentStartOffset = fullText.indexOf(currentChunk.toString(), currentStartOffset);
                    if (currentStartOffset < 0) currentStartOffset = 0;
                }
                currentHeading = detectedHeading;
            }

            // Check if adding this paragraph would exceed target size
            int projectedTokens = estimateTokens(currentChunk.toString() + "\n\n" + paragraph);
            if (projectedTokens > TARGET_CHUNK_TOKENS && currentChunk.length() >= MIN_CHUNK_LENGTH) {
                // Save current chunk
                chunks.add(createChunkInfo(
                        currentChunk.toString().trim(), chunkIndex++,
                        currentStartOffset, currentHeading, fullText, pageTexts
                ));
                // Start new chunk with overlap
                String overlap = getOverlapText(currentChunk.toString());
                currentChunk = new StringBuilder(overlap);
                currentStartOffset = fullText.indexOf(paragraph, currentStartOffset);
                if (currentStartOffset < 0) currentStartOffset = Math.max(0, fullText.length() - paragraph.length());
            }

            if (currentChunk.length() > 0) {
                currentChunk.append("\n\n");
            }
            currentChunk.append(paragraph);
        }

        // Don't forget the last chunk
        if (currentChunk.length() >= MIN_CHUNK_LENGTH) {
            chunks.add(createChunkInfo(
                    currentChunk.toString().trim(), chunkIndex,
                    currentStartOffset, currentHeading, fullText, pageTexts
            ));
        }

        log.info("Created {} chunks from text of {} characters", chunks.size(), fullText.length());
        return chunks;
    }

    private ChunkInfo createChunkInfo(String content, int index, int startOffset,
                                       String heading, String fullText,
                                       Map<Integer, String> pageTexts) {
        int endOffset = Math.min(startOffset + content.length(), fullText.length());
        Integer pageNumber = determinePageNumber(content, pageTexts);
        return new ChunkInfo(
                content, index, pageNumber, heading,
                startOffset, endOffset, estimateTokens(content)
        );
    }

    /**
     * Determine which page a chunk came from by finding the best matching page.
     */
    private Integer determinePageNumber(String chunkText, Map<Integer, String> pageTexts) {
        if (pageTexts == null || pageTexts.isEmpty()) return null;

        // Find the page with the highest overlap with this chunk
        String sample = chunkText.length() > 200 ? chunkText.substring(0, 200) : chunkText;
        int bestPage = -1;
        int bestScore = 0;

        for (Map.Entry<Integer, String> entry : pageTexts.entrySet()) {
            String pageText = entry.getValue();
            // Simple substring matching
            if (pageText.contains(sample)) {
                return entry.getKey();
            }
            // Fallback: count matching words
            String[] chunkWords = sample.split("\\s+");
            int score = 0;
            for (String word : chunkWords) {
                if (word.length() > 3 && pageText.contains(word)) score++;
            }
            if (score > bestScore) {
                bestScore = score;
                bestPage = entry.getKey();
            }
        }
        return bestPage > 0 ? bestPage : null;
    }

    private String detectHeading(String paragraph) {
        // Heuristics for heading detection
        if (paragraph.length() > 200) return null; // Too long for a heading
        if (paragraph.matches("^\\d+\\.\\d*\\s+.*")) return paragraph; // "1.2 Topic"
        if (paragraph.matches("^Chapter\\s+\\d+.*")) return paragraph; // "Chapter 3"
        if (paragraph.equals(paragraph.toUpperCase()) && paragraph.length() < 100 && paragraph.length() > 2) {
            return paragraph; // ALL CAPS heading
        }
        if (paragraph.matches("^[A-Z][A-Za-z\\s:–-]{3,60}$") && !paragraph.contains(".")) {
            return paragraph; // Title case short text without periods
        }
        return null;
    }

    private int estimateTokens(String text) {
        return (int) Math.ceil(text.length() / APPROX_CHARS_PER_TOKEN);
    }

    private String getOverlapText(String text) {
        int overlapChars = (int) (OVERLAP_TOKENS * APPROX_CHARS_PER_TOKEN);
        if (text.length() <= overlapChars) return "";
        return text.substring(text.length() - overlapChars);
    }

    /**
     * Record holding chunk data with metadata.
     */
    public record ChunkInfo(
            String content,
            int chunkIndex,
            Integer pageNumber,
            String sectionHeading,
            int startCharOffset,
            int endCharOffset,
            int tokenCount
    ) {}
}
