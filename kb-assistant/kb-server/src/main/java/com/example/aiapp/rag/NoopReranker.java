package com.example.aiapp.rag;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 默认精排：不调用外部 rerank 模型，按 RRF 名次直通。
 * kb.rerank.provider=none（默认）时生效；有 DASHSCOPE_API_KEY 后改 dashscope 即可切换。
 */
@Component
@ConditionalOnProperty(name = "kb.rerank.provider", havingValue = "none", matchIfMissing = true)
public class NoopReranker implements Reranker {

    @Override
    public List<RerankResult> rerank(String query, List<RrfFusion.FusedDoc> docs, int topN) {
        return docs.stream()
                .limit(topN)
                .map(d -> new RerankResult(d, d.score()))
                .toList();
    }
}
