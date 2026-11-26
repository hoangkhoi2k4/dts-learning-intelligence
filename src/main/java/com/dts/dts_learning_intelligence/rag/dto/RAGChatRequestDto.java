package com.dts.dts_learning_intelligence.rag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RAGChatRequestDto {

    @NotBlank(message = "Câu hỏi không được để trống")
    @Size(max = 500, message = "Câu hỏi không được vượt quá 500 ký tự")
    private String message;

    private String contextType; // GENERAL, THEORY_QUESTION, SIMULATION_SCENARIO, THEORY_SKILL
    private String contextRefId; // ID câu hỏi hoặc mã kỹ năng
    
    @Builder.Default
    private String licenseClass = "B";
}
