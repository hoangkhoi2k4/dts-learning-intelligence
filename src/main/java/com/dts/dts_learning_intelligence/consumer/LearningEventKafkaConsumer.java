package com.dts.dts_learning_intelligence.consumer;

import com.dts.dts_learning_intelligence.dto.ExamAttemptSubmittedEventDto;
import com.dts.dts_learning_intelligence.dto.LearningEventDto;
import com.dts.dts_learning_intelligence.service.EvidenceIngestionAndProfilingService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Slf4j
@Component
@ConditionalOnProperty(name = "dts.kafka.enabled", havingValue = "true")
public class LearningEventKafkaConsumer {

    private final EvidenceIngestionAndProfilingService ingestionService;
    private final ObjectMapper objectMapper;

    public LearningEventKafkaConsumer(EvidenceIngestionAndProfilingService ingestionService, ObjectMapper objectMapper) {
        this.ingestionService = ingestionService;
        this.objectMapper = objectMapper != null ? objectMapper.copy().findAndRegisterModules() : new ObjectMapper().findAndRegisterModules();
    }

    @KafkaListener(
            topics = "${dts.kafka.topics.learning-progress}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onExamAttemptSubmitted(String rawMessage) {
        try {
            ExamAttemptSubmittedEventDto batchEvent =
                    objectMapper.readValue(rawMessage, ExamAttemptSubmittedEventDto.class);

            if (batchEvent.getItems() == null || batchEvent.getItems().isEmpty()) {
                log.warn("Nhận event {} nhưng danh sách items rỗng, bỏ qua.", batchEvent.getEventId());
                return;
            }

            log.info("Đang xử lý batch event {} (sessionId={}) gồm {} câu hỏi",
                    batchEvent.getEventId(), batchEvent.getSessionId(), batchEvent.getItems().size());

            for (ExamAttemptSubmittedEventDto.ItemDto item : batchEvent.getItems()) {
                // Tạo eventId riêng cho từng câu dựa trên sourceOutcomeKey để đảm bảo unique trong bảng learning_evidence
                UUID itemEventId = UUID.nameUUIDFromBytes(item.getSourceOutcomeKey().getBytes(StandardCharsets.UTF_8));

                java.time.Instant occurredInstant = item.getAnsweredAt() != null ? item.getAnsweredAt() : batchEvent.getOccurredAt();
                java.time.OffsetDateTime occurredAt = occurredInstant != null ? occurredInstant.atOffset(java.time.ZoneOffset.UTC) : null;

                LearningEventDto singleEvent = LearningEventDto.builder()
                        .eventId(itemEventId)
                        .sourceOutcomeKey(item.getSourceOutcomeKey())
                        .userId(batchEvent.getUserId())
                        .licenseClass(batchEvent.getLicenseClass())
                        .bankVersion(batchEvent.getBankVersion())
                        .activityType(batchEvent.getActivityType())
                        .sessionId(batchEvent.getSessionId())
                        .practiceQuestionId(item.getPracticeQuestionId())
                        .contentBuilderQuestionId(item.getContentBuilderQuestionId())
                        .scenarioId(item.getScenarioId())
                        .scenarioNumber(item.getScenarioNumber())
                        .score(item.getScore())
                        .firstResponseMs(item.getFirstResponseMs())
                        .durationMs(item.getDurationMs())
                        .outcome(item.getOutcome())
                        .occurredAt(occurredAt)
                        .build();

                ingestionService.processLearningEvent(singleEvent);
            }
        } catch (JsonProcessingException e) {
            log.error("Lỗi parse JSON batch event từ Kafka: {}", rawMessage, e);
        } catch (Exception e) {
            log.error("Lỗi xử lý batch event, Kafka sẽ retry: {}", e.getMessage(), e);
            throw e;
        }
    }
}