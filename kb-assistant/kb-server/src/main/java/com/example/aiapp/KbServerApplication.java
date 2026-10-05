package com.example.aiapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * P2 企业知识库问答系统 kb-server。
 *
 * 链路：文档上传 -> 解析/清洗/条款切块 -> embedding 入 pgvector + 原文入 ES（双写）
 *      -> 问答：对话记忆 -> 查询改写 -> 向量+BM25 混合召回 -> RRF 融合 -> (可选 rerank)
 *      -> 带引用编号 [n] 的流式生成 -> SSE 推流 -> 流尾追加 sources 事件
 *
 * 评估：mvn spring-boot:run -Dspring-boot.run.profiles=eval 跑一次 golden set 回归评估后退出
 */
@SpringBootApplication
public class KbServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(KbServerApplication.class, args);
    }
}
