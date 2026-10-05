package com.example.aiapp.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 自定义混合检索器（第 7 章第八步）：把「混合检索 + rerank」包成
 * Spring AI 的 DocumentRetriever，可直接挂进 RetrievalAugmentationAdvisor。
 */
@Component
public class HybridDocumentRetriever implements DocumentRetriever {

    private final RetrievalLabService retrievalLab;
    private final Reranker reranker;

    public HybridDocumentRetriever(RetrievalLabService retrievalLab, Reranker reranker) {
        this.retrievalLab = retrievalLab;
        this.reranker = reranker;
    }

    @Override
    public List<Document> retrieve(Query query) {
        // 1. 粗召回：混合检索 top 50
        List<RrfFusion.FusedDoc> coarse = retrievalLab.hybrid(query.text(), 50);
        // 2. 精排取 top 8，把精排分数写回 Document.score
        return reranker.rerank(query.text(), coarse, 8).stream()
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
