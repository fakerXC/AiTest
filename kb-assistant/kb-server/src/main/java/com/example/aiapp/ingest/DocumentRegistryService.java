package com.example.aiapp.ingest;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 文档登记表：文档列表、版本号（单调递增）、chunk 数。
 * 版本号写进 chunk metadata（docVersion），检索可校验、前端可展示答案依据的版本（第 8 章坑 3）。
 */
@Service
public class DocumentRegistryService {

    private final JdbcTemplate jdbcTemplate;

    public DocumentRegistryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void initSchema() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS kb_document (
                    doc_id       VARCHAR(128) PRIMARY KEY,
                    name         VARCHAR(512) NOT NULL,
                    version      INT NOT NULL,
                    chunk_count  INT NOT NULL,
                    updated_at   TIMESTAMP NOT NULL DEFAULT now()
                )
                """);
    }

    /** 登记或升级文档，返回新版本号 */
    public int registerOrBump(String docId, String name, int chunkCount) {
        initSchema();
        Integer current = jdbcTemplate.query(
                "SELECT version FROM kb_document WHERE doc_id = ?",
                rs -> rs.next() ? rs.getInt(1) : null, docId);
        int version = current == null ? 1 : current + 1;
        jdbcTemplate.update("""
                INSERT INTO kb_document (doc_id, name, version, chunk_count, updated_at)
                VALUES (?, ?, ?, ?, now())
                ON CONFLICT (doc_id) DO UPDATE
                SET name = EXCLUDED.name, version = EXCLUDED.version,
                    chunk_count = EXCLUDED.chunk_count, updated_at = now()
                """, docId, name, version, chunkCount);
        return version;
    }

    public void remove(String docId) {
        initSchema();
        jdbcTemplate.update("DELETE FROM kb_document WHERE doc_id = ?", docId);
    }

    public List<DocumentInfo> list() {
        initSchema();
        return jdbcTemplate.query(
                "SELECT doc_id, name, version, chunk_count, updated_at FROM kb_document ORDER BY updated_at DESC",
                (rs, i) -> new DocumentInfo(rs.getString("doc_id"), rs.getString("name"),
                        rs.getInt("version"), rs.getInt("chunk_count"),
                        rs.getTimestamp("updated_at").toString()));
    }

    public record DocumentInfo(String docId, String name, int version,
                               int chunkCount, String updatedAt) {}
}
