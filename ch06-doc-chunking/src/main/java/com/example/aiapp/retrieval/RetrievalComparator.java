package com.example.aiapp.retrieval;

import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 6.8 检索效果对比实验：7 个代表性问题覆盖四种意图类型——
 * 精确编号查询、精确条款、跨条款组合、语义模糊（无原文关键词）、表格数据、列举型。
 *
 * 读结果技巧：凡是「三档全失败」的问题，失败原因一定在切块之前（解析/清洗层）；
 * 凡是「随档位变化明显」的问题，失败原因才在 chunk size 本身。
 */
@Component
public class RetrievalComparator {

    private static final List<String> QUESTIONS = List.of(
            "第九条规定的报销材料有哪些？",      // 精确编号
            "出差住宿超标怎么办？",              // 精确条款
            "迟到是怎么规定的，和绩效怎么挂钩？", // 跨条款组合
            "住酒店花了 600 块能报吗？",         // 语义模糊，无原文关键词
            "一线城市住宿标准多少钱？",          // 表格数据
            "报销要提供哪些材料？",              // 列举型
            "出差期间迟到扣绩效吗？");           // 跨条款组合（考勤+出差）

    private final VectorStore vectorStore;

    public RetrievalComparator(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /** 跑完全部 7 个问题 */
    public void compareAll() {
        for (String question : QUESTIONS) {
            compareRetrieval(question);
        }
    }

    public void compareRetrieval(String question) {
        System.out.printf("%n>>> 问题：%s%n", question);
        var results = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(question)
                        .topK(3)
                        .similarityThreshold(0.5)
                        .build());
        if (results.isEmpty()) {
            System.out.println("  （无命中，全部低于相似度阈值 0.5）");
        }
        for (var doc : results) {
            String t = doc.getText().replace("\n", " ");
            System.out.printf("  score=%.3f | titlePath=%s | %s...%n",
                    doc.getScore(), doc.getMetadata().get("titlePath"),
                    t.substring(0, Math.min(60, t.length())));
        }
    }
}
