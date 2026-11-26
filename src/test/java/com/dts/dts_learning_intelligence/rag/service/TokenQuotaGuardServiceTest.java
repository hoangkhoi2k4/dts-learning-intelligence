package com.dts.dts_learning_intelligence.rag.service;

import com.dts.dts_learning_intelligence.rag.config.RAGProperties;
import com.dts.dts_learning_intelligence.rag.entity.UserRagUsage;
import com.dts.dts_learning_intelligence.rag.exception.DailyQuotaExceededException;
import com.dts.dts_learning_intelligence.rag.exception.RateLimitExceededException;
import com.dts.dts_learning_intelligence.rag.repository.UserRagUsageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TokenQuotaGuardServiceTest {

    @Mock
    private UserRagUsageRepository usageRepository;

    private RAGProperties ragProperties;
    private TokenQuotaGuardService tokenQuotaGuardService;

    @BeforeEach
    void setUp() {
        ragProperties = new RAGProperties();
        ragProperties.getTokenLimits().setRateLimitPerMinute(3); // Giả lập tối đa 3 câu/phút
        ragProperties.getTokenLimits().setDailyMaxQueries(10);   // Giả lập tối đa 10 câu/ngày

        tokenQuotaGuardService = new TokenQuotaGuardService(usageRepository, ragProperties);
    }

    @Test
    @DisplayName("Chặn khi gửi câu hỏi vượt quá tần suất Rate Limit (3 requests/min)")
    void testRateLimitEnforcement() {
        UUID userId = UUID.randomUUID();

        // 3 request đầu tiên được chấp nhận
        assertDoesNotThrow(() -> tokenQuotaGuardService.checkRateLimit(userId));
        assertDoesNotThrow(() -> tokenQuotaGuardService.checkRateLimit(userId));
        assertDoesNotThrow(() -> tokenQuotaGuardService.checkRateLimit(userId));

        // Request thứ 4 phải bị ném RateLimitExceededException
        assertThrows(RateLimitExceededException.class, () -> tokenQuotaGuardService.checkRateLimit(userId));
    }

    @Test
    @DisplayName("Chặn khi học viên đã dùng hết hạn ngạch ngày Daily Quota (>= 10 câu)")
    void testDailyQuotaExceeded() {
        UUID userId = UUID.randomUUID();
        UserRagUsage exhaustedUsage = UserRagUsage.builder()
                .userId(userId)
                .usageDate(LocalDate.now())
                .queryCount(10)
                .totalTokensConsumed(5000)
                .build();

        when(usageRepository.findByUserIdAndUsageDate(eq(userId), any(LocalDate.class)))
                .thenReturn(Optional.of(exhaustedUsage));

        assertThrows(DailyQuotaExceededException.class, () -> tokenQuotaGuardService.validateDailyQuota(userId));
    }

    @Test
    @DisplayName("Cộng dồn số token tiêu thụ chính xác sau khi LLM phản hồi")
    void testRecordUsage() {
        UUID userId = UUID.randomUUID();
        UserRagUsage usage = UserRagUsage.builder()
                .userId(userId)
                .usageDate(LocalDate.now())
                .queryCount(1)
                .totalTokensConsumed(300)
                .build();

        when(usageRepository.findByUserIdAndUsageDate(eq(userId), any(LocalDate.class)))
                .thenReturn(Optional.of(usage));

        tokenQuotaGuardService.recordUsage(userId, 450);

        assertEquals(2, usage.getQueryCount());
        assertEquals(750, usage.getTotalTokensConsumed());
        verify(usageRepository, times(1)).save(usage);
    }
}
