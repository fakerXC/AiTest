package com.example.aiapp.rag;

import tools.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 阿里百炼 gte-rerank 精排客户端（DashScope 原生 HTTP 接口，第 7 章第六步）。
 * 注意：gte-rerank 系列只走 DashScope 原生端点，OpenAI 兼容模式（/compatible-mode/v1）不提供。
 * 启用方式：配置环境变量 DASHSCOPE_API_KEY，并设 kb.rerank.provider=dashscope
 */
@Component
@ConditionalOnProperty(name = "kb.rerank.provider", havingValue = "dashscope")
public class DashScopeReranker implements Reranker {

    private static final String RERANK_URL =
            "https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank";

    private final RestClient http;

    public DashScopeReranker(@Value("${DASHSCOPE_API_KEY}") String apiKey) {
        this.http = RestClient.builder()
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    @Override
    public List<RerankResult> rerank(String query, List<RrfFusion.FusedDoc> docs, int topN) {
        List<String> texts = docs.stream().map(RrfFusion.FusedDoc::content).toList();

        Map<String, Object> body = Map.of(
                "model", "gte-rerank-v2",
                "input", Map.of("query", query, "documents", texts),
                "parameters", Map.of(
                        "top_n", topN,
                        "return_documents", false));  // 不返回原文，靠 index 回指，省流量

        JsonNode resp = http.post()
                .uri(RERANK_URL)
                .body(body)
                .retrieve()
                .body(JsonNode.class);

        List<RerankResult> results = new ArrayList<>();
        // output.results 每项含 index / relevance_score
        for (JsonNode item : resp.path("output").path("results")) {
            int idx = item.path("index").asInt();
            results.add(new RerankResult(docs.get(idx), item.path("relevance_score").asDouble()));
        }
        results.sort(Comparator.comparingDouble(RerankResult::score).reversed());
        return results;
    }
}
