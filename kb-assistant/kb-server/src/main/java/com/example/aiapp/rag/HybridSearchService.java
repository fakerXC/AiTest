package com.example.aiapp.rag;

import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 混合检索主服务（第 7 章链路 + 第 8 章问答/评估共用的同一个 Bean——
 * 评估器调用的检索服务必须和线上问答是同一个实现，否则评估失真）。
 *
 * 链路：混合召回（向量 top-recall + BM25 top-recall）-> RRF 融合 -> rerank 精排 top-n。
 */
@Service
public class HybridSearchService {

    private final RetrievalLabService retrievalLab;
    private final Reranker reranker;
    private final int recallSize;
    private final int rerankTopN;

    public HybridSearchService(RetrievalLabService retrievalLab,
                               Reranker reranker,
                               @Value("${kb.recall-size}") int recallSize,
                               @Value("${kb.rerank.top-n}") int rerankTopN) {
        this.retrievalLab = retrievalLab;
        this.reranker = reranker;
        this.recallSize = recallSize;
        this.rerankTopN = rerankTopN;
    }

    /**
     * 完整检索链路，返回按精排分数降序的 Document 列表（metadata 含 docId/docName/chunkId/titlePath）。
     * @param topK 最终返回条数（问答取 5，评估取 10）
     */
    public List<Document> search(String query, int topK) {
        List<RrfFusion.FusedDoc> coarse = retrievalLab.hybrid(query, recallSize);
        return reranker.rerank(query, coarse, Math.max(topK, rerankTopN)).stream()
                .limit(topK)
                .map(r -> Document.builder()
                        .id(r.doc().chunkId())
                        .text(r.doc().content())
                        .metadata("chunkId", r.doc().chunkId())
                        .metadata("docId", r.doc().docId())
                        .metadata("docName", r.doc().docName())
                        .metadata("titlePath", r.doc().titlePath())
                        .score(r.score())
                        .build())
                .toList();
    }
}
