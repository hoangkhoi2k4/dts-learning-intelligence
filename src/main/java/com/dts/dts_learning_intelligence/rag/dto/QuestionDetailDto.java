package com.dts.dts_learning_intelligence.rag.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionDetailDto {
    private Integer questionId;
    private Integer sourceQuestionId;
    private Integer chapter;
    private String questionText;
    private List<Map<String, Object>> options;
    private String correctAnswer;
    private Boolean isCritical;
    private String explanation;
    private String skillCode;
    private String skillName;
}
