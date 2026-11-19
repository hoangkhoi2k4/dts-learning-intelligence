package com.dts.dts_learning_intelligence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "learning_evidence",
        uniqueConstraints = {
                @UniqueConstraint(name = "learning_evidence_event_id_key", columnNames = {"event_id"}),
                @UniqueConstraint(name = "learning_evidence_source_outcome_key_key", columnNames = {"source_outcome_key"})
        },
        indexes = {
                @Index(name = "idx_evidence_user_scope", columnList = "user_id, license_class, bank_version, occurred_at"),
                @Index(name = "idx_evidence_user_question", columnList = "user_id, license_class, bank_version, content_builder_question_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LearningEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Column(name = "source_outcome_key", length = 255, nullable = false, unique = true)
    private String sourceOutcomeKey;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "license_class", length = 16, nullable = false)
    private String licenseClass;

    @Column(name = "bank_version", length = 64, nullable = false)
    private String bankVersion;

    @Column(name = "activity_type", length = 32, nullable = false)
    private String activityType;

    @Column(name = "session_id")
    private UUID sessionId;

    @Column(name = "practice_question_id", nullable = false)
    private Integer practiceQuestionId;

    @Column(name = "content_builder_question_id")
    private UUID contentBuilderQuestionId;

    @Column(name = "outcome", length = 20, nullable = false)
    private String outcome;

    @Builder.Default
    @Column(name = "evidence_identity_status", length = 20, nullable = false)
    private String evidenceIdentityStatus = "VERIFIED";

    @Builder.Default
    @Column(name = "attempt_number_for_question", nullable = false)
    private Integer attemptNumberForQuestion = 1;

    @Builder.Default
    @Column(name = "evidence_weight", precision = 5, scale = 4, nullable = false)
    private BigDecimal evidenceWeight = new BigDecimal("1.0000");

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    @CreationTimestamp
    @Column(name = "recorded_at", nullable = false, updatable = false)
    private OffsetDateTime recordedAt;
}
