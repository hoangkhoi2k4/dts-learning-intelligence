package com.dts.dts_learning_intelligence.rag.util;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Tiện ích làm sạch câu hỏi tự nhiên tiếng Việt cho bộ tìm kiếm RAG PostgreSQL.
 * Loại bỏ từ đệm giao tiếp, câu hỏi tu từ, và sinh chuỗi tsquery chuẩn.
 */
public class VietnameseQueryCleaner {

    private static final Set<String> STOPWORDS = new HashSet<>(Arrays.asList(
            // Từ hỏi & nghi vấn
            "tại", "sao", "vì", "làm", "thế", "nào", "như", "bao", "nhiêu", "ở", "đâu", "là", "gì", "ai",
            // Từ đệm hội thoại
            "giải", "thích", "cho", "tôi", "mình", "bạn", "hỏi", "biết", "xin", "vui", "lòng", "giúp", "với",
            // Trạng từ, quan hệ từ
            "bị", "khi", "lại", "này", "của", "và", "các", "những", "cho", "đang", "đã", "sẽ", "được", "có",
            // Thuật ngữ ngữ cảnh bài làm
            "câu", "hỏi", "chọn", "sai", "đúng", "không", "đáp", "án", "phương", "tiện", "nào", "mục", "này"
    ));

    private static final Pattern WORD_PATTERN = Pattern.compile("[\\p{L}0-9]+");

    public record CleanedQueryResult(
            String rawKeywords,
            String andTsQuery,
            String orTsQuery
    ) {}

    public static CleanedQueryResult clean(String input) {
        if (input == null || input.isBlank()) {
            return new CleanedQueryResult("", "", "");
        }

        List<String> tokens = new ArrayList<>();
        Matcher matcher = WORD_PATTERN.matcher(input.toLowerCase());
        while (matcher.find()) {
            String word = matcher.group();
            if (!STOPWORDS.contains(word)) {
                tokens.add(word);
            }
        }

        if (tokens.isEmpty()) {
            // Nếu lọc hết (do câu toàn từ đệm), lấy lại các từ dài hơn 2 ký tự
            matcher = WORD_PATTERN.matcher(input.toLowerCase());
            while (matcher.find()) {
                String word = matcher.group();
                if (word.length() > 2) {
                    tokens.add(word);
                }
            }
        }

        // Loại bỏ trùng lặp giữ nguyên thứ tự
        List<String> distinctTokens = tokens.stream().distinct().collect(Collectors.toList());

        String rawKeywords = String.join(" ", distinctTokens);
        String andTsQuery = String.join(" & ", distinctTokens);
        String orTsQuery = String.join(" | ", distinctTokens);

        return new CleanedQueryResult(rawKeywords, andTsQuery, orTsQuery);
    }
}
