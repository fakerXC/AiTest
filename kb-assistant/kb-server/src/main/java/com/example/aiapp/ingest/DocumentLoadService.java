package com.example.aiapp.ingest;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 解析（第 6 章复用）：PDF/DOCX/PPTX/HTML 走 Tika；.txt/.md 直读，别让 Tika 杀鸡用牛刀。
 */
@Component
public class DocumentLoadService {

    public List<Document> load(Path path) {
        String lower = path.getFileName().toString().toLowerCase();
        if (lower.endsWith(".txt") || lower.endsWith(".md")) {
            try {
                String text = Files.readString(path, StandardCharsets.UTF_8);
                return List.of(new Document(text, Map.of("source", path.toString())));
            } catch (IOException e) {
                throw new UncheckedIOException("读取文件失败: " + path, e);
            }
        }
        return new TikaDocumentReader(new FileSystemResource(path)).read();
    }
}
