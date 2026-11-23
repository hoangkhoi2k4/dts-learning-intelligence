package com.dts.dts_learning_intelligence.service;

import com.dts.dts_learning_intelligence.client.PracticeSessionClient;
import com.dts.dts_learning_intelligence.entity.AdaptiveSessionSnapshot;
import com.dts.dts_learning_intelligence.entity.StudyPlan;
import com.dts.dts_learning_intelligence.repository.AdaptiveSessionSnapshotRepository;
import com.dts.dts_learning_intelligence.repository.StudyPlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StudyPlanOrchestrationService {

    private final AdaptiveRecommendationService recommendationService;
    private final StudyPlanRepository planRepo;
    private final AdaptiveSessionSnapshotRepository snapshotRepo;
    private final PracticeSessionClient practiceClient;

    private static final String DEFAULT_BANK_VERSION = "dts-2026-600-v1";
    private static final String DEFAULT_TAXONOMY_VERSION = "tax-v1.0";

    // Tạo đề xuất kế hoạch học (Bước 3 trong luồng nghiệp vụ 4.1)
    @Transactional
    public StudyPlanResponse createPlan(UUID userId, String licenseClass, int targetCount) {
        List<AdaptiveRecommendationService.RecommendedQuestionItem> items = recommendationService
                .selectTopQuestions(userId, licenseClass, DEFAULT_BANK_VERSION, DEFAULT_TAXONOMY_VERSION, targetCount);

        if (items.isEmpty()) {
            throw new IllegalStateException("Không đủ nguồn câu hỏi hợp lệ cho hạng " + licenseClass);
        }

        List<String> focusSkills = items.stream()
                .map(AdaptiveRecommendationService.RecommendedQuestionItem::skillCode)
                .distinct()
                .toList();

        StudyPlan plan = StudyPlan.builder()
                .userId(userId)
                .licenseClass(licenseClass)
                .bankVersion(DEFAULT_BANK_VERSION)
                .taxonomyVersion(DEFAULT_TAXONOMY_VERSION)
                .targetQuestionCount(items.size())
                .estimatedMinutes(items.size()) // Ước tính 1 phút/câu
                .focusSkillCodes(focusSkills)
                .recommendationReason("Đề xuất ưu tiên các kỹ năng cần củng cố và nội dung đến hạn ôn.")
                .status("PROPOSED")
                .algorithmVersion("hybrid-rank-v1.0")
                .build();
        plan = planRepo.save(plan);

        return new StudyPlanResponse(plan.getId(), plan.getStatus(), plan.getTargetQuestionCount(),
                plan.getEstimatedMinutes(), focusSkills, plan.getRecommendationReason(), items);
    }

    // Bắt đầu phiên luyện tập thực tế (Bước 4 - Khóa snapshot + Idempotency)
    @Transactional
    public StartSessionResponse startPlanSession(UUID userId, UUID planId, String idempotencyKey) {
        String effectiveKey = (idempotencyKey != null && !idempotencyKey.isBlank())
                ? idempotencyKey : "plan-start:" + planId;

        // 1. Kiểm tra chống trùng: Nếu đã tạo phiên trước đó -> trả về đúng phiên cũ (Quy tắc ON-04)
        Optional<AdaptiveSessionSnapshot> existing = snapshotRepo.findByIdempotencyKey(effectiveKey);
        if (existing.isPresent()) {
            AdaptiveSessionSnapshot snap = existing.get();
            return new StartSessionResponse(snap.getPracticeSessionId(), snap.getStatus(), true, snap.getQuestionSnapshot());
        }

        StudyPlan plan = planRepo.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy kế hoạch học: " + planId));

        // 2. Chọn danh sách câu hỏi và khóa cứng thành snapshot
        var items = recommendationService.selectTopQuestions(
                userId, plan.getLicenseClass(), plan.getBankVersion(), plan.getTaxonomyVersion(), plan.getTargetQuestionCount());
        List<Integer> questionIds = items.stream()
                .map(AdaptiveRecommendationService.RecommendedQuestionItem::practiceQuestionId)
                .toList();

        // 3. Gọi sang dts-practice (Mock hoặc Real) để tạo phiên thi thực tế
        var practiceRes = practiceClient.createAdaptiveSession(
                effectiveKey,
                new PracticeSessionClient.CreateAdaptiveSessionRequest(
                        userId, plan.getLicenseClass(), plan.getBankVersion(), plan.getId(), questionIds
                )
        );

        // 4. Lưu snapshot bất biến & đổi trạng thái kế hoạch
        AdaptiveSessionSnapshot snapshot = AdaptiveSessionSnapshot.builder()
                .studyPlanId(plan.getId())
                .userId(userId)
                .idempotencyKey(effectiveKey)
                .practiceSessionId(practiceRes.sessionId())
                .licenseClass(plan.getLicenseClass())
                .bankVersion(plan.getBankVersion())
                .questionSnapshot(items)
                .status("STARTED")
                .build();
        snapshotRepo.save(snapshot);

        plan.setStatus("IN_PROGRESS");
        planRepo.save(plan);

        return new StartSessionResponse(practiceRes.sessionId(), "STARTED", false, items);
    }

    @Transactional
    public StudyPlanSkipResponse skipPlan(UUID userId, UUID planId) {
        StudyPlan plan = planRepo.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy kế hoạch học: " + planId));

        // Bảo mật: Đảm bảo user chỉ được bỏ qua kế hoạch của chính mình (Mục 10.3)
        if (!plan.getUserId().equals(userId)) {
                throw new SecurityException("Bạn không có quyền thao tác trên kế hoạch này.");
        }

        // Chỉ cho phép bỏ qua khi kế hoạch đang được đề xuất hoặc đang làm dở
        if ("COMPLETED".equals(plan.getStatus())) {
                throw new IllegalStateException("Kế hoạch đã hoàn thành, không thể bỏ qua.");
        }
        if ("SKIPPED".equals(plan.getStatus())) {
                return new StudyPlanSkipResponse(plan.getId(), plan.getStatus(), "Kế hoạch đã được bỏ qua trước đó.");
        }

        plan.setStatus("SKIPPED");
        planRepo.save(plan);

        // Nếu đã lỡ tạo phiên snapshot (đang IN_PROGRESS) thì đánh dấu ABANDONED để dọn dẹp
        snapshotRepo.findByStudyPlanId(planId).ifPresent(snapshot -> {
                if ("STARTED".equals(snapshot.getStatus())) {
                snapshot.setStatus("ABANDONED");
                snapshotRepo.save(snapshot);
                }
        });

        // Quy tắc NT-11: Không cộng lỗi sai, không cập nhật giảm điểm LearnerSkillProfile
        return new StudyPlanSkipResponse(
                plan.getId(),
                "SKIPPED",
                "Đã bỏ qua kế hoạch học. Hệ thống không tính lỗi sai và sẽ đề xuất lộ trình mới phù hợp hơn."
        );
        }

    public record StudyPlanSkipResponse(UUID planId, String status, String message) {}

    public record StudyPlanResponse(UUID planId, String status, int totalQuestions, int estimatedMinutes,
                                    List<String> focusSkills, String reason,
                                    List<AdaptiveRecommendationService.RecommendedQuestionItem> questions) {}

    public record StartSessionResponse(UUID practiceSessionId, String status, boolean reusedExistingSession,
                                       Object questionSnapshot) {}
}
