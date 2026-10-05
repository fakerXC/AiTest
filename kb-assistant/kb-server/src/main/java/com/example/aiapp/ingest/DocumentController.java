package com.example.aiapp.ingest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 文档管理接口：上传（PDF/Word/Markdown/TXT）、列表、删除、重新摄取。
 */
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final IngestService ingestService;
    private final DocumentRegistryService registry;
    private final Path uploadDir;

    public DocumentController(IngestService ingestService,
                              DocumentRegistryService registry,
                              @Value("${kb.upload-dir}") String uploadDir) {
        this.ingestService = ingestService;
        this.registry = registry;
        this.uploadDir = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    public record UploadResult(String docId, String docName, int version, int chunkCount) {}

    /** 上传并摄取。docId 不传时用文件名去后缀（稳定的业务标识，别用文件哈希） */
    @PostMapping("/upload")
    public Mono<Map<String, Object>> upload(@RequestPart("file") FilePart file,
                                            @RequestParam(value = "docId", required = false) String docId,
                                            @RequestParam(value = "docName", required = false) String docName) {
        // multipart 文件名与 URL 参数都可能被按错误编码解码成乱码，统一走修复逻辑
        String finalDocName = (docName != null && !docName.isBlank())
                ? repairMojibake(docName)
                : repairMojibake(file.filename());
        String finalDocId = (docId == null || docId.isBlank())
                ? finalDocName.replaceAll("\\.[^.]+$", "")
                : docId;
        try {
            Files.createDirectories(uploadDir);
            Path target = uploadDir.resolve(finalDocName);
            return file.transferTo(target)
                    .then(Mono.fromSupplier(() -> {
                        int version = ingestService.ingest(target, finalDocId, finalDocName);
                        int chunkCount = registry.list().stream()
                                .filter(d -> d.docId().equals(finalDocId))
                                .map(DocumentRegistryService.DocumentInfo::chunkCount)
                                .findFirst().orElse(0);
                        return Map.<String, Object>of(
                                "docId", finalDocId, "docName", finalDocName,
                                "version", version, "chunkCount", chunkCount);
                    }).subscribeOn(Schedulers.boundedElastic()));
        } catch (Exception e) {
            return Mono.error(e);
        }
    }

    /**
     * 乱码修复：客户端把 UTF-8/GBK 字节按 ISO-8859-1 误读时的还原。
     * 已是正常中文则原样返回；否则还原原始字节后依次按 UTF-8、GBK 重解，取能得到汉字的那个。
     */
    private String repairMojibake(String s) {
        if (s == null || s.chars().anyMatch(this::isHan)) {
            return s;
        }
        byte[] raw = s.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        for (var cs : List.of(java.nio.charset.StandardCharsets.UTF_8,
                              java.nio.charset.Charset.forName("GBK"))) {
            String t = new String(raw, cs);
            if (!t.contains("") && t.chars().anyMatch(this::isHan)) {
                return t;
            }
        }
        return s;
    }

    private boolean isHan(int c) {
        return Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN;
    }

    @GetMapping
    public Mono<List<DocumentRegistryService.DocumentInfo>> list() {
        // JDBC 是阻塞调用，WebFlux 下统一切到弹性线程池
        return Mono.fromCallable(registry::list).subscribeOn(Schedulers.boundedElastic());
    }

    /** 删除：chunk 从两库清除，检索结果里立即消失（验收清单第一条） */
    @DeleteMapping("/{docId}")
    public Mono<Map<String, Object>> delete(@PathVariable String docId) {
        return Mono.fromCallable(() -> {
            ingestService.deleteDocument(docId);
            return Map.<String, Object>of("docId", docId, "deleted", true);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /** 重新摄取：读上传目录里的原文件，触发先删后插，版本号 +1 */
    @PostMapping("/{docId}/reingest")
    public Mono<Map<String, Object>> reingest(@PathVariable String docId) {
        return Mono.fromCallable(() -> {
            var info = registry.list().stream()
                    .filter(d -> d.docId().equals(docId)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("文档不存在: " + docId));
            Path file = uploadDir.resolve(info.name());
            int version = ingestService.ingest(file, docId, info.name());
            return Map.<String, Object>of("docId", docId, "version", version);
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
