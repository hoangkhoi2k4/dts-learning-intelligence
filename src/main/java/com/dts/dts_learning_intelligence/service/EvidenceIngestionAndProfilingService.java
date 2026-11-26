package com.dts.dts_learning_intelligence.service;

import com.dts.dts_learning_intelligence.dto.LearningEventDto;
import com.dts.dts_learning_intelligence.entity.AdaptiveSessionSnapshot;
import com.dts.dts_learning_intelligence.entity.LearnerSkillProfile;
import com.dts.dts_learning_intelligence.entity.LearningEvidence;
import com.dts.dts_learning_intelligence.entity.QuestionSkillMapping;
import com.dts.dts_learning_intelligence.entity.SimulationSkillMapping;
import com.dts.dts_learning_intelligence.entity.StudyPlan;
import com.dts.dts_learning_intelligence.repository.AdaptiveSessionSnapshotRepository;
import com.dts.dts_learning_intelligence.repository.LearnerSkillProfileRepository;
import com.dts.dts_learning_intelligence.repository.LearningEvidenceRepository;
import com.dts.dts_learning_intelligence.repository.QuestionSkillMappingRepository;
import com.dts.dts_learning_intelligence.repository.SimulationSkillMappingRepository;
import com.dts.dts_learning_intelligence.repository.StudyPlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class EvidenceIngestionAndProfilingService {

    private final LearningEvidenceRepository evidenceRepo;
    private final QuestionSkillMappingRepository mappingRepo;
    private final SimulationSkillMappingRepository simMappingRepo;
    private final LearnerSkillProfileRepository profileRepo;
    private final AdaptiveSessionSnapshotRepository snapshotRepo;
    private final StudyPlanRepository planRepo;

    // Hằng số làm mượt Bayes (Mục 9.2)
    private static final double ALPHA = 2.0;
    private static final double BETA = 2.0;
    private static final String ALGO_VERSION = "bayes-v1.0";
    private static final String DEFAULT_TAXONOMY_VERSION = "tax-v1.0";

    @Transactional
    public void processLearningEvent(LearningEventDto event) {
        if (event == null) {
            throw new IllegalArgumentException("LearningEventDto must not be null");
        }

        // Tự sinh giá trị mặc định cho mock/test nếu người dùng không truyền
        if (event.getEventId() == null) {
            event.setEventId(UUID.randomUUID());
        }
        if (event.getSourceOutcomeKey() == null || event.getSourceOutcomeKey().isBlank()) {
            event.setSourceOutcomeKey(event.getSessionId() != null 
                    ? event.getSessionId() + ":" + event.getPracticeQuestionId()
                    : UUID.randomUUID().toString());
        }
        if (event.getBankVersion() == null || event.getBankVersion().isBlank()) {
            event.setBankVersion("dts-2026-600-v1");
        }
        if (event.getActivityType() == null || event.getActivityType().isBlank()) {
            event.setActivityType("PRACTICE_CHAPTER");
        }
        if (event.getOccurredAt() == null) {
            event.setOccurredAt(OffsetDateTime.now());
        }
        if (event.getOutcome() == null || event.getOutcome().isBlank()) {
            event.setOutcome("CORRECT");
        }

        // 1. Chống trùng lặp event (Idempotency - Mục 8.2)
        if (evidenceRepo.existsByEventId(event.getEventId()) ||
            evidenceRepo.existsBySourceOutcomeKey(event.getSourceOutcomeKey())) {
            return;
        }

        String primarySkillCode;
        double earnedScore; // Điểm chuẩn hóa trong khoảng [0.0, 1.0]
        boolean isCritical = false;
        int currentAttemptNo = 1;
        double weight = 1.0;

        if ("SIMULATION".equalsIgnoreCase(event.getActivityType())) {
            // 1. Bỏ qua nếu không bấm phím (UNANSWERED / firstResponseMs == null hoặc score == null)
            if ("UNANSWERED".equalsIgnoreCase(event.getOutcome()) || event.getScore() == null) {
                saveIgnoredEvidence(event);
                return;
            }

            // 2. Tra cứu kỹ năng mô phỏng theo scenarioId hoặc scenarioNumber
            SimulationSkillMapping simMapping = null;
            if (event.getScenarioId() != null) {
                simMapping = simMappingRepo.findByBankVersionAndTaxonomyVersionAndScenarioId(
                        event.getBankVersion(), DEFAULT_TAXONOMY_VERSION, event.getScenarioId()).orElse(null);
            }
            if (simMapping == null && event.getScenarioNumber() != null) {
                simMapping = simMappingRepo.findByBankVersionAndTaxonomyVersionAndScenarioNumber(
                        event.getBankVersion(), DEFAULT_TAXONOMY_VERSION, event.getScenarioNumber()).orElse(null);
            }

            if (simMapping == null) {
                log.warn("Không tìm thấy mapping mô phỏng cho scenarioNumber={}, scenarioId={}",
                        event.getScenarioNumber(), event.getScenarioId());
                return;
            }

            primarySkillCode = simMapping.getPrimarySkillCode();
            // Quy đổi thang điểm 0..5 về [0.0 .. 1.0]
            earnedScore = Math.max(0.0, Math.min(5.0, event.getScore())) / 5.0;

            if (event.getScenarioNumber() != null) {
                long prevAttempts = evidenceRepo.countByUserIdAndLicenseClassAndBankVersionAndScenarioNumber(
                        event.getUserId(), event.getLicenseClass(), event.getBankVersion(), event.getScenarioNumber());
                currentAttemptNo = (int) prevAttempts + 1;
                weight = Math.pow(0.5, currentAttemptNo - 1);
            }

            LearningEvidence evidence = LearningEvidence.builder()
                    .eventId(event.getEventId())
                    .sourceOutcomeKey(event.getSourceOutcomeKey())
                    .userId(event.getUserId())
                    .licenseClass(event.getLicenseClass())
                    .bankVersion(event.getBankVersion())
                    .activityType(event.getActivityType())
                    .sessionId(event.getSessionId())
                    .scenarioId(simMapping.getScenarioId())
                    .scenarioNumber(simMapping.getScenarioNumber())
                    .score(event.getScore())
                    .firstResponseMs(event.getFirstResponseMs())
                    .earnedScore(BigDecimal.valueOf(earnedScore))
                    .outcome(event.getOutcome())
                    .evidenceIdentityStatus("VERIFIED")
                    .attemptNumberForQuestion(currentAttemptNo)
                    .evidenceWeight(BigDecimal.valueOf(weight))
                    .occurredAt(event.getOccurredAt())
                    .build();
            evidenceRepo.save(evidence);

        } else {
            // Luồng Lý thuyết
            if ("UNANSWERED".equalsIgnoreCase(event.getOutcome())) {
                saveIgnoredEvidence(event);
                return;
            }

            QuestionSkillMapping mapping = resolveTheoryMapping(event);
            if (mapping == null) {
                return;
            }

            primarySkillCode = mapping.getPrimarySkillCode();
            isCritical = Boolean.TRUE.equals(mapping.getIsCritical());
            earnedScore = "CORRECT".equalsIgnoreCase(event.getOutcome()) ? 1.0 : 0.0;

            // Tính số lần user đã gặp câu hỏi này để giảm trọng số (Quy tắc HS-02)
            long previousAttempts = evidenceRepo.countByUserIdAndLicenseClassAndBankVersionAndPracticeQuestionId(
                    event.getUserId(), event.getLicenseClass(), event.getBankVersion(), event.getPracticeQuestionId());
            currentAttemptNo = (int) previousAttempts + 1;
            weight = Math.pow(0.5, currentAttemptNo - 1); // Lần 1: 1.0, Lần 2: 0.5, Lần 3: 0.25
            if (isCritical) {
                weight *= 1.5; // Câu điểm liệt nhân 1.5 trọng số
            }

            LearningEvidence evidence = LearningEvidence.builder()
                    .eventId(event.getEventId())
                    .sourceOutcomeKey(event.getSourceOutcomeKey())
                    .userId(event.getUserId())
                    .licenseClass(event.getLicenseClass())
                    .bankVersion(event.getBankVersion())
                    .activityType(event.getActivityType())
                    .sessionId(event.getSessionId())
                    .practiceQuestionId(event.getPracticeQuestionId())
                    .contentBuilderQuestionId(mapping.getContentBuilderQuestionId())
                    .earnedScore(BigDecimal.valueOf(earnedScore))
                    .outcome(event.getOutcome()) // CORRECT, INCORRECT, UNANSWERED
                    .evidenceIdentityStatus("VERIFIED")
                    .attemptNumberForQuestion(currentAttemptNo)
                    .evidenceWeight(BigDecimal.valueOf(weight))
                    .occurredAt(event.getOccurredAt())
                    .build();
            evidenceRepo.save(evidence);
        }

        // 5. Cập nhật Bayes nếu câu hỏi đã được trả lời
        boolean isSuccessfulAnswer = earnedScore >= 0.6; // Đạt >= 3/5 điểm mô phỏng hoặc đúng lý thuyết
        recalculateSkillProfile(
                event.getUserId(),
                event.getLicenseClass(),
                event.getBankVersion(),
                DEFAULT_TAXONOMY_VERSION,
                primarySkillCode,
                isSuccessfulAnswer,
                event.getOccurredAt()
        );

        // 6. Kiểm tra và tự động chốt hoàn thành StudyPlan nếu event thuộc phiên ôn AI
        if (event.getSessionId() != null) {
            checkAndCompleteAdaptiveSession(event.getSessionId(), event.getOccurredAt());
        }
    }

    private QuestionSkillMapping resolveTheoryMapping(LearningEventDto event) {
        QuestionSkillMapping mapping = null;
        if (event.getContentBuilderQuestionId() != null) {
            mapping = mappingRepo.findByBankVersionAndTaxonomyVersionAndContentBuilderQuestionId(
                    event.getBankVersion(), DEFAULT_TAXONOMY_VERSION, event.getContentBuilderQuestionId()
            ).orElse(null);
        }
        if (mapping == null && event.getPracticeQuestionId() != null) {
            mapping = mappingRepo.findByBankVersionAndTaxonomyVersionAndPracticeQuestionId(
                    event.getBankVersion(), DEFAULT_TAXONOMY_VERSION, event.getPracticeQuestionId()
            ).orElse(null);
        }

        if (mapping == null) {
            log.warn("Không tìm thấy mapping cho câu hỏi practiceId={}, cbId={}, bankVersion={}",
                    event.getPracticeQuestionId(), event.getContentBuilderQuestionId(), event.getBankVersion());
        }
        return mapping;
    }

    private void saveIgnoredEvidence(LearningEventDto event) {
        LearningEvidence evidence = LearningEvidence.builder()
                .eventId(event.getEventId())
                .sourceOutcomeKey(event.getSourceOutcomeKey())
                .userId(event.getUserId())
                .licenseClass(event.getLicenseClass())
                .bankVersion(event.getBankVersion())
                .activityType(event.getActivityType())
                .sessionId(event.getSessionId())
                .practiceQuestionId(event.getPracticeQuestionId())
                .contentBuilderQuestionId(event.getContentBuilderQuestionId())
                .scenarioId(event.getScenarioId())
                .scenarioNumber(event.getScenarioNumber())
                .score(event.getScore())
                .firstResponseMs(event.getFirstResponseMs())
                .earnedScore(BigDecimal.ZERO)
                .outcome("UNANSWERED")
                .evidenceIdentityStatus("VERIFIED")
                .attemptNumberForQuestion(1)
                .evidenceWeight(BigDecimal.ONE)
                .occurredAt(event.getOccurredAt())
                .build();
        evidenceRepo.save(evidence);

        if (event.getSessionId() != null) {
            checkAndCompleteAdaptiveSession(event.getSessionId(), event.getOccurredAt());
        }
    }

    private void checkAndCompleteAdaptiveSession(UUID practiceSessionId, OffsetDateTime completedAt) {
        snapshotRepo.findByPracticeSessionId(practiceSessionId).ifPresent(snapshot -> {
            // Nếu phiên đã COMPLETED trước đó rồi thì bỏ qua (Idempotent - Quy tắc NT-07)
            if ("COMPLETED".equals(snapshot.getStatus())) {
                return;
            }

            StudyPlan plan = planRepo.findById(snapshot.getStudyPlanId()).orElse(null);
            if (plan == null) return;

            // Đếm tổng số câu đã nhận được kết quả từ dts-practice cho phiên này (bao gồm cả câu UNANSWERED)
            long recordedItemsCount = evidenceRepo.countBySessionId(practiceSessionId);

            // Khi số câu nhận về đủ số lượng câu của kế hoạch -> Chốt COMPLETED
            if (recordedItemsCount >= plan.getTargetQuestionCount()) {
                snapshot.setStatus("COMPLETED");
                snapshot.setCompletedAt(completedAt != null ? completedAt : OffsetDateTime.now());
                snapshotRepo.save(snapshot);

                plan.setStatus("COMPLETED");
                planRepo.save(plan);
            }
        });
    }

    private void recalculateSkillProfile(UUID userId, String licenseClass, String bankVersion,
                                         String taxonomyVersion, String skillCode,
                                         boolean isLastAnswerCorrect, OffsetDateTime occurredAt) {
        // Lấy toàn bộ bằng chứng hợp lệ (đã trả lời) của user ở kỹ năng này
        List<LearningEvidence> validEvidences = evidenceRepo.findValidEvidencesBySkill(
                userId, licenseClass, bankVersion, taxonomyVersion, skillCode);

        double weightedCorrectSum = 0.0;
        double totalWeightSum = 0.0;
        Set<String> distinctItems = new HashSet<>();
        Set<UUID> distinctSessions = new HashSet<>();
        int correctCount = 0;

        for (LearningEvidence ev : validEvidences) {
            double w = ev.getEvidenceWeight() != null ? ev.getEvidenceWeight().doubleValue() : 1.0;
            double scoreVal;
            if (ev.getEarnedScore() != null) {
                scoreVal = ev.getEarnedScore().doubleValue();
            } else {
                scoreVal = "CORRECT".equals(ev.getOutcome()) ? 1.0 : 0.0;
            }

            weightedCorrectSum += (scoreVal * w);
            totalWeightSum += w;

            if (scoreVal >= 0.6) {
                correctCount++;
            }

            if (ev.getScenarioNumber() != null) {
                distinctItems.add("SIM_" + ev.getScenarioNumber());
            } else if (ev.getPracticeQuestionId() != null) {
                distinctItems.add("Q_" + ev.getPracticeQuestionId());
            } else if (ev.getContentBuilderQuestionId() != null) {
                distinctItems.add("CB_" + ev.getContentBuilderQuestionId());
            }

            if (ev.getSessionId() != null) {
                distinctSessions.add(ev.getSessionId());
            }
        }

        // Công thức Bayes có làm mượt (Prior = 0.5 với ALPHA = 2.0, BETA = 2.0 hoặc tùy chọn)
        double mHat = (ALPHA + weightedCorrectSum) / (ALPHA + BETA + totalWeightSum);

        // Tính độ tin cậy dựa trên độ đa dạng câu hỏi / tình huống (tối thiểu 5 câu/tình huống độc lập đạt độ tin cậy cao)
        int distinctQCount = distinctItems.size();
        double confidence = Math.min(1.0, distinctQCount / 5.0);

        // Phân loại trạng thái năng lực (Mục 5.1)
        String status;
        String reason;
        if (distinctQCount < 3) {
            status = "INSUFFICIENT_DATA";
            reason = String.format("Mới luyện %d câu/tình huống độc lập (cần tối thiểu 3 câu để đánh giá).", distinctQCount);
        } else if (mHat < 0.60) {
            status = "NEEDS_REINFORCEMENT";
            reason = String.format("Tỷ lệ đúng có trọng số đạt %.0f%% qua %d câu/tình huống khác nhau — cần ưu tiên củng cố.", mHat * 100, distinctQCount);
        } else if (mHat < 0.82 || distinctQCount < 5) {
            status = "IMPROVING";
            reason = String.format("Đang tiến bộ (điểm ước lượng %.0f%%), cần thêm câu hỏi/tình huống mới để kiểm chứng độ ổn định.", mHat * 100);
        } else {
            status = "RELATIVELY_STABLE";
            reason = String.format("Nắm vững kiến thức (%.0f%% qua %d câu/tình huống độc lập), chuyển sang ôn giãn cách.", mHat * 100, distinctQCount);
        }

        // Tìm hoặc tạo mới bản ghi profile
        LearnerSkillProfile profile = profileRepo
                .findByUserIdAndLicenseClassAndBankVersionAndTaxonomyVersionAndSkillCode(
                        userId, licenseClass, bankVersion, taxonomyVersion, skillCode)
                .orElseGet(() -> LearnerSkillProfile.builder()
                        .userId(userId)
                        .licenseClass(licenseClass)
                        .bankVersion(bankVersion)
                        .taxonomyVersion(taxonomyVersion)
                        .skillCode(skillCode)
                        .reviewIntervalDays(1)
                        .consecutiveCorrectReviews(0)
                        .build());

        // Chính sách điều chỉnh lịch ôn giãn cách (Mục 7.2 & 9.4)
        updateSpacedRepetitionSchedule(profile, isLastAnswerCorrect, occurredAt);

        profile.setMasteryScore(BigDecimal.valueOf(mHat).setScale(4, RoundingMode.HALF_UP));
        profile.setConfidenceScore(BigDecimal.valueOf(confidence).setScale(4, RoundingMode.HALF_UP));
        profile.setDistinctQuestionsCount(distinctQCount);
        profile.setDistinctSessionsCount(distinctSessions.size());
        profile.setTotalAttemptsCount(validEvidences.size());
        profile.setCorrectAttemptsCount(correctCount);
        profile.setProficiencyStatus(status);
        profile.setExplanationReason(reason);
        profile.setAlgorithmVersion(ALGO_VERSION);
        profile.setLastPracticedAt(occurredAt);

        profileRepo.save(profile);
    }

    private void updateSpacedRepetitionSchedule(LearnerSkillProfile profile, boolean isCorrect, OffsetDateTime occurredAt) {
        int[] intervals = {1, 3, 7, 14}; // Mốc thử nghiệm mục 9.4
        if (isCorrect) {
            int streak = profile.getConsecutiveCorrectReviews() + 1;
            int idx = Math.min(streak - 1, intervals.length - 1);
            int days = intervals[idx];
            profile.setConsecutiveCorrectReviews(streak);
            profile.setReviewIntervalDays(days);
            profile.setNextReviewAt(occurredAt.plus(days, ChronoUnit.DAYS));
        } else {
            // Sai ở lần ôn -> reset chuỗi, rút ngắn khoảng cách về 1 ngày (Mục 7.2)
            profile.setConsecutiveCorrectReviews(0);
            profile.setReviewIntervalDays(1);
            profile.setNextReviewAt(occurredAt.plus(1, ChronoUnit.DAYS));
        }
    }
}
