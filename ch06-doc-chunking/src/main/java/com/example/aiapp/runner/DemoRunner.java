package com.example.aiapp.runner;

import com.example.aiapp.etl.DocumentLoadService;
import com.example.aiapp.etl.IngestService;
import com.example.aiapp.etl.TextCleaner;
import com.example.aiapp.retrieval.ParentChildService;
import com.example.aiapp.retrieval.RagAnswerService;
import com.example.aiapp.retrieval.RetrievalComparator;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 演示入口，按命令行参数选择模式：
 *
 *   （无参数）              入库（demo.chunk-size 档）+ 7 问检索对比
 *   --experiment           6.8 三档实验：200/500/1000 串行，每档先删后插再跑 7 问
 *   --qa=问题              入库后用 DeepSeek 基于检索结果作答
 *   --parent-child         练习 3A：父子检索入库 + 演示一次「命中子块换父块」
 */
@Component
public class DemoRunner implements ApplicationRunner {

    private final IngestService ingestService;
    private final RetrievalComparator comparator;
    private final RagAnswerService qaService;
    private final ParentChildService parentChildService;
    private final DocumentLoadService loadService;
    private final TextCleaner cleaner;

    @Value("${demo.file-path}")
    private String filePath;
    @Value("${demo.doc-id}")
    private String docId;
    @Value("${demo.doc-name}")
    private String docName;
    @Value("${demo.chunk-size}")
    private int chunkSize;

    public DemoRunner(IngestService ingestService, RetrievalComparator comparator,
                      RagAnswerService qaService, ParentChildService parentChildService,
                      DocumentLoadService loadService, TextCleaner cleaner) {
        this.ingestService = ingestService;
        this.comparator = comparator;
        this.qaService = qaService;
        this.parentChildService = parentChildService;
        this.loadService = loadService;
        this.cleaner = cleaner;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (args.containsOption("experiment")) {
            runChunkSizeExperiment();
        } else if (args.containsOption("parent-child")) {
            runParentChild();
        } else if (args.containsOption("qa")) {
            String question = args.getOptionValues("qa").get(0);
            ingestService.ingest(filePath, docId, docName, chunkSize);
            System.out.printf("%n>>> 问题：%s%n%s%n", question, qaService.answer(question));
        } else {
            ingestService.ingest(filePath, docId, docName, chunkSize);
            comparator.compareAll();
        }
    }

    /** 6.8 实验：三档复用同一 docId 串行跑——不要图省事用三个 docId 同时入库，会跨批次失真 */
    private void runChunkSizeExperiment() {
        for (int size : new int[]{200, 500, 1000}) {
            System.out.printf("%n########## chunkSize=%d ##########%n", size);
            ingestService.ingest(filePath, docId, docName, size);
            comparator.compareAll();
        }
    }

    /** 练习 3A：父子检索——子块进向量库负责命中，父块存关系表负责喂模型 */
    private void runParentChild() {
        List<Document> cleaned = cleaner.cleanAll(loadService.load(filePath));
        parentChildService.ingest(cleaned, docId, docName);

        String question = "出差住宿超标怎么办？";
        System.out.printf("%n>>> 问题：%s%n", question);
        List<String> parentContexts = parentChildService.retrieveParentContexts(question, 3);
        System.out.printf("%n换回父块 %d 个，拼入 prompt 的内容：%n", parentContexts.size());
        for (String parent : parentContexts) {
            System.out.println("---- 父块 ----");
            System.out.println(parent);
        }
    }
}
