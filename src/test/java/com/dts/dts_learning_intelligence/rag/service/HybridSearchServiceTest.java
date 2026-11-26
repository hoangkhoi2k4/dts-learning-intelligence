package com.dts.dts_learning_intelligence.rag.service;

import com.dts.dts_learning_intelligence.rag.dto.ScoredDocumentDto;
import com.dts.dts_learning_intelligence.rag.entity.LegalKnowledgeChunk;
import com.dts.dts_learning_intelligence.rag.repository.LegalKnowledgeChunkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HybridSearchServiceTest {

    @Mock
    private LegalKnowledgeChunkRepository repository;

    private HybridSearchService hybridSearchService;

    @BeforeEach
    void setUp() {
        hybridSearchService = new HybridSearchService(repository);
    }

    @Test
    @DisplayName("RRF xếp hạng cao nhất cho tài liệu xuất hiện ở cả Vector Search và Keyword Search")
    void testHybridSearchCombinesScoresCorrectly() {
        UUID doc1Id = UUID.randomUUID();
        UUID doc2Id = UUID.randomUUID();
        UUID doc3Id = UUID.randomUUID();

        LegalKnowledgeChunk doc1 = LegalKnowledgeChunk.builder()
                .id(doc1Id)
                .documentTitle("Luật GTĐB")
                .documentNumber("36/2024/QH15")
                .article("5")
                .content("Quy tắc vượt xe an toàn")
                .build();

        LegalKnowledgeChunk doc2 = LegalKnowledgeChunk.builder()
                .id(doc2Id)
                .documentTitle("Nghị định xử phạt")
                .documentNumber("100/2019/NĐ-CP")
                .article("6")
                .content("Xử phạt vi phạm vượt đèn đỏ")
                .build();

        LegalKnowledgeChunk doc3 = LegalKnowledgeChunk.builder()
                .id(doc3Id)
                .documentTitle("Luật GTĐB")
                .documentNumber("36/2024/QH15")
                .article("8")
                .content("Các hành vi bị nghiêm cấm")
                .build();

        // Vector Search trả về: doc1 (rank 1), doc2 (rank 2)
        when(repository.searchSimilarVectors(anyString(), anyInt()))
                .thenReturn(List.of(doc1, doc2));

        // Keyword Search trả về: doc1 (rank 1), doc3 (rank 2)
        when(repository.searchMultiTierKeywordMatches(anyString(), anyString(), anyInt()))
                .thenReturn(List.of(doc1, doc3));

        List<ScoredDocumentDto> results = hybridSearchService.search("vượt xe an toàn", "[0.1, 0.2]", 3);

        assertNotNull(results);
        assertEquals(3, results.size());

        // Doc1 xuất hiện ở cả 2 luồng nên RRF Score phải cao nhất và đứng đầu
        assertEquals(doc1Id, results.get(0).getId());
        assertEquals(1, results.get(0).getVectorRank());
        assertEquals(1, results.get(0).getKeywordRank());
        assertTrue(results.get(0).getRrfScore() > results.get(1).getRrfScore());
    }

    @Test
    @DisplayName("Fallback hoạt động an toàn khi không có vector query (chỉ tìm kiếm keyword)")
    void testKeywordOnlySearch() {
        UUID docId = UUID.randomUUID();
        LegalKnowledgeChunk doc = LegalKnowledgeChunk.builder()
                .id(docId)
                .documentTitle("Nghị định 100")
                .documentNumber("100/2019/NĐ-CP")
                .article("5")
                .clause("1")
                .content("Phạt tiền từ 200.000 đến 400.000 đồng khi không có bảo hiểm")
                .build();

        when(repository.searchMultiTierKeywordMatches(anyString(), anyString(), anyInt()))
                .thenReturn(List.of(doc));

        List<ScoredDocumentDto> results = hybridSearchService.search("bảo hiểm xe máy", null, 5);

        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals(docId, results.get(0).getId());
        assertEquals(1, results.get(0).getKeywordRank());
        assertNull(results.get(0).getVectorRank());
        assertTrue(results.get(0).getRrfScore() > 0);
    }
}
