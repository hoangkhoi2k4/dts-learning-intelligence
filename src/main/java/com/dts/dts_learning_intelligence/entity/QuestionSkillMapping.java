package com.dts.dts_learning_intelligence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "question_skill_mapping",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_mapping_bank_cb_id", columnNames = {"bank_version", "taxonomy_version", "content_builder_question_id"}),
                @UniqueConstraint(name = "uq_mapping_bank_practice_id", columnNames = {"bank_version", "taxonomy_version", "practice_question_id"})
        },
        indexes = {
                @Index(name = "idx_mapping_filter", columnList = "bank_version, taxonomy_version, status, primary_skill_code")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuestionSkillMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "bank_version", length = 64, nullable = false)
    private String bankVersion;

    @Column(name = "taxonomy_version", length = 64, nullable = false)
    private String taxonomyVersion;

    @Column(name = "content_builder_question_id", nullable = false)
    private UUID contentBuilderQuestionId;

    @Column(name = "practice_question_id", nullable = false)
    private Integer practiceQuestionId;

    @Column(name = "source_question_id", nullable = false)
    private Integer sourceQuestionId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "applicable_licenses", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<String> applicableLicenses = new ArrayList<>();

    @Builder.Default
    @Column(name = "is_critical", nullable = false)
    private Boolean isCritical = false;

    @Column(name = "primary_skill_code", length = 64, nullable = false)
    private String primarySkillCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "secondary_skill_codes", columnDefinition = "jsonb")
    @Builder.Default
    private List<String> secondarySkillCodes = new ArrayList<>();

    @Builder.Default
    @Column(name = "status", length = 20, nullable = false)
    private String status = "PUBLISHED";

    @Column(name = "checksum", length = 64)
    private String checksum;

    @CreationTimestamp
    @Column(name = "published_at", nullable = false, updatable = false)
    private OffsetDateTime publishedAt;
}
