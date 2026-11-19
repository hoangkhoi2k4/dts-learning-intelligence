package com.dts.dts_learning_intelligence.service;

import com.dts.dts_learning_intelligence.entity.LearnerSkillProfile;
import com.dts.dts_learning_intelligence.entity.QuestionSkillMapping;
import com.dts.dts_learning_intelligence.repository.LearnerSkillProfileRepository;
import com.dts.dts_learning_intelligence.repository.LearningEvidenceRepository;
import com.dts.dts_learning_intelligence.repository.QuestionSkillMappingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdaptiveRecommendationService {

    private final QuestionSkillMappingRepository mappingRepo;
    private final LearnerSkillProfileRepository profileRepo;
    private final LearningEvidenceRepository evidenceRepo;

    // Trọng số cấu hình (Mục 9.3)
    private static final double LAMBDA_WEAKNESS = 0.35;   // W: Kỹ năng yếu
    private static final double LAMBDA_DUE = 0.25;        // D: Đến hạn ôn
    private static final double LAMBDA_UNSEEN = 0.25;     // U: Câu chưa gặp
    private static final double LAMBDA_IMPORTANCE = 0.15; // I: Câu điểm liệt

    public List<RecommendedQuestionItem> selectTopQuestions(UUID userId, String licenseClass,
                                                            String bankVersion, String taxonomyVersion,
                                                            int targetCount) {
        // 1. Lọc cứng các câu hỏi hợp lệ: PUBLISHED + đúng bankVersion + đúng hạng bằng lái (Quy tắc ON-01)
        List<QuestionSkillMapping> eligibleQuestions = mappingRepo
                .findPublishedByBankAndLicense(bankVersion, taxonomyVersion, licenseClass);

        // Lấy hồ sơ năng lực hiện tại của user
        Map<String, LearnerSkillProfile> profileMap = profileRepo
                .findByUserIdAndLicenseClassAndBankVersionAndTaxonomyVersion(
                        userId, licenseClass, bankVersion, taxonomyVersion)
                .stream()
                .collect(Collectors.toMap(LearnerSkillProfile::getSkillCode, Function.identity()));

        // Lấy tập các câu hỏi user đã từng làm và thời điểm làm gần nhất
        Map<Integer, OffsetDateTime> lastAttemptedMap = new HashMap<>();
        List<Object[]> rawAttempts = evidenceRepo.findRawLastAttemptedTimePerQuestion(userId, licenseClass, bankVersion);
        for (Object[] row : rawAttempts) {
            if (row[0] != null && row[1] != null) {
                Integer qId = ((Number) row[0]).intValue();
                OffsetDateTime odt;
                if (row[1] instanceof OffsetDateTime o) {
                    odt = o;
                } else if (row[1] instanceof Timestamp ts) {
                    odt = ts.toInstant().atOffset(OffsetDateTime.now().getOffset());
                } else {
                    odt = OffsetDateTime.parse(row[1].toString());
                }
                lastAttemptedMap.put(qId, odt);
            }
        }

        OffsetDateTime now = OffsetDateTime.now();
        List<ScoredQuestion> scoredList = new ArrayList<>();

        for (QuestionSkillMapping q : eligibleQuestions) {
            LearnerSkillProfile profile = profileMap.get(q.getPrimarySkillCode());

            // W(q): Nhu cầu củng cố (0.0 -> 1.0)
            double mHat = (profile != null) ? profile.getMasteryScore().doubleValue() : 0.5;
            double wScore = 1.0 - mHat;

            // D(q): Mức đến hạn ôn (0.0 hoặc 1.0)
            double dScore = (profile != null && profile.getNextReviewAt() != null && !profile.getNextReviewAt().isAfter(now))
                    ? 1.0 : 0.0;

            // U(q): Thiếu bao phủ (1.0 nếu chưa gặp bao giờ, 0.3 nếu đã gặp lâu, 0.0 nếu vừa làm)
            OffsetDateTime lastDone = lastAttemptedMap.get(q.getPracticeQuestionId());
            double uScore;
            if (lastDone == null) {
                uScore = 1.0; // Ưu tiên câu chưa gặp để kiểm chứng vận dụng (Mục 1.1)
            } else if (Duration.between(lastDone, now).toHours() > 24) {
                uScore = 0.3;
            } else {
                uScore = 0.0; // Tránh lặp lại câu vừa làm trong 24h (Mục 6.1)
            }

            // I(q): Câu điểm liệt quan trọng
            double iScore = Boolean.TRUE.equals(q.getIsCritical()) ? 1.0 : 0.0;

            double basePriority = (LAMBDA_WEAKNESS * wScore)
                                + (LAMBDA_DUE * dScore)
                                + (LAMBDA_UNSEEN * uScore)
                                + (LAMBDA_IMPORTANCE * iScore);

            String reason = buildQuestionReason(profile, lastDone == null, dScore > 0, q.getIsCritical());
            scoredList.add(new ScoredQuestion(q, basePriority, reason));
        }

        // 2. Chọn câu theo vòng lặp có phạt đa dạng V(q) để không bị dồn 100% vào 1 kỹ năng
        List<RecommendedQuestionItem> selected = new ArrayList<>();
        Map<String, Integer> skillPickedCount = new HashMap<>();

        while (selected.size() < targetCount && !scoredList.isEmpty()) {
            // Tìm câu có điểm cao nhất sau khi trừ hệ số đa dạng V(q)
            ScoredQuestion best = null;
            double bestFinalScore = -1.0;

            for (ScoredQuestion item : scoredList) {
                int alreadyPicked = skillPickedCount.getOrDefault(item.mapping.getPrimarySkillCode(), 0);
                double diversityPenalty = alreadyPicked * 0.08; // V(q): Giảm nhẹ nếu kỹ năng này đã được chọn nhiều
                double finalScore = item.baseScore - diversityPenalty;

                if (finalScore > bestFinalScore) {
                    bestFinalScore = finalScore;
                    best = item;
                }
            }

            if (best == null) break;
            scoredList.remove(best);

            String skill = best.mapping.getPrimarySkillCode();
            skillPickedCount.put(skill, skillPickedCount.getOrDefault(skill, 0) + 1);

            selected.add(new RecommendedQuestionItem(
                    best.mapping.getPracticeQuestionId(),
                    best.mapping.getContentBuilderQuestionId(),
                    skill,
                    bestFinalScore,
                    best.reason
            ));
        }

        return selected;
    }

    private String buildQuestionReason(LearnerSkillProfile profile, boolean isUnseen, boolean isDue, Boolean isCritical) {
        if (profile == null || "INSUFFICIENT_DATA".equals(profile.getProficiencyStatus())) {
            return "Câu hỏi khám phá giúp hệ thống đánh giá năng lực nhóm kỹ năng mới.";
        }
        if ("NEEDS_REINFORCEMENT".equals(profile.getProficiencyStatus()) && isUnseen) {
            return "Câu hỏi mới cùng kỹ năng đang yếu để kiểm chứng khả năng vận dụng thực tế.";
        }
        if (isDue) {
            return "Nội dung đến hạn ôn tập chống quên theo lịch giãn cách.";
        }
        if (Boolean.TRUE.equals(isCritical)) {
            return "Câu điểm liệt quan trọng cần duy trì độ chính xác tuyệt đối.";
        }
        return "Câu hỏi tổng hợp giúp duy trì sự đa dạng kiến thức.";
    }

    private record ScoredQuestion(QuestionSkillMapping mapping, double baseScore, String reason) {}
    public record RecommendedQuestionItem(Integer practiceQuestionId, UUID contentBuilderQuestionId,
                                          String skillCode, double priorityScore, String reason) {}
}
