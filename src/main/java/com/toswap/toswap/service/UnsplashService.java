package com.toswap.toswap.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

/**
 * Unsplash API 연동 서비스.
 *
 * 역할: Part 2 (사진 묘사) 문제에 사용할 이미지 URL을 키워드로 검색한다.
 * 인증: Authorization: Client-ID {accessKey} 헤더 방식
 *
 * 검색 실패 시 null 반환 → 이미지 없이도 문제 생성을 계속 진행 (graceful degradation).
 * Unsplash 무료 티어 제한: 시간당 50회 요청.
 */
@Slf4j
@Service
public class UnsplashService {

    private static final String BASE_URL = "https://api.unsplash.com";

    private final WebClient webClient;

    public UnsplashService(
            WebClient.Builder webClientBuilder,
            @Value("${external.unsplash.api-key}") String accessKey) {
        this.webClient = webClientBuilder
                .baseUrl(BASE_URL)
                .defaultHeader("Authorization", "Client-ID " + accessKey)
                .build();
    }

    /**
     * 키워드로 Unsplash 이미지를 검색하고 첫 번째 결과의 regular URL을 반환한다.
     * regular 사이즈: 가로 1080px 수준으로 화면 표시에 적합.
     *
     * @return 이미지 URL, 검색 실패 시 null
     */
    public String searchImage(String keyword) {
        try {
            UnsplashSearchResponse response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/search/photos")
                            .queryParam("query", keyword)
                            .queryParam("per_page", 1)
                            .queryParam("orientation", "landscape") // 가로형 사진이 묘사 문제에 적합
                            .build())
                    .retrieve()
                    .bodyToMono(UnsplashSearchResponse.class)
                    .block();

            if (response == null
                    || response.results() == null
                    || response.results().isEmpty()) {
                return null;
            }

            return response.results().get(0).urls().regular();

        } catch (Exception e) {
            // 이미지 검색 실패해도 문제 생성은 계속 진행
            log.warn("Unsplash 이미지 검색 실패 (keyword={}): {}", keyword, e.getMessage());
            return null;
        }
    }

    // ── Unsplash 검색 API 응답 내부 레코드 ───────────────────────────────────
    // GET /search/photos 응답: {"results": [{"urls": {"regular": "..."}}]}

    record UnsplashSearchResponse(List<UnsplashPhoto> results) {
        record UnsplashPhoto(UnsplashUrls urls) {}
        record UnsplashUrls(String regular) {}
    }
}
