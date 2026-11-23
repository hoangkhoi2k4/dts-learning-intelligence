package com.dts.dts_learning_intelligence.controller;

import com.dts.dts_learning_intelligence.dto.ExamAttemptSubmittedEventDto;
import com.dts.dts_learning_intelligence.dto.LearningEventDto;
import com.dts.dts_learning_intelligence.repository.LearnerSkillProfileRepository;
import com.dts.dts_learning_intelligence.service.AdaptiveRecommendationService;
import com.dts.dts_learning_intelligence.service.EvidenceIngestionAndProfilingService;
import com.dts.dts_learning_intelligence.service.StudyPlanOrchestrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/learning-intelligence/v1")
@RequiredArgsConstructor
public class LearningIntelligenceController {

    private final LearnerSkillProfileRepository profileRepo;
    private final StudyPlanOrchestrationService planService;
    private final AdaptiveRecommendationService recommendationService;
    private final EvidenceIngestionAndProfilingService ingestionService;

    private static final String DEFAULT_BANK_VERSION = "dts-2026-600-v1";
    private static final String DEFAULT_TAXONOMY_VERSION = "tax-v1.0";

    // 1. Xem hồ sơ năng lực theo kỹ năng (Mục 10.2)
    @GetMapping("/me/profile")
    public ResponseEntity<?> getMyProfile(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestParam(defaultValue = "B") String licenseClass) {
        var profiles = profileRepo.findByUserIdAndLicenseClassAndBankVersionAndTaxonomyVersion(
                userId, licenseClass, DEFAULT_BANK_VERSION, DEFAULT_TAXONOMY_VERSION);
        return ResponseEntity.ok(Map.of(
                "userId", userId,
                "licenseClass", licenseClass,
                "overallStatus", profiles.isEmpty() ? "INSUFFICIENT_DATA" : "ACTIVE",
                "skills", profiles
        ));
    }

    // 2. Xem gợi ý câu hỏi và lý do (Mục 10.2)
    @GetMapping("/me/recommendations")
    public ResponseEntity<?> getRecommendations(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestParam(defaultValue = "B") String licenseClass,
            @RequestParam(defaultValue = "5") int limit) {
        var items = recommendationService.selectTopQuestions(userId, licenseClass, DEFAULT_BANK_VERSION, DEFAULT_TAXONOMY_VERSION, limit);
        return ResponseEntity.ok(Map.of("licenseClass", licenseClass, "items", items));
    }

    // 3. Tạo kế hoạch học tập theo mục tiêu (Mục 10.2)
    @PostMapping("/me/plans")
    public ResponseEntity<?> createStudyPlan(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestBody CreatePlanRequest req) {
        int count = (req.targetQuestionCount() != null && req.targetQuestionCount() > 0) ? req.targetQuestionCount() : 5;
        String license = (req.licenseClass() != null) ? req.licenseClass() : "B";
        return ResponseEntity.ok(planService.createPlan(userId, license, count));
    }

    // 4. Bắt đầu phiên luyện tập thực tế (Mục 10.2 - Hỗ trợ Idempotency)
    @PostMapping("/me/plans/{planId}/start")
    public ResponseEntity<?> startPlan(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            @PathVariable UUID planId) {
        return ResponseEntity.ok(planService.startPlanSession(userId, planId, idempotencyKey));
    }

    // 5. Danh sách kỹ năng đến hạn ôn chống quên (Mục 10.2)
    @GetMapping("/me/reviews/due")
    public ResponseEntity<?> getDueReviews(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestParam(defaultValue = "B") String licenseClass) {
        var allProfiles = profileRepo.findByUserIdAndLicenseClassAndBankVersionAndTaxonomyVersion(
                userId, licenseClass, DEFAULT_BANK_VERSION, DEFAULT_TAXONOMY_VERSION);
        OffsetDateTime now = OffsetDateTime.now();
        var dueList = allProfiles.stream()
                .filter(p -> p.getNextReviewAt() != null && !p.getNextReviewAt().isAfter(now))
                .toList();
        return ResponseEntity.ok(Map.of("dueCount", dueList.size(), "dueSkills", dueList));
    }

    // 6. [MOCK ENDPOINT] Giả lập dts-practice bắn Learning Event sang để test
    @PostMapping("/internal/mock/events")
    public ResponseEntity<?> ingestMockEvent(@RequestBody LearningEventDto event) {
        ingestionService.processLearningEvent(event);
        return ResponseEntity.ok(Map.of("status", "PROCESSED", "eventId", event.getEventId()));
    }

    @PostMapping("/internal/mock/batch-events")
    public ResponseEntity<?> ingestMockBatchEvent(@RequestBody ExamAttemptSubmittedEventDto batchEvent) {
        if (batchEvent.getItems() != null) {
            for (var item : batchEvent.getItems()) {
                UUID itemEventId = UUID.nameUUIDFromBytes(item.getSourceOutcomeKey().getBytes(StandardCharsets.UTF_8));
                java.time.Instant occurredInstant = item.getAnsweredAt() != null ? item.getAnsweredAt() : batchEvent.getOccurredAt();
                java.time.OffsetDateTime occurredAt = occurredInstant != null ? occurredInstant.atOffset(java.time.ZoneOffset.UTC) : null;

                LearningEventDto singleEvent = LearningEventDto.builder()
                        .eventId(itemEventId)
                        .sourceOutcomeKey(item.getSourceOutcomeKey())
                        .userId(batchEvent.getUserId())
                        .licenseClass(batchEvent.getLicenseClass())
                        .bankVersion(batchEvent.getBankVersion())
                        .activityType(batchEvent.getActivityType())
                        .sessionId(batchEvent.getSessionId())
                        .practiceQuestionId(item.getPracticeQuestionId())
                        .contentBuilderQuestionId(item.getContentBuilderQuestionId())
                        .outcome(item.getOutcome())
                        .occurredAt(occurredAt)
                        .build();
                ingestionService.processLearningEvent(singleEvent);
            }
        }
        return ResponseEntity.ok(Map.of(
                "status", "BATCH_PROCESSED",
                "sessionId", batchEvent.getSessionId(),
                "processedCount", batchEvent.getItems() != null ? batchEvent.getItems().size() : 0
        ));
    }

    @PostMapping("/me/plans/{planId}/skip")
    public ResponseEntity<?> skipPlan(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID planId) {
        return ResponseEntity.ok(planService.skipPlan(userId, planId));
    }

    public record CreatePlanRequest(String licenseClass, Integer targetQuestionCount) {}
}
