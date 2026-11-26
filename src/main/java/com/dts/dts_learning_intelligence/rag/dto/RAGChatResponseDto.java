package com.dts.dts_learning_intelligence.rag.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RAGChatResponseDto {

    private String answer;
    private List<LegalSourceDto> sources;
    private String contextUsed;
    private int remainingDailyQueries;
    private boolean isFallback;
    private List<String> suggestedActions;
    private QuestionDetailDto questionDetail;
}
