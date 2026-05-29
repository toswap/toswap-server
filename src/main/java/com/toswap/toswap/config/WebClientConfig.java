package com.toswap.toswap.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * WebClient 설정.
 *
 * Spring Boot 4.x에서는 WebClient.Builder를 자동 등록하지 않으므로 직접 빈으로 등록한다.
 * prototype 스코프: 주입받는 곳마다 새 인스턴스 → GeminiService, UnsplashService가 각각 독립적으로 설정 가능.
 */
@Configuration
public class WebClientConfig {

    @Bean
    @Scope("prototype")
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
}
