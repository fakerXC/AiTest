package com.example.aiapp.chat;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 把答案中的 [n] 映射回 chunk，生成引用列表。
 * 解析永远容错：越界编号静默丢弃，引用宁可少标不可错标。
 */
@Component
public class CitationService {

    private static final Pattern CITATION = Pattern.compile("\\[(\\d{1,2})]");

    /**
     * @param answer 模型生成的完整答案（含 [n] 标注）
     * @param docs   本次问答实际塞进 prompt 的检索结果（顺序即编号）
     */
    public List<SourceRef> extract(String answer, List<Document> docs) {
        Matcher m = CITATION.matcher(answer);
        Map<Integer, SourceRef> refs = new LinkedHashMap<>(); // 保持出现顺序、自动去重
        while (m.find()) {
            int idx = Integer.parseInt(m.group(1));
            if (idx < 1 || idx > docs.size()) {
                continue; // 模型编了不存在的编号：静默丢弃，绝不让前端炸掉
            }
            refs.computeIfAbsent(idx, i -> {
                Document d = docs.get(i - 1);
                String text = d.getText() == null ? "" : d.getText();
                return new SourceRef(i,
                        String.valueOf(d.getMetadata().get("docName")),
                        String.valueOf(d.getMetadata().get("titlePath")),
                        String.valueOf(d.getMetadata().get("chunkId")),
                        text.substring(0, Math.min(120, text.length())));
            });
        }
        return new ArrayList<>(refs.values());
    }
}
