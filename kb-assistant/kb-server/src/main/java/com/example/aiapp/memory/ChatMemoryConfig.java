package com.example.aiapp.memory;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatMemoryConfig {

    /**
     * 消息窗口记忆：每会话最多保留最近 20 条，防止上下文无限膨胀撑爆 token。
     * ChatMemoryRepository 由 starter 自动装配（JdbcChatMemoryRepository，
     * 表结构由 spring.ai.chat.memory.repository.jdbc.initialize-schema 自动创建）。
     */
    @Bean
    public ChatMemory chatMemory(ChatMemoryRepository repo) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(repo)
                .maxMessages(20)
                .build();
    }
}
