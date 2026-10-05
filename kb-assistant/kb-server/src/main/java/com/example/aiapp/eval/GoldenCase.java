package com.example.aiapp.eval;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** 一条黄金评测样本，对应 JSONL 文件里的一行 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GoldenCase(
        String id,
        String question,
        String goldenAnswer,
        List<String> goldenChunkIds,   // 标准出处 chunk 的 ID 列表
        String type                    // simple / multi-hop / unanswerable
) {}
