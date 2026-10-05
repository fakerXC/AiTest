package com.example.aiapp.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 检索通道实验与粗召回（第 7 章第五步）：纯向量 / 纯 BM25 / RRF 融合三路。
 */
@Service
public class RetrievalLabService {

    private final VectorStore vectorStore;
    private final EsBm25Searcher bm25Searcher;

    public RetrievalLabService(VectorStore vectorStore, EsBm25Searcher bm25Searcher) {
        this.vectorStore = vectorStore;
        this.bm25Searcher = bm25Searcher;
    }

    /** 纯向量：pgvector 相似度检索 */
    public List<RrfFusion.FusedDoc> vectorOnly(String query, int topK) {
        return RrfFusion.fuse(List.of(vectorSearch(query, topK)));
    }

    /** 纯 BM25 */
    public List<RrfFusion.FusedDoc> bm25Only(String query, int topK) {
        return RrfFusion.fuse(List.of(bm25Searcher.search(query, topK)));
    }

    /** 混合：两路各召回 topK，RRF 等权融合 */
    public List<RrfFusion.FusedDoc> hybrid(String query, int topK) {
        return RrfFusion.fuse(List.of(vectorSearch(query, topK), bm25Searcher.search(query, topK)));
    }

    private List<EsBm25Searcher.RankedDoc> vectorSearch(String query, int topK) {
        return vectorStore.similaritySearch(
                        SearchRequest.builder().query(query).topK(topK).build())
                .stream().map(this::toRanked).toList();
    }

    private EsBm25Searcher.RankedDoc toRanked(Document d) {
        // 入库时已把 chunkId/docId/docName/titlePath 写入 metadata，保证与 ES 侧同源
        return new EsBm25Searcher.RankedDoc(
                String.valueOf(d.getMetadata().getOrDefault("chunkId", d.getId())),
                String.valueOf(d.getMetadata().getOrDefault("docId", "")),
                String.valueOf(d.getMetadata().getOrDefault("docName", "")),
                String.valueOf(d.getMetadata().getOrDefault("titlePath", "")),
                d.getText(),
                d.getScore() != null ? d.getScore() : 0.0);
    }
}
