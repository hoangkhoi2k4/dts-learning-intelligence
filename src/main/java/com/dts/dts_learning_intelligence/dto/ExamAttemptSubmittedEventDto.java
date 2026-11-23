package com.dts.dts_learning_intelligence.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExamAttemptSubmittedEventDto {
    private UUID eventId;
    private String eventType;
    private Integer schemaVersion;
    private String sourceService;
    private Instant occurredAt;
    private UUID userId;
    private String licenseClass;
    private String bankVersion;
    private String activityType;
    private UUID sessionId;
    private List<ItemDto> items;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemDto {
        private String sourceOutcomeKey;
        private Integer practiceQuestionId;
        private Integer sourceQuestionId;
        private UUID contentBuilderQuestionId;
        private Integer chapterId;
        private Boolean critical;
        private String outcome; // CORRECT, INCORRECT, UNANSWERED
        private Instant answeredAt;
        private String evidenceIdentityStatus;
    }
}