package com.dts.dts_learning_intelligence;

import com.dts.dts_learning_intelligence.dto.LearningEventDto;
import com.dts.dts_learning_intelligence.entity.AdaptiveSessionSnapshot;
import com.dts.dts_learning_intelligence.entity.LearnerSkillProfile;
import com.dts.dts_learning_intelligence.entity.StudyPlan;
import com.dts.dts_learning_intelligence.repository.AdaptiveSessionSnapshotRepository;
import com.dts.dts_learning_intelligence.repository.LearnerSkillProfileRepository;
import com.dts.dts_learning_intelligence.repository.StudyPlanRepository;
import com.dts.dts_learning_intelligence.service.AdaptiveRecommendationService;
import com.dts.dts_learning_intelligence.service.EvidenceIngestionAndProfilingService;
import com.dts.dts_learning_intelligence.service.StudyPlanOrchestrationService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@SpringBootTest
public class FlowIntegrationTest {

    @Autowired
    private StudyPlanOrchestrationService planService;

    @Autowired
    private EvidenceIngestionAndProfilingService ingestionService;

    @Autowired
    private StudyPlanRepository planRepo;

    @Autowired
    private AdaptiveSessionSnapshotRepository snapshotRepo;

    @Autowired
    private LearnerSkillProfileRepository profileRepo;

    @Test
    void testCompletedFlow() {
        UUID testUserId = UUID.randomUUID();
        String licenseClass = "B";

        // 1. Tao ke hoach targetQuestionCount = 2
        var planResponse = planService.createPlan(testUserId, licenseClass, 2);
        UUID planId = planResponse.planId();
        Assertions.assertNotNull(planId);
        Assertions.assertEquals(2, planResponse.totalQuestions());
        Assertions.assertEquals("PROPOSED", planResponse.status());

        // 2. Start plan de sinh session va snapshot
        var startResponse = planService.startPlanSession(testUserId, planId, "test-key-" + UUID.randomUUID());
        UUID practiceSessionId = startResponse.practiceSessionId();
        Assertions.assertNotNull(practiceSessionId);
        Assertions.assertEquals("STARTED", startResponse.status());

        // Lay thong tin question tu planResponse
        List<AdaptiveRecommendationService.RecommendedQuestionItem> questions = planResponse.questions();
        Assertions.assertEquals(2, questions.size());

        AdaptiveRecommendationService.RecommendedQuestionItem q1 = questions.get(0);
        AdaptiveRecommendationService.RecommendedQuestionItem q2 = questions.get(1);

        // 3. Ban event 1: CORRECT
        LearningEventDto ev1 = LearningEventDto.builder()
                .eventId(UUID.randomUUID())
                .userId(testUserId)
                .licenseClass(licenseClass)
                .bankVersion("2025.1")
                .activityType("ADAPTIVE_PRACTICE")
                .sessionId(practiceSessionId)
                .practiceQuestionId(q1.practiceQuestionId())
                .outcome("CORRECT")
                .occurredAt(OffsetDateTime.now())
                .build();
        ingestionService.processLearningEvent(ev1);

        // Kiem tra giua chung: moi co 1 cau nen plan chua COMPLETED
        StudyPlan planMid = planRepo.findById(planId).orElseThrow();
        Assertions.assertEquals("IN_PROGRESS", planMid.getStatus());

        // 4. Ban event 2: UNANSWERED
        LearningEventDto ev2 = LearningEventDto.builder()
                .eventId(UUID.randomUUID())
                .userId(testUserId)
                .licenseClass(licenseClass)
                .bankVersion("2025.1")
                .activityType("ADAPTIVE_PRACTICE")
                .sessionId(practiceSessionId)
                .practiceQuestionId(q2.practiceQuestionId())
                .outcome("UNANSWERED")
                .occurredAt(OffsetDateTime.now())
                .build();
        ingestionService.processLearningEvent(ev2);

        // 5. Kiem tra DB sau khi du 2 cau:
        StudyPlan completedPlan = planRepo.findById(planId).orElseThrow();
        Assertions.assertEquals("COMPLETED", completedPlan.getStatus(), "StudyPlan phai chuyen thanh COMPLETED");

        AdaptiveSessionSnapshot completedSnapshot = snapshotRepo.findByPracticeSessionId(practiceSessionId).orElseThrow();
        Assertions.assertEquals("COMPLETED", completedSnapshot.getStatus(), "AdaptiveSessionSnapshot phai chuyen thanh COMPLETED");
        Assertions.assertNotNull(completedSnapshot.getCompletedAt(), "completedAt khong duoc null");

        // Ho so ky nang duoc cap nhat cho cau dung (q1)
        List<LearnerSkillProfile> profiles = profileRepo.findByUserIdAndLicenseClassAndBankVersionAndTaxonomyVersion(
                testUserId, licenseClass, "2025.1", "tax-v1.0");
        Assertions.assertFalse(profiles.isEmpty(), "Ho so ky nang phai duoc tao/cap nhat");
        LearnerSkillProfile p1 = profiles.stream()
                .filter(p -> p.getSkillCode().equals(q1.skillCode()))
                .findFirst()
                .orElse(null);
        Assertions.assertNotNull(p1, "Ky nang cua cau hoi 1 phai co profile");
        Assertions.assertTrue(p1.getMasteryScore().compareTo(BigDecimal.ZERO) > 0, "Diem mastery phai lon hon 0");
    }

    @Test
    void testSkippedFlow() {
        UUID testUserId = UUID.randomUUID();
        String licenseClass = "B";

        // Tao ke hoach
        var planResponse = planService.createPlan(testUserId, licenseClass, 2);
        UUID planId = planResponse.planId();

        // Kiem tra profile truoc khi skip
        int profileCountBefore = profileRepo.findByUserIdAndLicenseClassAndBankVersionAndTaxonomyVersion(
                testUserId, licenseClass, "2025.1", "tax-v1.0").size();

        // Goi skipPlan
        var skipResponse = planService.skipPlan(testUserId, planId);
        Assertions.assertEquals("SKIPPED", skipResponse.status());

        // Kiem tra DB: plan thanh SKIPPED
        StudyPlan skippedPlan = planRepo.findById(planId).orElseThrow();
        Assertions.assertEquals("SKIPPED", skippedPlan.getStatus(), "StudyPlan phai la SKIPPED");

        // Ho so ky nang giu nguyen
        int profileCountAfter = profileRepo.findByUserIdAndLicenseClassAndBankVersionAndTaxonomyVersion(
                testUserId, licenseClass, "2025.1", "tax-v1.0").size();
        Assertions.assertEquals(profileCountBefore, profileCountAfter, "Ho so ky nang khong duoc bi thay doi hay tao rac khi skip");
    }
}
