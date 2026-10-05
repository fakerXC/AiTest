package com.example.aiapp.ingest;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 清洗（第 6 章复用）：去页眉/页脚/水印 -> 合并硬换行。
 * 空行必须保留（段落边界信号）；合并断行时中文不加空格。
 */
@Component
public class TextCleaner {

    private static final Pattern PAGE_FOOTER =
            Pattern.compile("第\\s*\\d+\\s*页(\\s*共\\s*\\d+\\s*页)?");
    private static final Pattern HEADER =
            Pattern.compile("^.*科技有限公司.*$");
    private static final Pattern WATERMARK =
            Pattern.compile("内部资料[,，]?严禁外传");

    public Document clean(Document doc) {
        String text = doc.getText();
        text = removeNoiseLines(text);
        text = mergeHardLineBreaks(text);
        return new Document(doc.getId(), text, doc.getMetadata());
    }

    private String removeNoiseLines(String text) {
        StringBuilder sb = new StringBuilder();
        for (String line : text.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                sb.append('\n');
                continue;
            }
            if (PAGE_FOOTER.matcher(trimmed).matches()
                    || HEADER.matcher(trimmed).matches()
                    || WATERMARK.matcher(trimmed).find()) {
                continue;
            }
            sb.append(trimmed).append('\n');
        }
        return sb.toString();
    }

    private String mergeHardLineBreaks(String text) {
        StringBuilder sb = new StringBuilder();
        for (String line : text.split("\n")) {
            if (line.isEmpty()) {
                sb.append("\n\n");
                continue;
            }
            if (sb.length() > 0 && !endsWithParagraphTerminator(sb)) {
                sb.append(line);
            } else {
                if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') {
                    sb.append('\n');
                }
                sb.append(line);
            }
        }
        return sb.toString();
    }

    private boolean endsWithParagraphTerminator(StringBuilder sb) {
        if (sb.length() == 0) return false;
        char last = sb.charAt(sb.length() - 1);
        return last == '\n' || "。！？；：.!?;:".indexOf(last) >= 0;
    }

    public List<Document> cleanAll(List<Document> docs) {
        return docs.stream().map(this::clean).toList();
    }
}
