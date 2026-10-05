package com.example.aiapp.etl;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * ETL 第二步（Transform 之清洗）：去页眉/页脚/水印 -> 合并硬换行。
 *
 * 三个设计决定（详见教程 6.4）：
 * 1. 空行必须保留——它是段落边界信号，切块器靠它识别自然段落；
 * 2. 合并断行时中文不加空格（中英混排可在这里加「两边都是 ASCII 字母时补空格」的分支）；
 * 3. 清洗规则没有银弹，正则要按真实文档调整——务必打印清洗前后文本对比（diff 报告）。
 */
@Component
public class TextCleaner {

    // 页脚：第 X 页 / 第 X 页 共 Y 页
    private static final Pattern PAGE_FOOTER =
            Pattern.compile("第\\s*\\d+\\s*页(\\s*共\\s*\\d+\\s*页)?");
    // 页眉：公司名行（按你的文档实际页眉调整）
    private static final Pattern HEADER =
            Pattern.compile("^.*科技有限公司.*$");
    // 水印文本
    private static final Pattern WATERMARK =
            Pattern.compile("内部资料[,，]?严禁外传");

    /** 清洗入口：去噪声行 -> 合并硬换行 */
    public Document clean(Document doc) {
        String text = doc.getText();
        text = removeNoiseLines(text);
        text = mergeHardLineBreaks(text);
        // 保留原 metadata（含 Tika 的 Content-Type 等），只替换正文
        return new Document(doc.getId(), text, doc.getMetadata());
    }

    /** 逐行过滤页眉、页脚、水印 */
    private String removeNoiseLines(String text) {
        StringBuilder sb = new StringBuilder();
        for (String line : text.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                sb.append('\n');          // 保留空行：它是段落边界信号
                continue;
            }
            if (PAGE_FOOTER.matcher(trimmed).matches()
                    || HEADER.matcher(trimmed).matches()
                    || WATERMARK.matcher(trimmed).find()) {
                continue;                  // 噪声行，整行丢弃
            }
            sb.append(trimmed).append('\n');
        }
        return sb.toString();
    }

    /**
     * 合并硬换行：一行若以中文/英文且不以句末标点结尾，
     * 说明是 PDF 视觉换行而非自然段落结束，与下一行拼接。
     */
    private String mergeHardLineBreaks(String text) {
        StringBuilder sb = new StringBuilder();
        String[] lines = text.split("\n");
        for (String line : lines) {
            if (line.isEmpty()) {
                sb.append("\n\n");         // 空行 -> 段落分隔，压成统一形式
                continue;
            }
            if (sb.length() > 0 && !endsWithParagraphTerminator(sb)) {
                sb.append(line);           // 上句没完，直接拼接（中文不需要空格）
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
        // 句末标点、换行视为自然断点
        return last == '\n' || "。！？；：.!?;:".indexOf(last) >= 0;
    }

    public List<Document> cleanAll(List<Document> docs) {
        return docs.stream().map(this::clean).toList();
    }
}
