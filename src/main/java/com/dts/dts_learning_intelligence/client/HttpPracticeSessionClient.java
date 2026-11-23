package com.dts.dts_learning_intelligence.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
@ConditionalOnProperty(name = "dts.practice.mock-enabled", havingValue = "false")
public class HttpPracticeSessionClient implements PracticeSessionClient {

    private final RestClient restClient;

    public HttpPracticeSessionClient(@Value("${dts.practice.base-url}") String practiceBaseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(practiceBaseUrl)
                .build();
    }

    @Override
    public PracticeSessionResponse createAdaptiveSession(String idempotencyKey, CreateAdaptiveSessionRequest request) {
        log.info("Gọi sang dts-practice tạo phiên ADAPTIVE với idempotencyKey={}", idempotencyKey);
        return restClient.post()
                .uri("/internal/v1/adaptive-sessions")
                .header("X-Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(PracticeSessionResponse.class);
    }
}
