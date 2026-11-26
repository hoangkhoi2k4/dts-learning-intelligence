package com.dts.dts_learning_intelligence.rag.controller;

import com.dts.dts_learning_intelligence.rag.dto.RAGChatRequestDto;
import com.dts.dts_learning_intelligence.rag.dto.RAGChatResponseDto;
import com.dts.dts_learning_intelligence.rag.service.RAGOrchestrationService;
import com.dts.dts_learning_intelligence.rag.service.TokenQuotaGuardService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RAGChatControllerTest {

    @Mock
    private RAGOrchestrationService orchestrationService;

    @Mock
    private TokenQuotaGuardService quotaGuardService;

    @InjectMocks
    private RAGChatController controller;

    @Test
    @DisplayName("Gửi câu hỏi hợp lệ trả về kết quả 200 OK kèm trích dẫn và quota")
    void testChatSuccessful() {
        UUID userId = UUID.randomUUID();
        RAGChatRequestDto request = RAGChatRequestDto.builder()
                .message("Quy tắc vượt xe thế nào?")
                .contextType("THEORY_QUESTION")
                .contextRefId("15")
                .licenseClass("B")
                .build();

        RAGChatResponseDto mockResponse = RAGChatResponseDto.builder()
                .answer("Vượt xe phải có tín hiệu báo hiệu...")
                .sources(List.of())
                .contextUsed("THEORY_QUESTION")
                .remainingDailyQueries(29)
                .isFallback(false)
                .suggestedActions(List.of("Quay lại câu hỏi đang luyện"))
                .build();

        when(orchestrationService.processChat(eq(userId), any(RAGChatRequestDto.class)))
                .thenReturn(mockResponse);

        ResponseEntity<RAGChatResponseDto> responseEntity = controller.chat(userId, request);

        assertNotNull(responseEntity);
        assertEquals(200, responseEntity.getStatusCode().value());
        assertNotNull(responseEntity.getBody());
        assertEquals("Vượt xe phải có tín hiệu báo hiệu...", responseEntity.getBody().getAnswer());
        assertEquals(29, responseEntity.getBody().getRemainingDailyQueries());
    }

    @Test
    @DisplayName("Kiểm tra endpoint tra cứu hạn ngạch quota của học viên")
    void testGetQuota() {
        UUID userId = UUID.randomUUID();
        when(quotaGuardService.getRemainingQueries(userId)).thenReturn(25);

        ResponseEntity<Map<String, Object>> responseEntity = controller.getQuota(userId);

        assertNotNull(responseEntity);
        assertEquals(200, responseEntity.getStatusCode().value());
        assertEquals(25, responseEntity.getBody().get("remainingDailyQueries"));
        assertEquals(30, responseEntity.getBody().get("maxDailyQueries"));
    }
}
