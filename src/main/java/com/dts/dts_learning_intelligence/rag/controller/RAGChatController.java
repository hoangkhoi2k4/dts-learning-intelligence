package com.dts.dts_learning_intelligence.rag.controller;

import com.dts.dts_learning_intelligence.rag.dto.RAGChatRequestDto;
import com.dts.dts_learning_intelligence.rag.dto.RAGChatResponseDto;
import com.dts.dts_learning_intelligence.rag.service.RAGOrchestrationService;
import com.dts.dts_learning_intelligence.rag.service.TokenQuotaGuardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/learning-intelligence/v1/rag")
@RequiredArgsConstructor
public class RAGChatController {

    private final RAGOrchestrationService orchestrationService;
    private final TokenQuotaGuardService quotaGuardService;

    @PostMapping("/chat")
    public ResponseEntity<RAGChatResponseDto> chat(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody RAGChatRequestDto request) {
        RAGChatResponseDto response = orchestrationService.processChat(userId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/quota")
    public ResponseEntity<Map<String, Object>> getQuota(@RequestHeader("X-User-Id") UUID userId) {
        int remaining = quotaGuardService.getRemainingQueries(userId);
        return ResponseEntity.ok(Map.of(
                "userId", userId,
                "remainingDailyQueries", remaining,
                "maxDailyQueries", 30,
                "rateLimitPerMinute", 5
        ));
    }
}
