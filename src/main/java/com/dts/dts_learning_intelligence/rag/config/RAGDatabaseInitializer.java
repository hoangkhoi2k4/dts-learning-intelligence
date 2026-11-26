package com.dts.dts_learning_intelligence.rag.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class RAGDatabaseInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;
    private final RAGProperties ragProperties;

    @Override
    public void run(String... args) {
        if (!ragProperties.isEnabled()) {
            return;
        }

        try {
            // 1. Kiểm tra xem PostgreSQL có sẵn extension vector không trước khi kích hoạt
            Boolean hasVectorExtension = jdbcTemplate.queryForObject(
                    "SELECT EXISTS(SELECT 1 FROM pg_available_extensions WHERE name = 'vector')",
                    Boolean.class
            );

            if (Boolean.TRUE.equals(hasVectorExtension)) {
                jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS vector;");
                log.info("pgvector extension is available and enabled.");
            } else {
                log.info("PostgreSQL host does not have pgvector installed. Operating in native Full-Text Search mode (no extension required).");
            }

            // 2. Tự động kiểm tra và tạo cột/index Full-Text Search nếu bảng đã được Hibernate tạo
            jdbcTemplate.execute("""
                DO $$
                BEGIN
                    IF EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'legal_knowledge_chunk') THEN
                        ALTER TABLE legal_knowledge_chunk ADD COLUMN IF NOT EXISTS content_tsv TSVECTOR;
                        UPDATE legal_knowledge_chunk SET content_tsv = to_tsvector('simple', content) WHERE content_tsv IS NULL;
                        CREATE INDEX IF NOT EXISTS idx_legal_knowledge_tsv ON legal_knowledge_chunk USING gin(content_tsv);
                    END IF;
                END $$;
            """);

            // 3. Tự động tạo Vector HNSW Index nếu có extension vector
            jdbcTemplate.execute("""
                DO $$
                BEGIN
                    IF EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'legal_knowledge_chunk') THEN
                        BEGIN
                            CREATE INDEX IF NOT EXISTS idx_legal_knowledge_embedding 
                            ON legal_knowledge_chunk USING hnsw (embedding vector_cosine_ops);
                        EXCEPTION WHEN OTHERS THEN
                            -- Bỏ qua nếu cột embedding chưa có dữ liệu hoặc kiểu dữ liệu đang cập nhật
                            NULL;
                        END;
                    END IF;
                END $$;
            """);

            log.info("RAG database initialization completed successfully.");
        } catch (Exception e) {
            log.warn("Notice during RAG database initialization: {}. (System will continue to operate)", e.getMessage());
        }
    }
}
