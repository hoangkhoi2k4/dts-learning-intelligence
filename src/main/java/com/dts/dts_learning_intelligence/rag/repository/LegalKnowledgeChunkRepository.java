package com.dts.dts_learning_intelligence.rag.repository;

import com.dts.dts_learning_intelligence.rag.entity.LegalKnowledgeChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LegalKnowledgeChunkRepository extends JpaRepository<LegalKnowledgeChunk, UUID> {

    List<LegalKnowledgeChunk> findByValidityStatus(String validityStatus);

    @Query(value = """
            SELECT id, document_title, document_number, chapter, article, clause, point, content, metadata, embedding, validity_status, created_at
            FROM legal_knowledge_chunk
            WHERE validity_status = 'VALID'
              AND embedding IS NOT NULL
            ORDER BY embedding <=> cast(:queryVector as vector)
            LIMIT :topK
            """, nativeQuery = true)
    List<LegalKnowledgeChunk> searchSimilarVectors(@Param("queryVector") String queryVector, @Param("topK") int topK);

    @Query(value = """
            SELECT id, document_title, document_number, chapter, article, clause, point, content, metadata, embedding, validity_status, created_at
            FROM legal_knowledge_chunk
            WHERE validity_status = 'VALID'
              AND (
                   to_tsvector('simple', content) @@ plainto_tsquery('simple', :cleanKeywords)
                   OR (:orKeywords != '' AND to_tsvector('simple', content) @@ to_tsquery('simple', :orKeywords))
                   OR content ILIKE CONCAT('%', :cleanKeywords, '%')
              )
            ORDER BY
              CASE WHEN to_tsvector('simple', content) @@ plainto_tsquery('simple', :cleanKeywords) THEN 1 ELSE 0 END DESC,
              CASE WHEN :orKeywords != '' THEN ts_rank(to_tsvector('simple', content), to_tsquery('simple', :orKeywords)) ELSE 0 END DESC
            LIMIT :topK
            """, nativeQuery = true)
    List<LegalKnowledgeChunk> searchMultiTierKeywordMatches(
            @Param("cleanKeywords") String cleanKeywords,
            @Param("orKeywords") String orKeywords,
            @Param("topK") int topK
    );
}
