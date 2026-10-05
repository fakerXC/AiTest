package com.example.aiapp.etl.splitter;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import org.springframework.ai.transformer.splitter.TextSplitter;

import java.util.ArrayList;
import java.util.List;

/**
 * 动手练习 2：带 overlap（块间重叠）的切块器——补上 TokenTextSplitter 不支持的能力。
 *
 * 算法：按段落（\n\n）贪心装填，塞到 token 数超过 chunkSize 就封块；
 * 封块时把末尾若干段落保留为下一块的开头——保留量按「末尾段落累计 token 数
 * 达到 chunkSize 的 overlapRatio」计算，而不是固定段落个数，
 * 这样段落长短不一时缓冲区厚度才稳定。
 *
 * 验收：相邻两块中，「超标部分需提前经分管副总裁审批」这类边界句应同时完整出现。
 */
public class OverlapTextSplitter extends TextSplitter {

    private static final Encoding CL100K =
            Encodings.newLazyEncodingRegistry().getEncoding(EncodingType.CL100K_BASE);

    private final int chunkSize;
    private final double overlapRatio;   // 建议 0.10 ~ 0.20

    public OverlapTextSplitter(int chunkSize, double overlapRatio) {
        this.chunkSize = chunkSize;
        this.overlapRatio = overlapRatio;
    }

    @Override
    protected List<String> splitText(String text) {
        String[] paragraphs = text.split("\n\n");
        List<String> chunks = new ArrayList<>();
        List<String> current = new ArrayList<>();
        int currentTokens = 0;

        for (String paragraph : paragraphs) {
            int paraTokens = CL100K.countTokens(paragraph);
            if (currentTokens + paraTokens > chunkSize && !current.isEmpty()) {
                chunks.add(String.join("\n\n", current));
                // 封块：从末尾往回保留段落，直到累计 token 达到 overlap 目标
                List<String> carry = new ArrayList<>();
                int carryTokens = 0;
                int overlapTarget = (int) (chunkSize * overlapRatio);
                for (int i = current.size() - 1; i >= 0 && carryTokens < overlapTarget; i--) {
                    carry.add(0, current.get(i));
                    carryTokens += CL100K.countTokens(current.get(i));
                }
                current = carry;
                currentTokens = carryTokens;
            }
            current.add(paragraph);
            currentTokens += paraTokens;
        }
        if (!current.isEmpty()) {
            chunks.add(String.join("\n\n", current));
        }
        return chunks;
    }
}
