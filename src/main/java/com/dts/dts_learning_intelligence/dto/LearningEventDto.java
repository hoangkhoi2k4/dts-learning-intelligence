package com.dts.dts_learning_intelligence.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LearningEventDto {
    private UUID eventId;
    private String sourceOutcomeKey;
    private UUID userId;
    private String licenseClass;
    private String bankVersion;
    private String activityType;
    private UUID sessionId;
    private Integer practiceQuestionId;
    private UUID contentBuilderQuestionId;
    private String outcome; // CORRECT, INCORRECT, UNANSWERED
    private OffsetDateTime occurredAt;
}
