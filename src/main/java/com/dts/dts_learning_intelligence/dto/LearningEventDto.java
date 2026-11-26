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
    private String activityType; // EXAM, PRACTICE_CHAPTER, SIMULATION, etc.
    private UUID sessionId;
    
    // Câu hỏi lý thuyết
    private Integer practiceQuestionId;
    private UUID contentBuilderQuestionId;
    
    // Tình huống mô phỏng
    private UUID scenarioId;
    private Integer scenarioNumber;
    private Integer score;           // Điểm số 0..5
    private Integer firstResponseMs; // Thời điểm bấm phím Space
    private Integer durationMs;
    
    private String outcome; // CORRECT, INCORRECT, UNANSWERED
    private OffsetDateTime occurredAt;
}
