package com.dts.dts_learning_intelligence.client;

import java.util.List;
import java.util.UUID;

public interface PracticeSessionClient {

    PracticeSessionResponse createAdaptiveSession(String idempotencyKey, CreateAdaptiveSessionRequest request);

    record CreateAdaptiveSessionRequest(
            UUID userId,
            String licenseClass,
            String bankVersion,
            UUID studyPlanId,
            List<Integer> questionIds
    ) {}

    record PracticeSessionResponse(
            UUID sessionId,
            String status,
            int totalQuestions,
            boolean isExisting
    ) {}
}
