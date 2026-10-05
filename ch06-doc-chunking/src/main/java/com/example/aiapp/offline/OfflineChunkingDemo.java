package com.example.aiapp.offline;

import com.example.aiapp.etl.ChunkingExperiments;
import com.example.aiapp.etl.TextCleaner;
import com.example.aiapp.etl.splitter.ClauseTextSplitter;
import com.example.aiapp.etl.splitter.OverlapTextSplitter;
import org.springframework.ai.document.Document;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 离线切块演示：不启动 Spring 上下文，不连 pgvector、不调任何 API，
 * 直接把「解析 -> 清洗 -> 四种切法」的结果打印出来肉眼检查。
 *
 * 运行（在项目根目录）：
 *   mvn compile exec:java -Dexec.mainClass=com.example.aiapp.offline.OfflineChunkingDemo
 * 或直接在 IDE 里运行本类的 main 方法。可传入文件路径参数覆盖默认样例文档。
 *
 * Pinecone 经验法则：一段文本对人类读者能脱离上下文独立成立，对模型也成立——
 * 所以逐块看头尾，比看任何指标都快。
 */
public class OfflineChunkingDemo {

    public static void main(String[] args) throws IOException {
        String path = args.length > 0 ? args[0] : "docs/员工考勤与报销制度.txt";
        String text = Files.readString(Path.of(path), StandardCharsets.UTF_8);

        // 0. 清洗前后 diff 报告：调清洗规则的唯一正道是先打印对比
        TextCleaner cleaner = new TextCleaner();
        Document cleaned = cleaner.clean(new Document(text));
        System.out.println("===== 清洗前（前 300 字） =====");
        System.out.println(text.substring(0, Math.min(300, text.length())));
        System.out.println("===== 清洗后（前 300 字） =====");
        System.out.println(cleaned.getText().substring(0, Math.min(300, cleaned.getText().length())));

        List<Document> docs = List.of(cleaned);
        ChunkingExperiments experiments = new ChunkingExperiments();

        // 1. 递归字符切：200 / 500 / 1000 三档对比（6.5）
        for (int size : new int[]{200, 500, 1000}) {
            experiments.splitAndReport(docs, size);
        }

        // 2. 练习 1：条款结构切——块边界不跨条款，每块以完整条款收尾
        ChunkingExperiments.report("ClauseTextSplitter 条款结构切（上限 500 token）",
                new ClauseTextSplitter(500).apply(docs));

        // 3. 练习 2：带 overlap 的段落切——相邻两块应有约 15% 的重叠段落
        ChunkingExperiments.report("OverlapTextSplitter chunkSize=200 overlap=15%",
                new OverlapTextSplitter(200, 0.15).apply(docs));
    }
}
