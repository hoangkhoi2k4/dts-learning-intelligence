package com.dts.dts_learning_intelligence.rag.service;

import com.dts.dts_learning_intelligence.rag.dto.QuestionDetailDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Tra cứu thông tin chi tiết câu hỏi lý thuyết từ cơ sở dữ liệu dts_practice.
 */
@Service
@Slf4j
public class PracticeQuestionLookupService {

    private final JdbcTemplate practiceJdbcTemplate;
    private final ObjectMapper objectMapper;

    public PracticeQuestionLookupService(
            @Value("${DB_HOST:103.20.96.85}") String host,
            @Value("${DB_PORT:5434}") String port,
            @Value("${DB_USERNAME:postgres}") String username,
            @Value("${DB_PASSWORD:K59Ptl*100504}") String password,
            ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(String.format("jdbc:postgresql://%s:%s/dts_practice", host, port));
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        this.practiceJdbcTemplate = new JdbcTemplate(dataSource);
    }

    public Optional<QuestionDetailDto> findQuestionDetail(Integer questionId) {
        if (questionId == null) {
            return Optional.empty();
        }
        try {
            String sql = """
                    SELECT id, chapter, question_text, options::text as options_json, correct_answer, is_critical, explanation, source_question_id
                    FROM questions
                    WHERE id = ? OR source_question_id = ?
                    LIMIT 1
                    """;
            return practiceJdbcTemplate.query(sql, rs -> {
                if (rs.next()) {
                    List<Map<String, Object>> optionsList = List.of();
                    String optionsJson = rs.getString("options_json");
                    if (optionsJson != null && !optionsJson.isBlank()) {
                        try {
                            optionsList = objectMapper.readValue(optionsJson, new TypeReference<>() {});
                        } catch (Exception ex) {
                            log.warn("Failed to parse question options json: {}", ex.getMessage());
                        }
                    }

                    return Optional.of(QuestionDetailDto.builder()
                            .questionId(rs.getInt("id"))
                            .sourceQuestionId(rs.getInt("source_question_id"))
                            .chapter(rs.getInt("chapter"))
                            .questionText(rs.getString("question_text"))
                            .options(optionsList)
                            .correctAnswer(rs.getString("correct_answer"))
                            .isCritical(rs.getBoolean("is_critical"))
                            .explanation(rs.getString("explanation"))
                            .build());
                }
                return Optional.empty();
            }, questionId, questionId);
        } catch (Exception e) {
            log.warn("Could not query dts_practice.questions for ID {}: {}", questionId, e.getMessage());
            return Optional.empty();
        }
    }
}
