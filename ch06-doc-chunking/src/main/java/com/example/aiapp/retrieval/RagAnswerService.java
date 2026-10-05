package com.example.aiapp.retrieval;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

/**
 * 检索验证环节：把命中的 chunk（正文 + titlePath 元数据）拼进 prompt，
 * 让模型拿到「贴好标签的资料」而不是「来路不明的碎片」（教程 6.6 的组装流程）。
 *
 * chunk size 对「答案质量」的影响在这一步最直观：
 * 小块命中准但上下文可能残缺，大块上下文全但信噪比下降。
 */
@Service
public class RagAnswerService {

    private static final String PROMPT_TEMPLATE = """
            基于以下背景资料回答问题，回答末尾注明依据的条款来源。
            如果资料不足以回答，直接说「资料中没有相关内容」，不要编造。

            【背景资料】
            {context}

            【问题】
            {question}
            """;

    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    public RagAnswerService(ChatClient.Builder chatClientBuilder, VectorStore vectorStore) {
        this.chatClient = chatClientBuilder.build();
        this.vectorStore = vectorStore;
    }

    public String answer(String question) {
        var hits = vectorStore.similaritySearch(SearchRequest.builder()
                .query(question)
                .topK(3)
                .similarityThreshold(0.5)
                .build());

        StringBuilder context = new StringBuilder();
        for (var hit : hits) {
            context.append("来源：")
                    .append(hit.getMetadata().getOrDefault("titlePath", "未知"))
                    .append('\n')
                    .append("内容：").append(hit.getText())
                    .append("\n\n");
        }

        return chatClient.prompt()
                .user(u -> u.text(PROMPT_TEMPLATE)
                        .param("context", context.toString())
                        .param("question", question))
                .call()
                .content();
    }
}
