package com.example.aiapp.etl;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * ETL 第一步（Extract）：把文件解析成 Document 列表。
 *
 * PDF/DOCX/PPTX/HTML 走 Tika（自动识别格式）；
 * .txt/.md 直接读文件——别让 Tika 处理纯文本，杀鸡用牛刀还可能引入编码问题。
 */
@Component
public class DocumentLoadService {

    /** 解析任意常见格式，Tika 会把文件名、Content-Type 等放进 metadata */
    public List<Document> load(String filePath) {
        Path path = Path.of(filePath);
        String lower = path.getFileName().toString().toLowerCase();
        if (lower.endsWith(".txt") || lower.endsWith(".md")) {
            return loadPlainText(path, filePath);
        }
        Resource resource = new FileSystemResource(filePath);
        TikaDocumentReader reader = new TikaDocumentReader(resource);
        // 注意：Tika 按文档聚合，整篇通常只返回一个 Document。
        // 需要页码级元数据时换 PagePdfDocumentReader（spring-ai-pdf-document-reader）。
        return reader.read();
    }

    /** 纯文本/Markdown 直读。GBK 编码的老文件把 UTF_8 换成 Charset.forName("GBK") */
    private List<Document> loadPlainText(Path path, String filePath) {
        try {
            String text = Files.readString(path, StandardCharsets.UTF_8);
            return List.of(new Document(text, Map.of("source", filePath)));
        } catch (IOException e) {
            throw new UncheckedIOException("读取文件失败: " + filePath, e);
        }
    }
}
