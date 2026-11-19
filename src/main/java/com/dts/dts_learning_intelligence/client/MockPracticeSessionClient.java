package com.dts.dts_learning_intelligence.client;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@ConditionalOnProperty(name = "dts.practice.mock-enabled", havingValue = "true", matchIfMissing = true)
public class MockPracticeSessionClient implements PracticeSessionClient {

    @Override
    public PracticeSessionResponse createAdaptiveSession(String idempotencyKey, CreateAdaptiveSessionRequest request) {
        return new PracticeSessionResponse(
                UUID.randomUUID(),
                "IN_PROGRESS",
                request.questionIds() != null ? request.questionIds().size() : 0,
                false
        );
    }
}
