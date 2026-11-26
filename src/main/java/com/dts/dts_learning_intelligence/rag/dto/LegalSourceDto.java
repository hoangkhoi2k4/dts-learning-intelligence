package com.dts.dts_learning_intelligence.rag.dto;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LegalSourceDto {

    private UUID sourceId;
    private String documentTitle;
    private String documentNumber;
    private String chapter;
    private String article;
    private String clause;
    private String point;
    private String citation;
    private String snippet;
    private String validityStatus;
}
