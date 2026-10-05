package com.example.aiapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 第 6 章演示工程：文档解析与 Chunking 策略。
 *
 * 运行方式（详见 README.md）：
 *   mvn spring-boot:run                                    入库（512 token）+ 7 问检索对比
 *   mvn spring-boot:run -Dspring-boot.run.arguments=--experiment   200/500/1000 三档对比实验
 *   mvn spring-boot:run -Dspring-boot.run.arguments="--qa=出差住宿超标怎么办？"   检索 + LLM 作答
 */
@SpringBootApplication
public class ChunkingApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChunkingApplication.class, args);
    }
}
