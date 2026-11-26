package com.dts.dts_learning_intelligence.rag.service;

import com.dts.dts_learning_intelligence.rag.config.RAGProperties;
import com.dts.dts_learning_intelligence.rag.entity.UserRagUsage;
import com.dts.dts_learning_intelligence.rag.exception.DailyQuotaExceededException;
import com.dts.dts_learning_intelligence.rag.exception.RateLimitExceededException;
import com.dts.dts_learning_intelligence.rag.repository.UserRagUsageRepository;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class TokenQuotaGuardService {

    private final UserRagUsageRepository usageRepository;
    private final RAGProperties ragProperties;

    // Rate Limiting in-memory theo UserId bằng Bucket4j
    private final Map<UUID, Bucket> userBuckets = new ConcurrentHashMap<>();

    /**
     * 1. Kiểm tra Rate Limit: Tối đa số câu hỏi / phút (mặc định 5 câu/phút)
     */
    public void checkRateLimit(UUID userId) {
        int ratePerMinute = ragProperties.getTokenLimits().getRateLimitPerMinute();
        Bucket bucket = userBuckets.computeIfAbsent(userId, k -> {
            Bandwidth limit = Bandwidth.classic(ratePerMinute, Refill.intervally(ratePerMinute, Duration.ofMinutes(1)));
            return Bucket.builder().addLimit(limit).build();
        });

        if (!bucket.tryConsume(1)) {
            log.warn("User {} exceeded rate limit ({} requests/min)", userId, ratePerMinute);
            throw new RateLimitExceededException("Bạn đang gửi câu hỏi quá nhanh. Vui lòng đợi 1 phút trước khi hỏi tiếp.");
        }
    }

    /**
     * 2. Kiểm tra Daily Quota: Tối đa số câu hỏi / ngày (mặc định 30 câu/ngày)
     */
    @Transactional(readOnly = true)
    public UserRagUsage validateDailyQuota(UUID userId) {
        LocalDate today = LocalDate.now();
        int maxQueries = ragProperties.getTokenLimits().getDailyMaxQueries();

        UserRagUsage usage = usageRepository.findByUserIdAndUsageDate(userId, today)
                .orElse(null);

        if (usage != null && usage.getQueryCount() >= maxQueries) {
            log.warn("User {} exceeded daily RAG quota ({}/{} queries)", userId, usage.getQueryCount(), maxQueries);
            throw new DailyQuotaExceededException("Bạn đã dùng hết hạn mức " + maxQueries + " câu hỏi trợ lý AI trong ngày hôm nay. Vui lòng quay lại vào ngày mai!");
        }

        return usage;
    }

    /**
     * 3. Ghi nhận số token tiêu thụ thực tế và lượt hỏi sau khi gọi LLM thành công
     */
    @Transactional
    public void recordUsage(UUID userId, int tokensConsumed) {
        LocalDate today = LocalDate.now();
        UserRagUsage usage = usageRepository.findByUserIdAndUsageDate(userId, today)
                .orElseGet(() -> UserRagUsage.builder()
                        .userId(userId)
                        .usageDate(today)
                        .queryCount(0)
                        .totalTokensConsumed(0)
                        .lastRequestedAt(OffsetDateTime.now())
                        .build());

        usage.setQueryCount(usage.getQueryCount() + 1);
        usage.setTotalTokensConsumed(usage.getTotalTokensConsumed() + tokensConsumed);
        usage.setLastRequestedAt(OffsetDateTime.now());

        usageRepository.save(usage);
        log.info("Recorded RAG usage for user {}: queries={}, totalTokens={}", userId, usage.getQueryCount(), usage.getTotalTokensConsumed());
    }

    /**
     * Lấy số lượt hỏi còn lại trong ngày của học viên
     */
    public int getRemainingQueries(UUID userId) {
        LocalDate today = LocalDate.now();
        int maxQueries = ragProperties.getTokenLimits().getDailyMaxQueries();
        return usageRepository.findByUserIdAndUsageDate(userId, today)
                .map(u -> Math.max(0, maxQueries - u.getQueryCount()))
                .orElse(maxQueries);
    }
}
