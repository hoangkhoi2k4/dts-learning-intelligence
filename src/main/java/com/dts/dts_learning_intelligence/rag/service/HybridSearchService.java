package com.dts.dts_learning_intelligence.rag.service;

import com.dts.dts_learning_intelligence.rag.dto.LegalSourceDto;
import com.dts.dts_learning_intelligence.rag.dto.ScoredDocumentDto;
import com.dts.dts_learning_intelligence.rag.entity.LegalKnowledgeChunk;
import com.dts.dts_learning_intelligence.rag.repository.LegalKnowledgeChunkRepository;
import com.dts.dts_learning_intelligence.rag.util.VietnameseQueryCleaner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class HybridSearchService {

    private final LegalKnowledgeChunkRepository repository;

    private static final int RRF_K = 60;
    private static final double WEIGHT_VECTOR = 0.5;
    private static final double WEIGHT_KEYWORD = 0.5;

    public List<ScoredDocumentDto> search(String queryText, String queryVector, int topK) {
        if (queryText == null || queryText.trim().isEmpty()) {
            return Collections.emptyList();
        }

        int candidateLimit = Math.max(topK * 3, 15);
        Map<UUID, ScoredDocumentDto> scoredMap = new HashMap<>();

        // 1. Vector Search (nếu có vector truyền vào và pgvector khả dụng)
        if (queryVector != null && !queryVector.trim().isEmpty()) {
            try {
                List<LegalKnowledgeChunk> vectorResults = repository.searchSimilarVectors(queryVector, candidateLimit);
                for (int rank = 0; rank < vectorResults.size(); rank++) {
                    LegalKnowledgeChunk doc = vectorResults.get(rank);
                    int currentRank = rank + 1;
                    double vScore = WEIGHT_VECTOR / (RRF_K + currentRank);

                    ScoredDocumentDto dto = scoredMap.computeIfAbsent(doc.getId(), id -> toDto(doc));
                    dto.setVectorRank(currentRank);
                    dto.setRrfScore(dto.getRrfScore() + vScore);
                }
            } catch (Exception e) {
                log.warn("Vector search failed or pgvector extension not ready, falling back to full-text search: {}", e.getMessage());
            }
        }

        // 2. Full-Text Search (Multi-Tier Keyword Search: Cleaned AND + Ranked OR + ILIKE)
        try {
            VietnameseQueryCleaner.CleanedQueryResult cleaned = VietnameseQueryCleaner.clean(queryText);
            String cleanKeywords = cleaned.rawKeywords();
            String orKeywords = cleaned.orTsQuery();

            if (!cleanKeywords.isBlank()) {
                List<LegalKnowledgeChunk> keywordResults = repository.searchMultiTierKeywordMatches(cleanKeywords, orKeywords, candidateLimit);
                for (int rank = 0; rank < keywordResults.size(); rank++) {
                    LegalKnowledgeChunk doc = keywordResults.get(rank);
                    int currentRank = rank + 1;
                    double kwScore = WEIGHT_KEYWORD / (RRF_K + currentRank);

                    ScoredDocumentDto dto = scoredMap.computeIfAbsent(doc.getId(), id -> toDto(doc));
                    dto.setKeywordRank(currentRank);
                    dto.setRrfScore(dto.getRrfScore() + kwScore);
                }
            }
        } catch (Exception e) {
            log.warn("Keyword search encountered an issue: {}", e.getMessage());
        }

        // 3. Sắp xếp theo RRF Score giảm dần và lấy Top K
        return scoredMap.values().stream()
                .sorted(Comparator.comparingDouble(ScoredDocumentDto::getRrfScore).reversed())
                .limit(topK)
                .toList();
    }

    public List<LegalSourceDto> toSources(List<ScoredDocumentDto> scoredDocs) {
        List<LegalSourceDto> sources = new ArrayList<>();
        for (ScoredDocumentDto doc : scoredDocs) {
            String snippet = doc.getContent();
            if (snippet != null && snippet.length() > 300) {
                snippet = snippet.substring(0, 300) + "...";
            }
            sources.add(LegalSourceDto.builder()
                    .sourceId(doc.getId())
                    .documentTitle(doc.getDocumentTitle())
                    .documentNumber(doc.getDocumentNumber())
                    .chapter(doc.getChapter())
                    .article(doc.getArticle())
                    .clause(doc.getClause())
                    .point(doc.getPoint())
                    .citation(doc.formatCitation())
                    .snippet(snippet)
                    .validityStatus("VALID")
                    .build());
        }
        return sources;
    }

    private ScoredDocumentDto toDto(LegalKnowledgeChunk chunk) {
        return ScoredDocumentDto.builder()
                .id(chunk.getId())
                .documentTitle(chunk.getDocumentTitle())
                .documentNumber(chunk.getDocumentNumber())
                .chapter(chunk.getChapter())
                .article(chunk.getArticle())
                .clause(chunk.getClause())
                .point(chunk.getPoint())
                .content(chunk.getContent())
                .metadata(chunk.getMetadata())
                .rrfScore(0.0)
                .build();
    }
}
