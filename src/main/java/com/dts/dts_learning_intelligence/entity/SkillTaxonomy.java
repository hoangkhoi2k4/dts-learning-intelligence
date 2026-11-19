package com.dts.dts_learning_intelligence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "skill_taxonomy",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_taxonomy_version_skill", columnNames = {"taxonomy_version", "skill_code"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SkillTaxonomy {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "taxonomy_version", length = 64, nullable = false)
    private String taxonomyVersion;

    @Column(name = "skill_code", length = 64, nullable = false)
    private String skillCode;

    @Column(name = "skill_name", length = 255, nullable = false)
    private String skillName;

    @Column(name = "chapter_id", nullable = false)
    private Integer chapterId;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Builder.Default
    @Column(name = "status", length = 20, nullable = false)
    private String status = "PUBLISHED";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
