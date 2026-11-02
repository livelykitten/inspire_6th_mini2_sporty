package com.example.sporty.features.ai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.sporty.features.ai.tools.MatchAiTool;
import com.example.sporty.features.ai.tools.MatchDraftAiTool;
import com.example.sporty.features.ai.tools.MatchRecommendAiTool;

@Configuration 
public class AiAgentConfig {
    
    // 메서드명이 Bean 이름 - MatchAiAgent의 @Qualifier 값과 같아야 함
    @Bean
    public ChatClient matchChatClient(ChatClient.Builder builder, MatchAiTool matchAITool) {
        return builder.defaultTools(matchAITool).build();
    }

    // 메서드명이 Bean 이름 - MatchDraftAiAgent의 @Qualifier 값과 같아야 함
    @Bean
    public ChatClient matchDraftChatClient(ChatClient.Builder builder, MatchDraftAiTool matchDraftAiTool) {
        return builder.defaultTools(matchDraftAiTool).build();
    }

    // 메서드명이 Bean 이름 - MatchRecommendAiAgent의 @Qualifier 값과 같아야 함
    @Bean
    public ChatClient matchRecommendChatClient(ChatClient.Builder builder, MatchRecommendAiTool matchRecommendAiTool) {
        return builder.defaultTools(matchRecommendAiTool).build();
    }
}
