package com.dts.dts_learning_intelligence.rag.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GeminiGenerationResult {

    private String answerText;
    private int promptTokens;
    private int completionTokens;
    private int totalTokens;
    private boolean isFallback;
}
