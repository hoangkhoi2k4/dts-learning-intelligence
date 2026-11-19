package com.dts.dts_learning_intelligence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "study_plan",
        indexes = {
                @Index(name = "idx_study_plan_user", columnList = "user_id, license_class, status, created_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudyPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "license_class", length = 16, nullable = false)
    private String licenseClass;

    @Column(name = "bank_version", length = 64, nullable = false)
    private String bankVersion;

    @Column(name = "taxonomy_version", length = 64, nullable = false)
    private String taxonomyVersion;

    @Builder.Default
    @Column(name = "target_question_count", nullable = false)
    private Integer targetQuestionCount = 10;

    @Builder.Default
    @Column(name = "estimated_minutes", nullable = false)
    private Integer estimatedMinutes = 10;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "focus_skill_codes", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<String> focusSkillCodes = new ArrayList<>();

    @Column(name = "recommendation_reason", columnDefinition = "TEXT", nullable = false)
    private String recommendationReason;

    @Builder.Default
    @Column(name = "status", length = 24, nullable = false)
    private String status = "PROPOSED";

    @Builder.Default
    @Column(name = "algorithm_version", length = 32, nullable = false)
    private String algorithmVersion = "hybrid-rank-v1.0";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
