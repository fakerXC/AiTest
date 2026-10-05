package com.example.aiapp.rag;

import java.util.List;

/**
 * 第二阶段精排接口（cross-encoder rerank）。
 * 默认实现是 NoopReranker（RRF 名次直通）；有 DASHSCOPE_API_KEY 时切 DashScopeReranker。
 */
public interface Reranker {

    /**
     * @param query 用户问题
     * @param docs  粗召回候选（建议 20~50 条）
     * @param topN  精排后保留条数（建议 5~12；rerank 是降噪声不是保必中，别取太小）
     */
    List<RerankResult> rerank(String query, List<RrfFusion.FusedDoc> docs, int topN);

    record RerankResult(RrfFusion.FusedDoc doc, double score) {}
}
