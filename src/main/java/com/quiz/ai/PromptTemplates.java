package com.quiz.ai;

/**
 * Prompt templates for all AI operations.
 * Each prompt is purpose-built and uses explicit instructions to ensure
 * grounded, structured, and high-quality output.
 */
public final class PromptTemplates {

    private PromptTemplates() {}

    // ═══════════════════════════════════════════════════════════════
    // 1. TOPIC EXTRACTION
    // ═══════════════════════════════════════════════════════════════

    public static final String TOPIC_EXTRACTION_SYSTEM = """
        You are an expert educational content analyst. Your job is to identify
        the major topics and sections within educational material.
        
        Rules:
        - Identify 3-10 major topics/sections from the provided text
        - Each topic should be a meaningful educational concept
        - Output ONLY valid JSON, no other text
        - Use the exact JSON format specified
        """;

    public static String topicExtractionPrompt(String text) {
        // Truncate to ~4000 chars for topic extraction (we don't need the full text)
        String truncated = text.length() > 6000 ? text.substring(0, 6000) + "..." : text;
        return """
            Analyze the following educational material and identify the major topics/sections.
            
            Return a JSON array of topic objects:
            [
              {"name": "Topic Name", "description": "Brief one-line description", "importance": "HIGH|MEDIUM|LOW"}
            ]
            
            Material:
            \"\"\"
            %s
            \"\"\"
            """.formatted(truncated);
    }

    // ═══════════════════════════════════════════════════════════════
    // 2. QUESTION GENERATION
    // ═══════════════════════════════════════════════════════════════

    public static final String QUESTION_GENERATION_SYSTEM = """
        You are an expert educational assessment creator. You generate high-quality
        multiple-choice questions (MCQs) from provided source material.
        
        CRITICAL RULES:
        1. Every question MUST be answerable from the provided source context.
        2. Do NOT invent facts or information not present in the source.
        3. Each question must have exactly 4 options (A, B, C, D).
        4. Exactly ONE option must be correct.
        5. Distractors (wrong options) must be plausible but clearly incorrect.
        6. Questions should test understanding, not just recall.
        7. Avoid ambiguous or trick questions.
        8. Include a clear explanation referencing the source material.
        9. Output ONLY valid JSON, no other text.
        10. Do NOT generate duplicate or near-duplicate questions.
        """;

    public static String questionGenerationPrompt(String context, int count, String difficulty, String topicFocus) {
        String difficultyInstruction = switch (difficulty.toUpperCase()) {
            case "EASY" -> "Generate EASY questions that test basic recall and understanding.";
            case "HARD" -> "Generate HARD questions that require analysis, synthesis, or application of concepts.";
            case "MIXED" -> "Generate a MIX of easy (30%), medium (50%), and hard (20%) questions.";
            default -> "Generate MEDIUM difficulty questions that test comprehension and application.";
        };

        String topicInstruction = (topicFocus != null && !topicFocus.isBlank())
                ? "Focus on these topics: " + topicFocus
                : "Cover the material broadly and diversely.";

        return """
            Generate exactly %d multiple-choice questions from the following source material.
            
            %s
            %s
            
            Return a JSON array of question objects:
            [
              {
                "questionText": "The question text",
                "optionA": "First option",
                "optionB": "Second option",
                "optionC": "Third option",
                "optionD": "Fourth option",
                "correctOption": "A",
                "explanation": "Why this answer is correct, referencing the source",
                "difficulty": "EASY|MEDIUM|HARD",
                "topic": "The topic this question relates to",
                "confidence": 0.95
              }
            ]
            
            Source Material:
            \"\"\"
            %s
            \"\"\"
            """.formatted(count, difficultyInstruction, topicInstruction, context);
    }

    // ═══════════════════════════════════════════════════════════════
    // 3. QUESTION VALIDATION
    // ═══════════════════════════════════════════════════════════════

