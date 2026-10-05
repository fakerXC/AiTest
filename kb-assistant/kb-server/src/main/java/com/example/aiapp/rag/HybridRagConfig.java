package com.example.aiapp.rag;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.preretrieval.query.transformation.CompressionQueryTransformer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 第 7 章第八步：挂进 Spring AI 2.0 的模块化 RAG（备选链路）。
 *
 * 注意：P2 的问答主链路走的是 chat 包里的手动管线（需要拿到 docs 做引用溯源），
 * 本类提供的是 Spring AI 官方 advisor 形态的对照实现，可用于不需要引用编号的场景。
 *
 * 细节：CompressionQueryTransformer 内部要调 LLM 做改写，必须传 mutate() 派生的
 * Builder——直接把挂了 advisor 的 ChatClient 传给它会造成「改写又触发检索」的套娃。
 */
@Configuration
public class HybridRagConfig {

    @Bean
    public Advisor hybridRagAdvisor(ChatClient.Builder builder,
                                    HybridDocumentRetriever hybridRetriever) {
        return RetrievalAugmentationAdvisor.builder()
                // 检索前：把多轮对话中的指代追问压缩成独立问句
                .queryTransformers(CompressionQueryTransformer.builder()
                        .chatClientBuilder(builder.build().mutate())
                        .build())
                // 检索：混合检索 + rerank
                .documentRetriever(hybridRetriever)
                // 检索为空时允许模型兜底回答
                .queryAugmenter(ContextualQueryAugmenter.builder()
                        .allowEmptyContext(true)
                        .build())
                .build();
    }

    @Bean
    public ChatClient kbChatClient(ChatClient.Builder builder, Advisor hybridRagAdvisor) {
        return builder.defaultAdvisors(hybridRagAdvisor).build();
    }
}
