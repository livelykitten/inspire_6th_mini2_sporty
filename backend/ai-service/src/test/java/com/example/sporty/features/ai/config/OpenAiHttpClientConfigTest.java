package com.example.sporty.features.ai.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetSocketAddress;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

class OpenAiHttpClientConfigTest {

    @Test
    void delayedResponseTimesOutWithoutCallingOpenAi() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/slow", exchange -> {
            try {
                Thread.sleep(1000);
                exchange.sendResponseHeaders(200, -1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        try {
            RestClient.Builder builder = RestClient.builder();
            new OpenAiHttpClientConfig().openAiRestClientCustomizer(Duration.ofMillis(100))
                    .customize(builder);
            RestClient client = builder.build();

            assertThatThrownBy(() -> client.get()
                    .uri("http://127.0.0.1:" + server.getAddress().getPort() + "/slow")
                    .retrieve().toBodilessEntity())
                    .isInstanceOf(ResourceAccessException.class)
                    .hasRootCauseInstanceOf(HttpTimeoutException.class);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsInfiniteOrNegativeTimeout() {
        OpenAiHttpClientConfig config = new OpenAiHttpClientConfig();
        assertThatThrownBy(() -> config.openAiRestClientCustomizer(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> config.openAiRestClientCustomizer(Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
