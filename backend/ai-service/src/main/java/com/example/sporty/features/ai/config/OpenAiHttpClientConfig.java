package com.example.sporty.features.ai.config;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;

@Configuration
public class OpenAiHttpClientConfig {

    // Spring AI 1.0.0 uses the Boot RestClient.Builder for synchronous OpenAI calls.
    @Bean
    public RestClientCustomizer openAiRestClientCustomizer(
            @Value("${ai.openai.timeout:10s}") Duration timeout) {
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("OpenAI timeout must be positive");
        }
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(timeout);
        return builder -> builder.requestFactory(requestFactory);
    }
}
