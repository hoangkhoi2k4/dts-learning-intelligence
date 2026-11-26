package com.dts.dts_learning_intelligence.rag.dto;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScoredDocumentDto {

    private UUID id;
    private String documentTitle;
    private String documentNumber;
    private String chapter;
    private String article;
    private String clause;
    private String point;
    private String content;
    private Object metadata;
    private double rrfScore;
    private Integer vectorRank;
    private Integer keywordRank;

    public String formatCitation() {
        StringBuilder sb = new StringBuilder();
        if (documentNumber != null && !documentNumber.isBlank()) {
            sb.append(documentNumber);
        } else if (documentTitle != null) {
            sb.append(documentTitle);
        }
        if (article != null && !article.isBlank()) {
            sb.append(" - Điều ").append(article);
        }
        if (clause != null && !clause.isBlank()) {
            sb.append(" Khoản ").append(clause);
        }
        if (point != null && !point.isBlank()) {
            sb.append(" Điểm ").append(point);
        }
        return sb.toString();
    }
}
