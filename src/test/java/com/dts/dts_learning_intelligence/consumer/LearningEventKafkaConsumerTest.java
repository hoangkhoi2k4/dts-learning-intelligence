package com.dts.dts_learning_intelligence.consumer;

import com.dts.dts_learning_intelligence.dto.LearningEventDto;
import com.dts.dts_learning_intelligence.service.EvidenceIngestionAndProfilingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LearningEventKafkaConsumerTest {

    @Mock
    private EvidenceIngestionAndProfilingService ingestionService;

    private ObjectMapper objectMapper = new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    private LearningEventKafkaConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new LearningEventKafkaConsumer(ingestionService, objectMapper);
    }

    @Test
    void testConsumeTheoryExamEvent() {
        String theoryPayload = """
        {
          "eventId": "d290f1ee-6c54-4b01-90e6-d701748f0851",
          "eventType": "EXAM_ATTEMPT_SUBMITTED",
          "schemaVersion": 1,
          "sourceService": "dts-practice",
          "occurredAt": "2026-11-26T14:30:00Z",
          "userId": "255a5bb6-d223-4934-a83b-985bf607fa5d",
          "licenseClass": "B2",
          "bankVersion": "dts-2026-600-v1",
          "activityType": "THEORY_EXAM",
          "sessionId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
          "items": [
            {
              "sourceOutcomeKey": "exam-a1b2c3d4-q-1",
              "practiceQuestionId": 1,
              "contentBuilderQuestionId": "c0e8338e-94b7-4a99-949f-8ac9f273bd92",
              "outcome": "CORRECT",
              "score": null,
              "firstResponseMs": null,
              "durationMs": null,
              "answeredAt": "2026-11-26T14:25:30Z"
            },
            {
              "sourceOutcomeKey": "exam-a1b2c3d4-q-25",
              "practiceQuestionId": 25,
              "contentBuilderQuestionId": "d3a86767-992c-44a8-93a9-6246b07b0753",
              "outcome": "INCORRECT",
              "score": null,
              "firstResponseMs": null,
              "durationMs": null,
              "answeredAt": "2026-11-26T14:26:10Z"
            }
          ]
        }
        """;

        consumer.onExamAttemptSubmitted(theoryPayload);

        ArgumentCaptor<LearningEventDto> captor = ArgumentCaptor.forClass(LearningEventDto.class);
        verify(ingestionService, times(2)).processLearningEvent(captor.capture());

        List<LearningEventDto> captured = captor.getAllValues();
        assertEquals("THEORY_EXAM", captured.get(0).getActivityType());
        assertEquals(1, captured.get(0).getPracticeQuestionId());
        assertEquals("CORRECT", captured.get(0).getOutcome());

        assertEquals(25, captured.get(1).getPracticeQuestionId());
        assertEquals("INCORRECT", captured.get(1).getOutcome());
    }

    @Test
    void testConsumeSimulationExamEvent() {
        String simExamPayload = """
        {
          "eventId": "e3a89012-7b12-4c91-9123-abcdef123456",
          "eventType": "EXAM_ATTEMPT_SUBMITTED",
          "schemaVersion": 1,
          "sourceService": "dts-simulation-service",
          "occurredAt": "2026-11-26T15:00:00Z",
          "userId": "255a5bb6-d223-4934-a83b-985bf607fa5d",
          "licenseClass": "B2",
          "bankVersion": "dts-2026-600-v1",
          "activityType": "SIMULATION",
          "sessionId": "b8a91c2d-3e4f-5678-90ab-cdef12345678",
          "items": [
            {
              "sourceOutcomeKey": "sim-exam-b8a91c2d-scenario-12",
              "scenarioNumber": 12,
              "scenarioId": null,
              "score": 5,
              "firstResponseMs": 2850,
              "durationMs": 29000,
              "outcome": "CORRECT",
              "answeredAt": "2026-11-26T14:52:10Z"
            },
            {
              "sourceOutcomeKey": "sim-exam-b8a91c2d-scenario-45",
              "scenarioNumber": 45,
              "scenarioId": null,
              "score": 0,
              "firstResponseMs": 4200,
              "durationMs": 35000,
              "outcome": "INCORRECT",
              "answeredAt": "2026-11-26T14:53:40Z"
            },
            {
              "sourceOutcomeKey": "sim-exam-b8a91c2d-scenario-78",
              "scenarioNumber": 78,
              "scenarioId": null,
              "score": null,
              "firstResponseMs": null,
              "durationMs": 32000,
              "outcome": "UNANSWERED",
              "answeredAt": "2026-11-26T14:55:00Z"
            }
          ]
        }
        """;

        consumer.onExamAttemptSubmitted(simExamPayload);

        ArgumentCaptor<LearningEventDto> captor = ArgumentCaptor.forClass(LearningEventDto.class);
        verify(ingestionService, times(3)).processLearningEvent(captor.capture());

        List<LearningEventDto> captured = captor.getAllValues();
        assertEquals("SIMULATION", captured.get(0).getActivityType());
        assertEquals(12, captured.get(0).getScenarioNumber());
        assertEquals(5, captured.get(0).getScore());
        assertEquals(2850, captured.get(0).getFirstResponseMs());
        assertEquals("CORRECT", captured.get(0).getOutcome());

        assertEquals(45, captured.get(1).getScenarioNumber());
        assertEquals(0, captured.get(1).getScore());
        assertEquals("INCORRECT", captured.get(1).getOutcome());

        assertEquals(78, captured.get(2).getScenarioNumber());
        assertNull(captured.get(2).getScore());
        assertEquals("UNANSWERED", captured.get(2).getOutcome());
    }

    @Test
    void testConsumeSimulationPracticeEvent() {
        String simPracticePayload = """
        {
          "eventId": "f7890123-8c23-4d02-a234-bcdef1234567",
          "eventType": "EXAM_ATTEMPT_SUBMITTED",
          "schemaVersion": 1,
          "sourceService": "dts-simulation-service",
          "occurredAt": "2026-11-26T15:05:00Z",
          "userId": "255a5bb6-d223-4934-a83b-985bf607fa5d",
          "licenseClass": "B2",
          "bankVersion": "dts-2026-600-v1",
          "activityType": "SIMULATION",
          "sessionId": "c9b02d3e-4f5a-6789-01bc-def123456789",
          "items": [
            {
              "sourceOutcomeKey": "sim-practice-255a5bb6-scenario-15-1764173100",
              "scenarioNumber": 15,
              "score": 4,
              "firstResponseMs": 3100,
              "durationMs": 30000,
              "outcome": "CORRECT",
              "answeredAt": "2026-11-26T15:05:00Z"
            }
          ]
        }
        """;

        consumer.onExamAttemptSubmitted(simPracticePayload);

        ArgumentCaptor<LearningEventDto> captor = ArgumentCaptor.forClass(LearningEventDto.class);
        verify(ingestionService, times(1)).processLearningEvent(captor.capture());

        LearningEventDto item = captor.getValue();
        assertEquals("SIMULATION", item.getActivityType());
        assertEquals(15, item.getScenarioNumber());
        assertEquals(4, item.getScore());
        assertEquals(3100, item.getFirstResponseMs());
        assertEquals("CORRECT", item.getOutcome());
    }
}
