package com.dts.dts_learning_intelligence.rag.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "user_rag_usage",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_user_rag_usage", columnNames = {"user_id", "usage_date"})
        },
        indexes = {
                @Index(name = "idx_user_rag_usage_date", columnList = "user_id, usage_date")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserRagUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "usage_date", nullable = false)
    private LocalDate usageDate;

    @Builder.Default
    @Column(name = "query_count", nullable = false)
    private Integer queryCount = 0;

    @Builder.Default
    @Column(name = "total_tokens_consumed", nullable = false)
    private Integer totalTokensConsumed = 0;

    @Builder.Default
    @Column(name = "last_requested_at", nullable = false)
    private OffsetDateTime lastRequestedAt = OffsetDateTime.now();
}
