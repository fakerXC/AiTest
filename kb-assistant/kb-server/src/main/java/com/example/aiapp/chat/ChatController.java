package com.example.aiapp.chat;

import com.example.aiapp.rag.HybridSearchService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

/**
 * 问答 SSE 接口：对话记忆 -> 混合检索 -> 带引用编号的流式生成。
 * 流式正文先推给前端，流结束后追加 event: sources 携带引用列表
 * （引用要等全文生成完才能解析，这是它无法流式的原因）。
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatClient chatClient;
    private final HybridSearchService searchService;
    private final CitationPromptBuilder promptBuilder = new CitationPromptBuilder();
    private final CitationService citationService;

    public ChatController(ChatClient.Builder builder,
                          ChatMemory chatMemory,
                          HybridSearchService searchService,
                          CitationService citationService) {
        this.searchService = searchService;
        this.citationService = citationService;
        // 记忆 Advisor 挂在 Builder 上，对该 ChatClient 的所有请求生效
        this.chatClient = builder
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    public record ChatRequest(String conversationId, String question) {}

    @PostMapping(path = "/ask", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Object>> ask(@RequestBody ChatRequest req) {
        // 检索是阻塞调用（ES RestClient / pgvector JDBC / embedding HTTP），
        // WebFlux 事件循环线程禁止阻塞，必须切到 boundedElastic 弹性线程池
        return Mono.fromCallable(() -> searchService.search(req.question(), 5))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(docs -> streamAnswer(req, docs));
    }

    private Flux<ServerSentEvent<Object>> streamAnswer(ChatRequest req, List<Document> docs) {
        // 组装引用上下文与系统提示
        String context = promptBuilder.buildContext(docs);

        // 流式生成；同时用 StringBuilder 攒全文，结束后解析引用
        StringBuilder full = new StringBuilder();
        Flux<ServerSentEvent<Object>> chunks = chatClient.prompt()
                .system(promptBuilder.systemPrompt() + "\n\n资料：\n" + context)
                .user(req.question())
                // Spring AI 2.0 起 conversationId 为必填的 advisor 参数
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, req.conversationId()))
                .stream()
                .content()
                .map(delta -> {
                    full.append(delta);
                    return ServerSentEvent.<Object>builder(delta).event("chunk").build();
                });

        // 正文流结束后，追加引用事件与结束事件
        Flux<ServerSentEvent<Object>> tail = Flux.<ServerSentEvent<Object>>create(sink -> {
            try {
                var refs = citationService.extract(full.toString(), docs);
                sink.next(ServerSentEvent.<Object>builder(refs).event("sources").build());
                sink.next(ServerSentEvent.<Object>builder("[DONE]").event("done").build());
                sink.complete();
            } catch (Exception e) {
                sink.next(ServerSentEvent.<Object>builder("引用解析失败: " + e.getMessage())
                        .event("error").build());
                sink.complete();
            }
        });
        return Flux.concat(chunks, tail);
    }
}
