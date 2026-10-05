package com.example.aiapp.etl;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * ETL 第三步（Transform 之切块）：TokenTextSplitter 递归字符切 + 诊断打印。
 *
 * 中文场景两个要点：
 * 1. minChunkSizeChars 默认 350 是按英文调的，中文要随 chunkSize 同步调小，
 *    否则小 chunkSize 不生效（块块四五百字）；
 * 2. Spring AI 2.0 起构造器已废弃（forRemoval），统一用 builder 写法。
 *
 * 读诊断输出盯三个信号：块尾是否句末标点收尾、token 分布是否均匀、主题是否跨界。
 */
@Component
public class ChunkingExperiments {

    // TokenTextSplitter 内部同款编码器，用来统计每块真实 token 数
    private static final Encoding CL100K =
            Encodings.newLazyEncodingRegistry().getEncoding(EncodingType.CL100K_BASE);

    /** 用指定 chunkSize 切块并打印诊断信息 */
    public List<Document> splitAndReport(List<Document> docs, int chunkSize) {
        TokenTextSplitter splitter = TokenTextSplitter.builder()
                .withChunkSize(chunkSize)            // 目标块长（token）
                .withMinChunkSizeChars(chunkSize / 2) // 同步调小，否则小 chunkSize 不生效
                .withMinChunkLengthToEmbed(5)
                .withMaxNumChunks(10000)
                .withKeepSeparator(true)
                .build();

        List<Document> chunks = splitter.apply(docs);
        report("TokenTextSplitter chunkSize=" + chunkSize, chunks);
        return chunks;
    }

    /** 打印块数、每块 token 数与头尾——调参必打印，打印必看头尾 */
    public static void report(String label, List<Document> chunks) {
        System.out.printf("%n===== %s -> 共 %d 块 =====%n", label, chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            String text = chunks.get(i).getText();
            int tokens = CL100K.encode(text).size();
            String flat = text.replace("\n", " ");
            String head = flat.substring(0, Math.min(40, flat.length()));
            String tail = flat.substring(Math.max(0, flat.length() - 40));
            System.out.printf("[#%d] %d tokens | 头: %s ... 尾: %s%n", i, tokens, head, tail);
        }
    }
}
