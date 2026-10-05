package com.example.aiapp.etl;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * ETL 第四步（Transform 之元数据增强）：给每个 chunk 打标签。
 *
 * docId      用于整篇删除（文档更新时先删后插）；
 * docName / titlePath 用于检索过滤与引用溯源（第 8 章知识库项目的地基）。
 *
 * 进阶玩法：把 titlePath 拼进 chunk 正文开头再 embedding，
 * 即 Anthropic Contextual Retrieval 的规则简化版（教程 6.6 末尾）。
 */
@Component
public class MetadataEnricher {

    private static final Pattern TITLE =
            Pattern.compile("第[一二三四五六七八九十百0-9]+[章条][^，。\\n]{0,20}");

    public List<Document> enrich(List<Document> chunks, String docId, String docName) {
        for (int i = 0; i < chunks.size(); i++) {
            Document chunk = chunks.get(i);
            chunk.getMetadata().put("docId", docId);
            chunk.getMetadata().put("docName", docName);
            chunk.getMetadata().put("chunkIndex", i);
            // 标题路径：从块文本里提取最近一个「第X章/第X条」标题作为归属
            chunk.getMetadata().put("titlePath", extractTitlePath(chunk.getText(), docName));
        }
        return chunks;
    }

    /** 简化版标题提取：找块内第一个章节标题；找不到就归属文档名 */
    private String extractTitlePath(String text, String docName) {
        var matcher = TITLE.matcher(text);
        return matcher.find() ? docName + " > " + matcher.group() : docName;
    }
}
