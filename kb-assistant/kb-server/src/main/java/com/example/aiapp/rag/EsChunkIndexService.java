package com.example.aiapp.rag;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * 把 chunk 双写进 Elasticsearch，供 BM25 检索使用（第 7 章第二步）。
 * chunkId 是 pgvector 与 ES 两侧共同的 join key，RRF 去重靠它。
 */
@Service
public class EsChunkIndexService {

    private final RestClient esClient;
    private final String index;

    public EsChunkIndexService(@Value("${kb.es.url}") String esUrl,
                               @Value("${kb.es.index}") String index) {
        this.esClient = RestClient.builder().baseUrl(esUrl).build();
        this.index = index;
    }

    /** 建索引（幂等）：ik_max_word 建索引切最细保证召回，ik_smart 查询切粗粒度保证精度 */
    public void ensureIndex() {
        Boolean exists = esClient.head()
                .uri("/{index}", index)
                .exchange((req, resp) -> resp.getStatusCode().is2xxSuccessful());
        if (Boolean.TRUE.equals(exists)) {
            return;
        }
        esClient.put()
                .uri("/{index}", index)
                .body(Map.of("mappings", Map.of("properties", Map.of(
                        "chunkId", Map.of("type", "keyword"),
                        "docId", Map.of("type", "keyword"),
                        "docName", Map.of("type", "keyword"),
                        "titlePath", Map.of("type", "keyword"),
                        "content", Map.of(
                                "type", "text",
                                "analyzer", "ik_max_word",
                                "search_analyzer", "ik_smart")))))
                .retrieve()
                .toBodilessEntity();
    }

    /** 批量写入 chunk（逐条 PUT，演示用；生产请用 _bulk 接口） */
    public void indexChunks(List<ChunkRecord> chunks) {
        ensureIndex();
        for (ChunkRecord c : chunks) {
            esClient.put()
                    .uri("/{index}/_doc/{id}", index, c.chunkId())
                    .body(Map.of(
                            "chunkId", c.chunkId(),
                            "docId", c.docId(),
                            "docName", c.docName(),
                            "titlePath", c.titlePath(),
                            "content", c.content()))
                    .retrieve()
                    .toBodilessEntity();
        }
    }

    /** 按 docId 删除该文档全部 chunk（先删后插的「删」在 ES 侧的另一半） */
    public void deleteByDocId(String docId) {
        ensureIndex();  // 索引可能还没建（首次摄取的「先删」），先确保存在
        esClient.post()
                .uri("/{index}/_delete_by_query", index)
                .body(Map.of("query", Map.of("term", Map.of("docId", docId))))
                .retrieve()
                .toBodilessEntity();
    }

    /** 一条 chunk 的最小记录 */
    public record ChunkRecord(String chunkId, String docId, String docName,
                              String titlePath, String content) {}
}
