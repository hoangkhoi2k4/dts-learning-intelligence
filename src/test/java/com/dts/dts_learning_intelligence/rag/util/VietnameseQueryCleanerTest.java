package com.dts.dts_learning_intelligence.rag.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VietnameseQueryCleanerTest {

    @Test
    @DisplayName("Làm sạch câu hỏi nồng độ cồn loại bỏ stopwords thành công")
    void testCleanAlcoholQuery() {
        String input = "Nồng độ cồn khi lái xe ô tô bị xử phạt thế nào?";
        var result = VietnameseQueryCleaner.clean(input);

        assertNotNull(result);
        assertTrue(result.rawKeywords().contains("nồng độ cồn"));
        assertTrue(result.rawKeywords().contains("ô tô"));
        assertFalse(result.rawKeywords().contains("thế nào"));
        assertFalse(result.rawKeywords().contains("bị"));
        assertFalse(result.rawKeywords().contains("khi"));

        assertTrue(result.orTsQuery().contains("|"));
    }

    @Test
    @DisplayName("Làm sạch câu hỏi nhường đường loại bỏ từ giao tiếp 'giải thích'")
    void testCleanIntersectionQuery() {
        String input = "Giải thích quy tắc nhường đường tại nơi giao nhau";
        var result = VietnameseQueryCleaner.clean(input);

        assertNotNull(result);
        assertTrue(result.rawKeywords().contains("nhường đường"));
        assertTrue(result.rawKeywords().contains("giao nhau"));
        assertFalse(result.rawKeywords().contains("giải"));
        assertFalse(result.rawKeywords().contains("thích"));
    }

    @Test
    @DisplayName("Câu hỏi toàn từ đệm không bị crash và fallback an toàn")
    void testCleanStopwordsOnlyQuery() {
        String input = "Tại sao câu hỏi này tôi lại chọn sai?";
        var result = VietnameseQueryCleaner.clean(input);

        assertNotNull(result);
        // Do câu toàn từ đệm, phương thức trả về fallback token độ dài > 2 hoặc rỗng an toàn
        assertDoesNotThrow(() -> result.orTsQuery());
    }
}
