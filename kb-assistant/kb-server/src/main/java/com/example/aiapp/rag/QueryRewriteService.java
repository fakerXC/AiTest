package com.example.aiapp.rag;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 查询侧优化（第 7 章第七步）：对话改写 + 多查询扩展。
 * 护栏：原始 query 永远保留一路参与检索；专有名词/编号/错误码字面必须原样保留。
 */
@Service
public class QueryRewriteService {

    private static final String REWRITE_PROMPT = """
            你是一个查询改写器。给定多轮对话历史和用户的最新问题，
            请把最新问题改写成一个「脱离上下文也能独立理解」的完整检索问句。
            要求：
            1. 补全所有指代（这个、那个、它、上面提到的）；
            2. 保留专有名词、编号、错误码的原始字面，不得改写或翻译；
            3. 只输出改写后的问句本身，不要解释，不要加引号。

            对话历史：
            {history}

            最新问题：{question}
            """;

    private static final String EXPAND_PROMPT = """
            你是一个检索查询扩展器。针对下面的用户问题，生成 {n} 个
            表述不同但意图一致的检索问句变体（换同义词、换句式、换提问角度）。
            要求：每行一个变体，不要编号，不要解释，保留专有名词原文。

            用户问题：{question}
            """;

    private final ChatClient chatClient;

    public QueryRewriteService(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    /** 多轮对话改写：把指代不明的追问改写成独立问句；单轮直接短路返回，省一次 LLM 调用 */
    public String rewrite(String question, List<String> history) {
        if (history == null || history.isEmpty()) {
            return question;
        }
        return chatClient.prompt()
                .user(u -> u.text(REWRITE_PROMPT)
                        .param("history", String.join("\n", history))
                        .param("question", question))
                .call()
                .content()
                .trim();
    }

    /** 多查询扩展：一个问题扩出 n 个变体（不含原问题，原问题永远保留一路） */
    public List<String> expand(String question, int n) {
        String content = chatClient.prompt()
                .user(u -> u.text(EXPAND_PROMPT)
                        .param("n", String.valueOf(n))
                        .param("question", question))
                .call()
                .content();
        return content.lines()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .limit(n)
                .toList();
    }
}
