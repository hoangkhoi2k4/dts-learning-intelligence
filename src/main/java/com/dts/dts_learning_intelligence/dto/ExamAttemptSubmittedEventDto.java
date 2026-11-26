package com.dts.dts_learning_intelligence.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
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
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ItemDto {
        private String sourceOutcomeKey;
    
        private Integer practiceQuestionId;
        private Integer sourceQuestionId;
        private UUID contentBuilderQuestionId;
        private Boolean critical;
        private String evidenceIdentityStatus;
        
        // Mô phỏng (0..5 điểm)
        private UUID scenarioId;
        private Integer scenarioNumber;
        private Integer score;           // Thang điểm 0..5
        private Integer firstResponseMs; // Thời điểm bấm phím Space (ms)
        private Integer durationMs;      // Tổng thời lượng video (ms)

        // Trường dùng chung
        private Integer chapterId;
        private String outcome;          // CORRECT, INCORRECT, UNANSWERED
        private Instant answeredAt;
    }
}
