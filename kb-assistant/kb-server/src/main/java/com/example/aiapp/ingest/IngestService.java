package com.example.aiapp.ingest;

import com.example.aiapp.rag.EsChunkIndexService;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 摄取管线（第 6 章管线 + 第 7 章双写 + 第 8 章版本号）：
 * 解析 -> 清洗 -> 条款结构切块 -> 元数据增强（含确定性 chunkId）->
 * 先删后插双写 pgvector 与 ES，登记版本号。
 *
 * 幂等：同一 docId 重复摄取不会残留旧块。
 */
@Service
public class IngestService {

    private static final int MIN_CLEANED_LENGTH = 200;   // 扫描件/解析失败校验（第 6 章坑六）
    private static final Pattern TITLE =
            Pattern.compile("第[一二三四五六七八九十百0-9]+[章条][^，。\\n]{0,20}");

    private final VectorStore vectorStore;
    private final DocumentLoadService loadService;
    private final TextCleaner cleaner;
    private final ClauseTextSplitter splitter;
    private final EsChunkIndexService esIndex;
    private final DocumentRegistryService registry;

    public IngestService(VectorStore vectorStore, DocumentLoadService loadService,
                         TextCleaner cleaner, ClauseTextSplitter splitter,
                         EsChunkIndexService esIndex, DocumentRegistryService registry) {
        this.vectorStore = vectorStore;
        this.loadService = loadService;
        this.cleaner = cleaner;
        this.splitter = splitter;
        this.esIndex = esIndex;
        this.registry = registry;
    }

    /** 摄取一个文件，返回新版本号 */
    public int ingest(Path file, String docId, String docName) {
        // 1. 先删旧（向量库 + ES 双侧）
        deleteChunks(docId);

        // 2. 解析 -> 清洗 -> 校验
        List<Document> cleaned = cleaner.cleanAll(loadService.load(file));
        int totalLength = cleaned.stream().mapToInt(d -> d.getText().length()).sum();
        if (totalLength < MIN_CLEANED_LENGTH) {
            throw new IllegalStateException(
                    "解析产出过短（" + totalLength + " 字），可能是扫描件或乱码文档：" + file);
        }

        // 3. 切块 + 元数据增强（chunkId 确定性：docId#c%03d，评估与引用溯源都靠它）
        List<Document> chunks = splitter.apply(cleaned);
        for (int i = 0; i < chunks.size(); i++) {
            Document chunk = chunks.get(i);
            var md = chunk.getMetadata();
            md.put("docId", docId);
            md.put("docName", docName);
            md.put("chunkIndex", i);
            md.put("chunkId", docId + "#c" + String.format("%03d", i));
            md.put("titlePath", extractTitlePath(chunk.getText(), docName));
        }

        // 4. 双写：pgvector（向量检索）+ ES（BM25）
        vectorStore.add(chunks);
        esIndex.indexChunks(chunks.stream()
                .map(c -> new EsChunkIndexService.ChunkRecord(
                        String.valueOf(c.getMetadata().get("chunkId")), docId, docName,
                        String.valueOf(c.getMetadata().get("titlePath")), c.getText()))
                .toList());

        // 5. 登记版本（先删后插完成后版本 +1）
        int version = registry.registerOrBump(docId, docName, chunks.size());
        System.out.printf("文档 %s 摄取完成：v%d，共 %d 块%n", docName, version, chunks.size());
        return version;
    }

    /** 删除文档的全部 chunk（向量库 + ES + 登记表） */
    public void deleteDocument(String docId) {
        deleteChunks(docId);
        registry.remove(docId);
    }

    private void deleteChunks(String docId) {
        vectorStore.delete(new FilterExpressionBuilder().eq("docId", docId).build());
        esIndex.deleteByDocId(docId);
    }

    private String extractTitlePath(String text, String docName) {
        var matcher = TITLE.matcher(text);
        return matcher.find() ? docName + " > " + matcher.group() : docName;
    }
}
