"""
Script import dữ liệu pháp luật giao thông từ Do_An-Long-Khoi-2026 sang dts-learning-intelligence.
Hỗ trợ đọc data3.json (xử phạt) và data4.json (quy định) đã có sẵn vector 1024-chiều.
Chạy an toàn, không tạo dữ liệu trùng lặp (Idempotent).
"""

import json
import os
import sys
import psycopg2
from psycopg2.extras import execute_values

DB_HOST = os.getenv("DB_HOST", "localhost")
DB_PORT = os.getenv("DB_PORT", "5433")
DB_NAME = os.getenv("DB_NAME", "dts_learning_intelligence")
DB_USER = os.getenv("DB_USER", "postgres")
DB_PASS = os.getenv("DB_PASS", "khoi0000")

def import_data(conn, file_path, default_doc_title, default_doc_num):
    print(f"📖 Đang đọc file: {file_path}")
    if not os.path.exists(file_path):
        print(f"❌ Không tìm thấy file: {file_path}")
        return 0

    with open(file_path, "r", encoding="utf-8") as f:
        items = json.load(f)

    cur = conn.cursor()
    records_to_insert = []

    for item in items:
        content = item.get("content") or item.get("page_content") or ""
        if not content.strip():
            continue

        raw_meta = item.get("metadata") or {}
        path_meta = raw_meta.get("path") or {}
        
        doc_num = path_meta.get("nghi_dinh") or raw_meta.get("so_hieu") or default_doc_num
        doc_title = default_doc_title
        chapter = path_meta.get("chuong") or raw_meta.get("chuong")
        article = str(path_meta.get("dieu_so") or raw_meta.get("dieu") or "")
        clause = str(path_meta.get("khoan_so") or raw_meta.get("khoan") or "")
        point = str(path_meta.get("diem_chu") or raw_meta.get("diem") or "")

        embedding = item.get("embedding")
        # Chuyển embedding list thành chuỗi định dạng vector PostgreSQL: '[0.123, -0.456, ...]'
        embedding_str = str(embedding) if embedding else None

        records_to_insert.append((
            doc_title,
            doc_num,
            chapter,
            article,
            clause,
            point,
            content,
            json.dumps(raw_meta, ensure_ascii=False),
            embedding_str,
            'VALID'
        ))

    insert_sql = """
    INSERT INTO legal_knowledge_chunk (
        document_title, document_number, chapter, article, clause, point, content, metadata, embedding, validity_status
    ) VALUES %s
    """
    execute_values(cur, insert_sql, records_to_insert)
    conn.commit()
    inserted_count = len(records_to_insert)
    cur.close()
    print(f"✅ Đã nạp thành công {inserted_count} bản ghi từ {os.path.basename(file_path)}")
    return inserted_count

def main():
    print("🚀 Bắt đầu quá trình nạp dữ liệu pháp luật giao thông...")
    try:
        conn = psycopg2.connect(
            host=DB_HOST,
            port=DB_PORT,
            dbname=DB_NAME,
            user=DB_USER,
            password=DB_PASS
        )
        print(f"🔌 Kết nối thành công tới PostgreSQL {DB_HOST}:{DB_PORT}/{DB_NAME}")
    except Exception as e:
        print(f"❌ Lỗi kết nối DB: {e}")
        sys.exit(1)

    cur = conn.cursor()
    # Kiểm tra số lượng bản ghi hiện có
    cur.execute("SELECT COUNT(*) FROM legal_knowledge_chunk;")
    existing_count = cur.fetchone()[0]
    if existing_count > 0:
        print(f"ℹ️ Bảng legal_knowledge_chunk đã có sẵn {existing_count} bản ghi.")
        cur.close()
        conn.close()
        return

    base_dir = os.path.normpath("c:/Users/LongBeo/Desktop/Do_An-Long-Khoi-2026/tien_xu_ly")
    data4_path = os.path.join(base_dir, "data4.json")
    data3_path = os.path.join(base_dir, "data3.json")

    total = 0
    total += import_data(conn, data4_path, "Luật Trật tự an toàn giao thông đường bộ 2024", "36/2024/QH15")
    total += import_data(conn, data3_path, "Nghị định quy định xử phạt vi phạm hành chính giao thông", "100/2019/NĐ-CP")

    # Cập nhật cột TSVECTOR cho Full-Text Search
    print("🔄 Đang cập nhật chỉ mục Full-Text Search TSVECTOR...")
    cur = conn.cursor()
    cur.execute("UPDATE legal_knowledge_chunk SET content_tsv = to_tsvector('simple', content) WHERE content_tsv IS NULL;")
    conn.commit()
    cur.close()
    conn.close()
    print(f"🎉 HOÀN TẤT! Tổng cộng đã nạp {total} bản ghi tri thức pháp luật.")

if __name__ == "__main__":
    main()
