package com.example.aiapp.rag;

import tools.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 基于 Elasticsearch match 查询的 BM25 检索通道（第 7 章第三步）。
 * 与向量通道地位对等：输入 query、输出按 _score 降序的候选列表。
 */
@Component
public class EsBm25Searcher {

    private final RestClient esClient;
    private final String index;

    public EsBm25Searcher(@Value("${kb.es.url}") String esUrl,
                          @Value("${kb.es.index}") String index) {
        this.esClient = RestClient.builder().baseUrl(esUrl).build();
        this.index = index;
    }

    /**
     * BM25 检索，按 _score 降序返回。
     * @param query 用户查询原文（ES 侧先过 ik_smart 分词）
     * @param topK  粗召回条数（建议 20~50）
     */
    public List<RankedDoc> search(String query, int topK) {
        Map<String, Object> body = Map.of(
                "size", topK,
                "_source", List.of("chunkId", "docId", "docName", "titlePath", "content"),
                "query", Map.of("match", Map.of("content", query)));

        JsonNode resp = esClient.post()
                .uri("/{index}/_search", index)
                .body(body)
                .retrieve()
                .body(JsonNode.class);

        List<RankedDoc> result = new ArrayList<>();
        for (JsonNode hit : resp.path("hits").path("hits")) {
            JsonNode source = hit.path("_source");
            result.add(new RankedDoc(
                    source.path("chunkId").asText(),
                    source.path("docId").asText(),
                    source.path("docName").asText(),
                    source.path("titlePath").asText(),
                    source.path("content").asText(),
                    hit.path("_score").asDouble()));
        }
        return result;
    }

    /** 一条带分数的检索结果；chunkId 是与向量库对齐的 key */
    public record RankedDoc(String chunkId, String docId, String docName,
                            String titlePath, String content, double score) {}
}
