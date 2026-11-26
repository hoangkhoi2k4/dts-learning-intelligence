-- ====================================================================
-- Script khởi tạo hạ tầng RAG cho dts-learning-intelligence
-- Bảng tri thức pháp luật giao thông & Bảng quản lý hạn mức Token/Quota
-- ====================================================================

-- 1. Kích hoạt extension pgvector
CREATE EXTENSION IF NOT EXISTS vector;

-- 2. Bảng lưu trữ tri thức pháp luật giao thông
CREATE TABLE IF NOT EXISTS legal_knowledge_chunk (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_title VARCHAR(500) NOT NULL,
    document_number VARCHAR(100),       -- Ví dụ: 36/2024/QH15, 100/2019/ND-CP
    chapter VARCHAR(100),
    article VARCHAR(50),                -- Điều
    clause VARCHAR(50),                 -- Khoản
    point VARCHAR(50),                  -- Điểm
    content TEXT NOT NULL,
    metadata JSONB,                     -- { "loai_phuong_tien": "xe_may", "hanh_vi": "chay_qua_toc_do" }
    embedding VECTOR,                   -- Vector embedding (hỗ trợ cả 768-dim Gemini hoặc 1024-dim BGE-M3)
    validity_status VARCHAR(50) DEFAULT 'VALID', -- VALID, EXPIRED, AMENDED
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- Index tìm kiếm vector Cosine Distance
CREATE INDEX IF NOT EXISTS idx_legal_knowledge_embedding 
ON legal_knowledge_chunk USING hnsw (embedding vector_cosine_ops);

-- Cột và Index Full-Text Search
ALTER TABLE legal_knowledge_chunk ADD COLUMN IF NOT EXISTS content_tsv TSVECTOR;
UPDATE legal_knowledge_chunk SET content_tsv = to_tsvector('simple', content) WHERE content_tsv IS NULL;
CREATE INDEX IF NOT EXISTS idx_legal_knowledge_tsv ON legal_knowledge_chunk USING gin(content_tsv);

-- 3. Bảng quản lý hạn mức sử dụng Token theo từng học viên
CREATE TABLE IF NOT EXISTS user_rag_usage (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    usage_date DATE NOT NULL,
    query_count INT DEFAULT 0,
    total_tokens_consumed INT DEFAULT 0,
    last_requested_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_rag_usage UNIQUE(user_id, usage_date)
);
CREATE INDEX IF NOT EXISTS idx_user_rag_usage_date ON user_rag_usage(user_id, usage_date);
