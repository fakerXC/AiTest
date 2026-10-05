package com.example.aiapp.ingest;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 条款结构优先切块（第 6 章练习 1 的结论版）：制度类文档先按「第 X 条」切，
 * 超长条款再交给 TokenTextSplitter 二次切——结构优先，机械切分兜底。
 */
@Component
public class ClauseTextSplitter extends TextSplitter {

    private static final Pattern CLAUSE_BOUNDARY =
            Pattern.compile("(?=第[一二三四五六七八九十百0-9]+条)");
    private static final Encoding CL100K =
            Encodings.newLazyEncodingRegistry().getEncoding(EncodingType.CL100K_BASE);

    private final int chunkSize;

    public ClauseTextSplitter(@org.springframework.beans.factory.annotation.Value("${kb.chunk-size}") int chunkSize) {
        this.chunkSize = chunkSize;
    }

    @Override
    protected List<String> splitText(String text) {
        TokenTextSplitter fallback = TokenTextSplitter.builder()
                .withChunkSize(chunkSize)
                .withMinChunkSizeChars(chunkSize / 2)
                .withMinChunkLengthToEmbed(5)
                .withMaxNumChunks(10000)
                .withKeepSeparator(true)
                .build();

        List<String> chunks = new ArrayList<>();
        for (String clause : CLAUSE_BOUNDARY.split(text)) {
            String trimmed = clause.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (CL100K.countTokens(trimmed) <= chunkSize) {
                chunks.add(trimmed);
            } else {
                // TokenTextSplitter.splitText 是 protected，跨包走公开的 apply 再取回文本
                chunks.addAll(fallback.apply(List.of(new Document(trimmed)))
                        .stream().map(Document::getText).toList());
            }
        }
        return chunks;
    }
}
