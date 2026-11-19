package com.dts.dts_learning_intelligence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "adaptive_session_snapshot",
        uniqueConstraints = {
                @UniqueConstraint(name = "adaptive_session_snapshot_study_plan_id_key", columnNames = {"study_plan_id"}),
                @UniqueConstraint(name = "adaptive_session_snapshot_idempotency_key_key", columnNames = {"idempotency_key"}),
                @UniqueConstraint(name = "adaptive_session_snapshot_practice_session_id_key", columnNames = {"practice_session_id"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdaptiveSessionSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "study_plan_id", nullable = false, unique = true)
    private UUID studyPlanId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "idempotency_key", length = 128, nullable = false, unique = true)
    private String idempotencyKey;

    @Column(name = "practice_session_id", nullable = false, unique = true)
    private UUID practiceSessionId;

    @Column(name = "license_class", length = 16, nullable = false)
    private String licenseClass;

    @Column(name = "bank_version", length = 64, nullable = false)
    private String bankVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "question_snapshot", columnDefinition = "jsonb", nullable = false)
    private Object questionSnapshot;

    @Builder.Default
    @Column(name = "status", length = 20, nullable = false)
    private String status = "STARTED";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;
}
