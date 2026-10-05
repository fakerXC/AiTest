package com.example.aiapp.eval;

import com.example.aiapp.rag.HybridSearchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 检索评估入口。启动方式：
 *   mvn spring-boot:run -Dspring-boot.run.profiles=eval
 * 跑一次评估后退出；报告同时打印到控制台并写入 eval-reports/ 目录（带时间戳，提交 git 留档）。
 */
@Component
@Profile("eval") // 平时启动应用不会触发评估
public class RetrievalEvalRunner implements CommandLineRunner {

    private final GoldenSetLoader loader;
    private final HybridSearchService searchService; // 与线上问答同一个检索 Bean

    @Value("classpath:eval/golden-set.jsonl")
    private Resource goldenSetResource;

    public RetrievalEvalRunner(GoldenSetLoader loader, HybridSearchService searchService) {
        this.loader = loader;
        this.searchService = searchService;
    }

    @Override
    public void run(String... args) throws Exception {
        List<GoldenCase> goldenSet = loader.load(goldenSetResource);
        RetrievalEvaluator evaluator = new RetrievalEvaluator();

        // 同一套评估器，注入不同的检索实现，就能对比「优化前后」
        var report = evaluator.evaluate(goldenSet, q -> searchService.search(q, 10), 5);

        System.out.printf("%n===== 检索评估报告 =====%n样本数: %d（可答 %d / 拒答 %d）%nRecall@5: %.3f%nMRR: %.3f%n",
                report.total(), report.answerable(), report.unanswerable(),
                report.recallAtK(), report.mrr());
        // 打印得分垫底的 10 道题，它们是优化的第一手线索
        report.details().stream()
                .sorted((a, b) -> Double.compare(a.recall(), b.recall()))
                .limit(10)
                .forEach(d -> System.out.printf("  低分样本 %s recall=%.2f rr=%.2f%n",
                        d.caseId(), d.recall(), d.reciprocalRank()));

        // 报告落盘：eval-reports/ 目录，带时间戳与检索配置摘要
        Path dir = Path.of("eval-reports");
        Files.createDirectories(dir);
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path out = dir.resolve("retrieval-eval-" + ts + ".json");
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(out.toFile(), Map.of(
                "timestamp", ts,
                "retriever", "HybridSearchService（向量+BM25+RRF，rerank 见 kb.rerank.provider）",
                "k", 5,
                "total", report.total(),
                "answerable", report.answerable(),
                "unanswerable", report.unanswerable(),
                "recallAt5", report.recallAtK(),
                "mrr", report.mrr(),
                "details", report.details()));
        System.out.println("报告已写入: " + out.toAbsolutePath());
    }
}
