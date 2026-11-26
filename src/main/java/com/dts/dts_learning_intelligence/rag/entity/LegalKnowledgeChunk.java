package com.dts.dts_learning_intelligence.rag.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "legal_knowledge_chunk",
        indexes = {
                @Index(name = "idx_legal_doc_num", columnList = "document_number"),
                @Index(name = "idx_legal_validity", columnList = "validity_status")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LegalKnowledgeChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "document_title", length = 500, nullable = false)
    private String documentTitle;

    @Column(name = "document_number", length = 100)
    private String documentNumber;

    @Column(name = "chapter", length = 100)
    private String chapter;

    @Column(name = "article", length = 50)
    private String article;

    @Column(name = "clause", length = 50)
    private String clause;

    @Column(name = "point", length = 50)
    private String point;

    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Object metadata;

    @Column(name = "embedding", columnDefinition = "text")
    private String embedding;

    @Builder.Default
    @Column(name = "validity_status", length = 50, nullable = false)
    private String validityStatus = "VALID";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
