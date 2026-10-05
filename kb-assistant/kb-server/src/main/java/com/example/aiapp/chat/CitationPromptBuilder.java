package com.example.aiapp.chat;

import org.springframework.ai.document.Document;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 引用溯源的 prompt 组装：编号的权威来源是服务端拼接的上下文，不是模型的想象。
 * 让模型输出两位数编号而不是文件名——需要程序化解析的东西，就给模型自由度最低的形式。
 */
public class CitationPromptBuilder {

    private static final String SYSTEM = """
            你是企业知识库助手。只能根据下面带编号的资料回答问题，规则：
            1. 答案中凡使用了资料信息的语句，句末必须标注来源编号，格式为 [1]、[2]，可多个连标如 [1][3]；
            2. 编号必须与资料编号严格一致，禁止编造编号；
            3. 资料不足以回答时，直接回复「知识库中未找到相关信息」，不要猜测，也不要标注任何编号；
            4. 用中文简洁作答。
            """;

    /** 把 rerank 后的文档编上号，拼进 prompt；编号即 SourceRef.index */
    public String buildContext(List<Document> docs) {
        return IntStream.range(0, docs.size())
                .mapToObj(i -> "[%d]（来源：%s）\n%s".formatted(
                        i + 1,
                        docs.get(i).getMetadata().get("docName"),
                        docs.get(i).getText()))
                .collect(Collectors.joining("\n\n"));
    }

    public String systemPrompt() {
        return SYSTEM;
    }
}
