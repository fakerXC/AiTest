package com.example.aiapp.etl.splitter;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 动手练习 1：按「第 X 条」条款结构切分的自定义 TextSplitter（结构优先落地）。
 *
 * 思路（对应 unstructured 的 by_title 策略）：
 * 1. 先用正则把全文切成「条款块」——块边界永远不跨条款；
 * 2. 超过 chunkSize 上限的超长条款，交给 TokenTextSplitter 二次切兜底；
 * 3. 条款号元数据由 MetadataEnricher 的 titlePath 提取（其正则同时覆盖「第X章/第X条」）。
 *
 * 基类 TextSplitter 的 apply(List<Document>) 会处理 metadata 的继承与复制，
 * 这里只需专注 splitText。
 */
public class ClauseTextSplitter extends TextSplitter {

    // 前瞻断言：在「第X条」前下刀，且保留下刀标记本身
    private static final Pattern CLAUSE_BOUNDARY =
            Pattern.compile("(?=第[一二三四五六七八九十百0-9]+条)");
    private static final Encoding CL100K =
            Encodings.newLazyEncodingRegistry().getEncoding(EncodingType.CL100K_BASE);

    /** 单块 token 上限：条款本身超长时二次切的目标块长 */
    private final int chunkSize;

    public ClauseTextSplitter(int chunkSize) {
        this.chunkSize = chunkSize;
    }

    @Override
    protected List<String> splitText(String text) {
        // 二次切的兜底切块器：同样按中文调小 minChunkSizeChars
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
                continue;   // 首个「第X条」之前的前言部分若为空则跳过
            }
            if (CL100K.countTokens(trimmed) <= chunkSize) {
                chunks.add(trimmed);
            } else {
                // 超长条款：递归字符二次切，刀口退到句末标点。
                // TokenTextSplitter.splitText 是 protected，跨包只能走公开的 apply 再取回文本
                chunks.addAll(fallback.apply(List.of(new Document(trimmed)))
                        .stream().map(Document::getText).toList());
            }
        }
        return chunks;
    }
}
