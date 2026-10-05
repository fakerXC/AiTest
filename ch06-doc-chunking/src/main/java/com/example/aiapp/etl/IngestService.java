package com.example.aiapp.etl;

import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * ETL 全链路入库（Load）：解析 -> 清洗 -> 切块 -> 元数据增强 -> 入 pgvector。
 *
 * 两个工程细节（教程 6.7 / 常见坑三、坑六）：
 * 1. 幂等：按 metadata 的 docId 先删后插，重复入库不残留旧块；
 * 2. 解析产出校验：清洗后文本过短（扫描件/解析失败）直接报错，
 *    防止「管线不报错但知识库里这篇文档等于不存在」。
 */
@Service
public class IngestService {

    /** 清洗后正文低于该长度视为解析失败（疑似扫描件/乱码），转人工或 OCR */
    private static final int MIN_CLEANED_LENGTH = 200;

    private final VectorStore vectorStore;
    private final DocumentLoadService loadService;
    private final TextCleaner cleaner;
    private final ChunkingExperiments chunker;
    private final MetadataEnricher enricher;

    public IngestService(VectorStore vectorStore, DocumentLoadService loadService,
                         TextCleaner cleaner, ChunkingExperiments chunker,
                         MetadataEnricher enricher) {
        this.vectorStore = vectorStore;
        this.loadService = loadService;
        this.cleaner = cleaner;
        this.chunker = chunker;
        this.enricher = enricher;
    }

    /** 默认切块策略：TokenTextSplitter（递归字符切） */
    public void ingest(String filePath, String docId, String docName, int chunkSize) {
        ingest(filePath, docId, docName, chunkSize, null);
    }

    /**
     * 完整入库管线：可重复执行（幂等），同一 docId 重复入库不会残留旧块。
     *
     * @param customSplitter 传入自定义切块器（如 ClauseTextSplitter / OverlapTextSplitter）
     *                       可替换默认的 TokenTextSplitter；传 null 用默认
     */
    public void ingest(String filePath, String docId, String docName, int chunkSize,
                       TextSplitter customSplitter) {
        // 1. 先删：按 metadata 过滤删除该文档的全部旧 chunk
        vectorStore.delete(new FilterExpressionBuilder()
                .eq("docId", docId).build());

        // 2. ETL：解析 -> 清洗 -> 切块 -> 元数据增强
        List<Document> docs = loadService.load(filePath);
        List<Document> cleaned = cleaner.cleanAll(docs);
        validateParsedOutput(cleaned, filePath);

        List<Document> chunks = customSplitter != null
                ? customSplitter.apply(cleaned)
                : chunker.splitAndReport(cleaned, chunkSize);
        List<Document> enriched = enricher.enrich(chunks, docId, docName);

        // 3. 入库：PgVectorStore 内部会调 embedding 模型逐块向量化
        vectorStore.add(enriched);
        System.out.printf("文档 %s 入库完成，共 %d 块%n", docName, enriched.size());
    }

    /** 解析产出校验：扫描件抽出来是空文本，切块零块、入库静默成功，必须在这里拦住 */
    private void validateParsedOutput(List<Document> cleaned, String filePath) {
        int totalLength = cleaned.stream().mapToInt(d -> d.getText().length()).sum();
        if (totalLength < MIN_CLEANED_LENGTH) {
            throw new IllegalStateException(
                    "解析产出过短（清洗后共 " + totalLength + " 字，低于下限 " + MIN_CLEANED_LENGTH
                            + "）：" + filePath + " 可能是扫描件或乱码文档，请转 OCR 或检查源文件");
        }
    }
}
