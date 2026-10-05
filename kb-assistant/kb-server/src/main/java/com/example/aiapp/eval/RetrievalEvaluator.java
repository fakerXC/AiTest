package com.example.aiapp.eval;

import org.springframework.ai.document.Document;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 检索层评估器：对 golden set 逐题检索，计算 Recall@K 与 MRR。
 * 只依赖检索服务，不依赖大模型——确定性计算，适合做每次改动的回归门禁。
 */
public class RetrievalEvaluator {

    /** 检索函数：输入问题，返回按相关度排序的文档列表。由外部注入，可切换纯向量/混合/rerank 等实现 */
    @FunctionalInterface
    public interface Retriever {
        List<Document> retrieve(String question);
    }

    /** 单题评估结果 */
    public record CaseResult(String caseId, double recall, double reciprocalRank) {}

    /** 汇总报告；unanswerable 是拒答题数量（无标准出处，不参与 Recall/MRR 平均——见 Ragas 盲区二同理） */
    public record EvalReport(int total, int answerable, int unanswerable,
                             double recallAtK, double mrr, List<CaseResult> details) {}

    public EvalReport evaluate(List<GoldenCase> goldenSet, Retriever retriever, int k) {
        List<GoldenCase> answerable = goldenSet.stream()
                .filter(c -> c.goldenChunkIds() != null && !c.goldenChunkIds().isEmpty())
                .toList();
        int unanswerable = goldenSet.size() - answerable.size();
        List<CaseResult> details = answerable.stream()
                .map(c -> evaluateOne(c, retriever, k))
                .toList();
        double avgRecall = details.stream().mapToDouble(CaseResult::recall).average().orElse(0);
        double mrr = details.stream().mapToDouble(CaseResult::reciprocalRank).average().orElse(0);
        return new EvalReport(goldenSet.size(), answerable.size(), unanswerable,
                avgRecall, mrr, details);
    }

    private CaseResult evaluateOne(GoldenCase c, Retriever retriever, int k) {
        List<Document> topK = retriever.retrieve(c.question()).stream().limit(k).toList();
        Set<String> golden = new HashSet<>(c.goldenChunkIds());

        long hitCount = topK.stream().filter(d -> golden.contains(chunkIdOf(d))).count();
        // 分母是「min(标准出处数, K)」：出处多于 K 时，前 K 条最多也只能覆盖 K 个
        double recall = hitCount / (double) Math.min(golden.size(), k);

        double rr = 0; // 倒数排名：找到第一个命中的位置
        for (int i = 0; i < topK.size(); i++) {
            if (golden.contains(chunkIdOf(topK.get(i)))) {
                rr = 1.0 / (i + 1);
                break;
            }
        }
        return new CaseResult(c.id(), recall, rr);
    }

    /** chunk ID 存在文档 metadata 里，由摄取管线写入 */
    private String chunkIdOf(Document doc) {
        return String.valueOf(doc.getMetadata().get("chunkId"));
    }
}
