package com.example.aiapp.rag;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reciprocal Rank Fusion 融合器（第 7 章第四步）。
 * 不看分数只看名次：RRF(d) = Σ weight_i / (k + rank_i(d))。
 * 输入：若干路「已按相关性降序」的结果列表；输出：按 RRF 分数降序、按 chunkId 去重的融合列表。
 */
public final class RrfFusion {

    /** 平滑常数，RRF 原始论文与业界默认值（k=60），不建议随便改 */
    public static final int DEFAULT_K = 60;

    private RrfFusion() {}

    /** 等权融合 */
    public static List<FusedDoc> fuse(List<List<EsBm25Searcher.RankedDoc>> resultLists) {
        double[] ones = new double[resultLists.size()];
        Arrays.fill(ones, 1.0);
        return fuse(resultLists, ones);
    }

    /**
     * 带权重融合。
     * @param weights 与 resultLists 一一对应，如 向量路 1.0、BM25 路 1.5（文号/错误码类查询用）
     */
    public static List<FusedDoc> fuse(List<List<EsBm25Searcher.RankedDoc>> resultLists,
                                      double[] weights) {
        Map<String, FusedDoc> acc = new HashMap<>();
        for (int i = 0; i < resultLists.size(); i++) {
            List<EsBm25Searcher.RankedDoc> list = resultLists.get(i);
            for (int rank = 0; rank < list.size(); rank++) {
                EsBm25Searcher.RankedDoc doc = list.get(rank);
                // RRF 核心：名次倒数累加，rank 从 0 开始所以 +1
                double contribution = weights[i] * (1.0 / (DEFAULT_K + rank + 1));
                acc.compute(doc.chunkId(), (id, old) -> old == null
                        ? new FusedDoc(doc.chunkId(), doc.docId(), doc.docName(),
                                       doc.titlePath(), doc.content(), contribution)
                        : old.addScore(contribution));
            }
        }
        List<FusedDoc> fused = new ArrayList<>(acc.values());
        fused.sort((a, b) -> Double.compare(b.score(), a.score()));
        return fused;
    }

    /** 融合结果记录；addScore 返回累加后的新实例 */
    public record FusedDoc(String chunkId, String docId, String docName,
                           String titlePath, String content, double score) {
        FusedDoc addScore(double delta) {
            return new FusedDoc(chunkId, docId, docName, titlePath, content, score + delta);
        }
    }
}
