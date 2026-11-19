package com.dts.dts_learning_intelligence.service;

import com.dts.dts_learning_intelligence.dto.LearningEventDto;
import com.dts.dts_learning_intelligence.entity.LearnerSkillProfile;
import com.dts.dts_learning_intelligence.entity.LearningEvidence;
import com.dts.dts_learning_intelligence.entity.QuestionSkillMapping;
import com.dts.dts_learning_intelligence.repository.LearnerSkillProfileRepository;
import com.dts.dts_learning_intelligence.repository.LearningEvidenceRepository;
import com.dts.dts_learning_intelligence.repository.QuestionSkillMappingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class EvidenceIngestionAndProfilingService {

    private final LearningEvidenceRepository evidenceRepo;
    private final QuestionSkillMappingRepository mappingRepo;
    private final LearnerSkillProfileRepository profileRepo;

    // Hằng số làm mượt Bayes (Mục 9.2)
    private static final double ALPHA = 2.0;
    private static final double BETA = 2.0;
    private static final String ALGO_VERSION = "bayes-v1.0";

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
            event.setBankVersion("2025.1");
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

        // 2. Đối chiếu mapping câu hỏi đã công bố (Quy tắc HS-05)
        Optional<QuestionSkillMapping> mappingOpt = mappingRepo
                .findByBankVersionAndPracticeQuestionIdAndStatus(
                        event.getBankVersion(), event.getPracticeQuestionId(), "PUBLISHED");

        if (mappingOpt.isEmpty()) {
            // Không dùng dữ liệu chưa đối soát để nâng mức chắc chắn hồ sơ
            return;
        }
        QuestionSkillMapping mapping = mappingOpt.get();

        // 3. Tính số lần user đã gặp câu hỏi này để giảm trọng số (Quy tắc HS-02)
        long previousAttempts = evidenceRepo.countByUserIdAndLicenseClassAndBankVersionAndPracticeQuestionId(
                event.getUserId(), event.getLicenseClass(), event.getBankVersion(), event.getPracticeQuestionId());
        int currentAttemptNo = (int) previousAttempts + 1;
        double weight = Math.pow(0.5, currentAttemptNo - 1); // Lần 1: 1.0, Lần 2: 0.5, Lần 3: 0.25

        // 4. Lưu bằng chứng học tập
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
                .outcome(event.getOutcome()) // CORRECT, INCORRECT, UNANSWERED
                .evidenceIdentityStatus("VERIFIED")
                .attemptNumberForQuestion(currentAttemptNo)
                .evidenceWeight(BigDecimal.valueOf(weight))
                .occurredAt(event.getOccurredAt())
                .build();
        evidenceRepo.save(evidence);

        // Quy tắc HS-04: UNANSWERED không đánh giá sai kiến thức -> dừng tại đây, không trừ điểm Bayes
        if ("UNANSWERED".equals(event.getOutcome())) {
            return;
        }

        // 5. Cập nhật Hồ sơ năng lực cho kỹ năng CHÍNH (PRIMARY - Quy tắc HS-03)
        recalculateSkillProfile(
                event.getUserId(),
                event.getLicenseClass(),
                event.getBankVersion(),
                mapping.getTaxonomyVersion(),
                mapping.getPrimarySkillCode(),
                "CORRECT".equals(event.getOutcome()),
                event.getOccurredAt()
        );
    }

    private void recalculateSkillProfile(UUID userId, String licenseClass, String bankVersion,
                                         String taxonomyVersion, String skillCode,
                                         boolean isLastAnswerCorrect, OffsetDateTime occurredAt) {
        // Lấy toàn bộ bằng chứng hợp lệ (đã trả lời) của user ở kỹ năng này
        List<LearningEvidence> validEvidences = evidenceRepo.findValidEvidencesBySkill(
                userId, licenseClass, bankVersion, taxonomyVersion, skillCode);

        double weightedCorrectSum = 0.0;
        double totalWeightSum = 0.0;
        Set<Integer> distinctQuestions = new HashSet<>();
        Set<UUID> distinctSessions = new HashSet<>();
        int correctCount = 0;

        for (LearningEvidence ev : validEvidences) {
            double w = ev.getEvidenceWeight().doubleValue();
            boolean correct = "CORRECT".equals(ev.getOutcome());
            if (correct) {
                weightedCorrectSum += w;
                correctCount++;
            }
            totalWeightSum += w;
            distinctQuestions.add(ev.getPracticeQuestionId());
            if (ev.getSessionId() != null) {
                distinctSessions.add(ev.getSessionId());
            }
        }

        // Công thức Bayes có làm mượt (Mục 9.2)
        double mHat = (ALPHA + weightedCorrectSum) / (ALPHA + BETA + totalWeightSum);

        // Tính độ tin cậy dựa trên độ đa dạng câu hỏi (tối thiểu 5 câu độc lập đạt độ tin cậy cao)
        int distinctQCount = distinctQuestions.size();
        double confidence = Math.min(1.0, distinctQCount / 5.0);

        // Phân loại trạng thái năng lực (Mục 5.1)
        String status;
        String reason;
        if (distinctQCount < 3) {
            status = "INSUFFICIENT_DATA";
            reason = String.format("Mới luyện %d câu độc lập (cần tối thiểu 3 câu để đánh giá).", distinctQCount);
        } else if (mHat < 0.60) {
            status = "NEEDS_REINFORCEMENT";
            reason = String.format("Tỷ lệ đúng có trọng số đạt %.0f%% qua %d câu khác nhau — cần ưu tiên củng cố.", mHat * 100, distinctQCount);
        } else if (mHat < 0.82 || distinctQCount < 5) {
            status = "IMPROVING";
            reason = String.format("Đang tiến bộ (điểm ước lượng %.0f%%), cần thêm câu hỏi mới để kiểm chứng độ ổn định.", mHat * 100);
        } else {
            status = "RELATIVELY_STABLE";
            reason = String.format("Nắm vững kiến thức (%.0f%% qua %d câu độc lập), chuyển sang ôn giãn cách.", mHat * 100, distinctQCount);
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
