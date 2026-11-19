package com.dts.dts_learning_intelligence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "learner_skill_profile",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_learner_skill_profile", columnNames = {"user_id", "license_class", "bank_version", "taxonomy_version", "skill_code"})
        },
        indexes = {
                @Index(name = "idx_profile_user_due", columnList = "user_id, license_class, next_review_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LearnerSkillProfile {

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

    @Column(name = "skill_code", length = 64, nullable = false)
    private String skillCode;

    // Các chỉ số thống kê Bayes
    @Builder.Default
    @Column(name = "mastery_score", precision = 5, scale = 4, nullable = false)
    private BigDecimal masteryScore = new BigDecimal("0.5000");

    @Builder.Default
    @Column(name = "distinct_questions_count", nullable = false)
    private Integer distinctQuestionsCount = 0;

    @Builder.Default
    @Column(name = "distinct_sessions_count", nullable = false)
    private Integer distinctSessionsCount = 0;

    @Builder.Default
    @Column(name = "total_attempts_count", nullable = false)
    private Integer totalAttemptsCount = 0;

    @Builder.Default
    @Column(name = "correct_attempts_count", nullable = false)
    private Integer correctAttemptsCount = 0;

    // Trạng thái phân loại & Độ tin cậy
    @Builder.Default
    @Column(name = "proficiency_status", length = 32, nullable = false)
    private String proficiencyStatus = "INSUFFICIENT_DATA";

    @Builder.Default
    @Column(name = "confidence_score", precision = 5, scale = 4, nullable = false)
    private BigDecimal confidenceScore = new BigDecimal("0.0000");

    // Lịch ôn chống quên (Spaced Repetition)
    @Builder.Default
    @Column(name = "review_interval_days", nullable = false)
    private Integer reviewIntervalDays = 1;

    @Builder.Default
    @Column(name = "consecutive_correct_reviews", nullable = false)
    private Integer consecutiveCorrectReviews = 0;

    @Column(name = "last_practiced_at")
    private OffsetDateTime lastPracticedAt;

    @Column(name = "next_review_at")
    private OffsetDateTime nextReviewAt;

    // Lưu vết giải thích
    @Builder.Default
    @Column(name = "algorithm_version", length = 32, nullable = false)
    private String algorithmVersion = "bayes-v1.0";

    @Column(name = "explanation_reason", columnDefinition = "TEXT")
    private String explanationReason;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