    public static final String VALIDATION_SYSTEM = """
        You are a quality assurance expert for educational assessments.
        Your job is to validate generated questions for correctness and quality.
        
        Rules:
        - Check if the question is answerable from the source
        - Verify exactly one correct answer exists
        - Check for ambiguity
        - Check for duplicate content
        - Output ONLY valid JSON
        """;

    public static String validationPrompt(String questionJson, String sourceContext) {
        return """
            Validate the following generated question against its source material.
            
            Question:
            %s
            
            Source Context:
            \"\"\"
            %s
            \"\"\"
            
            Return a JSON object:
            {
              "isValid": true/false,
              "issues": ["list of issues found, empty if valid"],
              "qualityScore": 0.0-1.0,
              "correctedAnswer": "A/B/C/D or null if original is correct",
              "suggestion": "improvement suggestion or null"
            }
            """.formatted(questionJson, sourceContext);
    }

    // ═══════════════════════════════════════════════════════════════
    // 4. KNOWLEDGE SLIDE — SOURCE GROUNDED
    // ═══════════════════════════════════════════════════════════════

    public static final String KNOWLEDGE_SLIDE_SYSTEM = """
        You are an expert educational content creator. You create concise,
        well-structured knowledge slides that explain concepts clearly.
        
        Rules:
        - Keep the slide concise (150-300 words)
        - Use clear, educational language
        - Include key points, examples, and common mistakes where relevant
        - Structure content with a title, explanation, key points, and optional example
        - Output ONLY valid JSON
        - If source material is provided, base your explanation on it
        - Do NOT invent facts not supported by the source
        """;

    public static String knowledgeSlidePrompt(String questionText, String correctAnswer,
                                               String explanation, String sourceContext,
                                               boolean hasSource) {
        String sourceInstruction = hasSource
                ? "Use the provided source material to ground your explanation. This is source-verified content."
                : "No source document is available. Generate an explanatory slide based on the question and answer. Note: this content is AI-generated and not verified against a source document.";

        String contextSection = (sourceContext != null && !sourceContext.isBlank())
                ? "Source Material:\n\"\"\"\n" + sourceContext + "\n\"\"\""
                : "";

        return """
            Generate a concise knowledge slide for the following question.
            
            %s
            
            Question: %s
            Correct Answer: %s
            Explanation: %s
            %s
            
            Return a JSON object:
            {
              "title": "Concise slide title",
              "content": "Markdown-formatted educational content with:\n- Brief explanation\n- Key concepts (bullet points)\n- Important facts\n- Example (if relevant)\n- Common mistake (if relevant)"
            }
            """.formatted(sourceInstruction, questionText, correctAnswer,
                explanation != null ? explanation : "Not provided",
                contextSection);
    }

    // ═══════════════════════════════════════════════════════════════
    // 5. QUESTION SELECTION (for AUTO mode)
    // ═══════════════════════════════════════════════════════════════

    public static final String SELECTION_SYSTEM = """
        You are an expert assessment designer. Your job is to select the best
        subset of questions from a pool to create a balanced, high-quality quiz.
        
        Selection criteria:
        1. Maximize topic coverage
        2. Ensure difficulty diversity
        3. Minimize redundancy
        4. Prefer higher quality scores
        5. Ensure educational value
        
        Output ONLY valid JSON.
        """;

    public static String selectionPrompt(String questionsJson, int targetCount) {
        return """
            From the following pool of candidate questions, select the best %d questions
            that together form a balanced, comprehensive quiz.
            
            Optimize for:
            - Topic coverage (spread across different topics)
            - Difficulty mix (not all the same difficulty)
            - No redundancy (avoid similar questions)
            - Quality (prefer higher confidence scores)
            
            Questions pool:
            %s
            
            Return a JSON array of selected question IDs:
            {"selectedIds": [1, 5, 8, ...]}
            """.formatted(targetCount, questionsJson);
    }
}
