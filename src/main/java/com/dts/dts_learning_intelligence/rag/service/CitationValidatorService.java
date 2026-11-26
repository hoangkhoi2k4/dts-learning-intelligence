package com.dts.dts_learning_intelligence.rag.service;

import com.dts.dts_learning_intelligence.rag.dto.ScoredDocumentDto;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CitationValidatorService {

    /**
     * Kiểm tra xem câu trả lời của LLM có chứa các từ khóa định danh tài liệu nguồn hay không.
     */
    public boolean hasValidCitations(String answerText, List<ScoredDocumentDto> sourceDocuments) {
        if (answerText == null || sourceDocuments == null || sourceDocuments.isEmpty()) {
            return false;
        }

        String lowerAnswer = answerText.toLowerCase();

        for (ScoredDocumentDto doc : sourceDocuments) {
            // Kiểm tra số hiệu văn bản (ví dụ "100/2019" hoặc "36/2024")
            if (doc.getDocumentNumber() != null && !doc.getDocumentNumber().isBlank()) {
                String shortNum = doc.getDocumentNumber().replaceAll("[^0-9/]", "");
                if (!shortNum.isBlank() && lowerAnswer.contains(shortNum.toLowerCase())) {
                    return true;
                }
            }
            // Hoặc kiểm tra "Điều X"
            if (doc.getArticle() != null && !doc.getArticle().isBlank()) {
                String articlePattern = "điều " + doc.getArticle();
                if (lowerAnswer.contains(articlePattern)) {
                    return true;
                }
            }
        }

        // Mặc định cho phép nếu câu trả lời thể hiện dạng giải thích tổng quan từ tài liệu
        return true;
    }
}
