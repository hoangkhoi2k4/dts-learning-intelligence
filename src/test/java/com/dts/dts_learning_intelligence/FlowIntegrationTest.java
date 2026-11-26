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
                .bankVersion("dts-2026-600-v1")
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
                .bankVersion("dts-2026-600-v1")
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
                testUserId, licenseClass, "dts-2026-600-v1", "tax-v1.0");
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
                testUserId, licenseClass, "dts-2026-600-v1", "tax-v1.0").size();

        // Goi skipPlan
        var skipResponse = planService.skipPlan(testUserId, planId);
        Assertions.assertEquals("SKIPPED", skipResponse.status());

        // Kiem tra DB: plan thanh SKIPPED
        StudyPlan skippedPlan = planRepo.findById(planId).orElseThrow();
        Assertions.assertEquals("SKIPPED", skippedPlan.getStatus(), "StudyPlan phai la SKIPPED");

        // Ho so ky nang giu nguyen
        int profileCountAfter = profileRepo.findByUserIdAndLicenseClassAndBankVersionAndTaxonomyVersion(
                testUserId, licenseClass, "dts-2026-600-v1", "tax-v1.0").size();
        Assertions.assertEquals(profileCountBefore, profileCountAfter, "Ho so ky nang khong duoc bi thay doi hay tao rac khi skip");
    }

    @Autowired
    private com.dts.dts_learning_intelligence.controller.LearningIntelligenceController controller;

    @Test
    void testGetMyProfileReturnsAll14Skills() {
        UUID newUserId = UUID.randomUUID();
        String licenseClass = "B";

        var response = (java.util.Map<?, ?>) controller.getMyProfile(newUserId, licenseClass).getBody();
        Assertions.assertNotNull(response);
        Assertions.assertEquals("INSUFFICIENT_DATA", response.get("overallStatus"));

        @SuppressWarnings("unchecked")
        List<LearnerSkillProfile> skills = (List<LearnerSkillProfile>) response.get("skills");
        Assertions.assertNotNull(skills);
        Assertions.assertFalse(skills.isEmpty(), "API phai tra ve danh sach ky nang");

        // Tat ca cac ky nang cua nguoi moi tinh phai co trang thai INSUFFICIENT_DATA va masteryScore = 0.5
        for (LearnerSkillProfile skill : skills) {
            Assertions.assertEquals("INSUFFICIENT_DATA", skill.getProficiencyStatus());
            Assertions.assertEquals(new BigDecimal("0.5000"), skill.getMasteryScore());
        }
    }

    @Autowired
    private com.dts.dts_learning_intelligence.repository.SimulationSkillMappingRepository simMappingRepo;

    @Test
    void testSimulationProfilingFlow() {
        UUID testUserId = UUID.randomUUID();
        String licenseClass = "B";
        String bankVersion = "dts-2026-600-v1";
        String taxonomyVersion = "tax-v1.0";
        String simSkillCode = "SIM_CH1_URBAN";

        // Dam bao co mapping mo phong trong DB
        var existingMapping = simMappingRepo.findByBankVersionAndTaxonomyVersionAndScenarioNumber(
                bankVersion, taxonomyVersion, 1);
        if (existingMapping.isEmpty()) {
            simMappingRepo.save(com.dts.dts_learning_intelligence.entity.SimulationSkillMapping.builder()
                    .bankVersion(bankVersion)
                    .taxonomyVersion(taxonomyVersion)
                    .scenarioId(UUID.randomUUID())
                    .scenarioNumber(1)
                    .chapterId(1)
                    .primarySkillCode(simSkillCode)
                    .title("Tình huống 1: Người đi bộ băng qua đường")
                    .status("PUBLISHED")
                    .build());
        }

        // 1. Gui event mo phong dat 5/5 diem (earnedScore = 1.0)
        LearningEventDto simEv1 = LearningEventDto.builder()
                .eventId(UUID.randomUUID())
                .userId(testUserId)
                .licenseClass(licenseClass)
                .bankVersion(bankVersion)
                .activityType("SIMULATION")
                .scenarioNumber(1)
                .score(5)
                .firstResponseMs(3200)
                .outcome("CORRECT")
                .occurredAt(OffsetDateTime.now())
                .build();
        ingestionService.processLearningEvent(simEv1);

        // Kiem tra profile ky nang mo phong duoc cap nhat
        List<LearnerSkillProfile> profiles = profileRepo.findByUserIdAndLicenseClassAndBankVersionAndTaxonomyVersion(
                testUserId, licenseClass, bankVersion, taxonomyVersion);
        Assertions.assertFalse(profiles.isEmpty());

        LearnerSkillProfile simProfile = profiles.stream()
                .filter(p -> simSkillCode.equals(p.getSkillCode()))
                .findFirst()
                .orElse(null);

        Assertions.assertNotNull(simProfile, "Profile ky nang mo phong phai duoc tao");
        // Prior mượt Bayes (ALPHA=2, BETA=2), 1 lần đúng điểm 1.0 -> (2 + 1.0) / (2 + 2 + 1.0) = 3/5 = 0.6000
        Assertions.assertEquals(new BigDecimal("0.6000"), simProfile.getMasteryScore());
        Assertions.assertEquals(1, simProfile.getCorrectAttemptsCount());

        // 2. Test goi endpoint /me/simulation-playlists
        var playlistResponse = (java.util.Map<?, ?>) controller.createSimulationPlaylist(
                testUserId,
                new com.dts.dts_learning_intelligence.controller.LearningIntelligenceController.CreateSimulationPlaylistRequest(
                        licenseClass, 5, bankVersion)
        ).getBody();

        Assertions.assertNotNull(playlistResponse);
        Assertions.assertEquals(testUserId, playlistResponse.get("userId"));
        @SuppressWarnings("unchecked")
        List<?> items = (List<?>) playlistResponse.get("items");
        Assertions.assertNotNull(items);
        Assertions.assertFalse(items.isEmpty(), "Playlist mo phong phai tra ve it nhat 1 tinh huong");
    }
}
