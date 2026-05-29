package com.toswap.toswap.service;

import com.toswap.toswap.exception.BusinessException;
import com.toswap.toswap.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * Gemini AI API 연동 서비스.
 *
 * 역할: TOEIC Speaking 파트별 문제 텍스트(+ Part 2는 이미지 키워드)를 AI로 생성한다.
 * 모델: gemini-2.0-flash (Google AI Studio 무료 티어 지원)
 * 인증: x-goog-api-key 헤더 방식
 *
 * Gemini에게 responseMimeType: "application/json" 으로 JSON 직접 반환을 요청하므로
 * candidates[0].content.parts[0].text 에 JSON 문자열이 담겨온다.
 */
@Slf4j
@Service
public class GeminiService {

    private static final String MODEL = "gemini-2.5-flash";
    private static final String BASE_URL = "https://generativelanguage.googleapis.com";

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public GeminiService(
            WebClient.Builder webClientBuilder,
            @Value("${external.gemini.api-key}") String apiKey,
            ObjectMapper objectMapper) {
        this.webClient = webClientBuilder
                .baseUrl(BASE_URL)
                .defaultHeader("x-goog-api-key", apiKey)
                .build();
        this.objectMapper = objectMapper;
    }

    /**
     * 파트 번호에 맞는 TOEIC Speaking 문제 데이터를 생성한다.
     *
     * @param partId TOEIC Speaking 파트 번호 (1~5)
     * @return content (문제 내용), imageKeyword (Part 2만 존재, 나머지 null)
     */
    public QuestionData generateQuestion(short partId) {
        String prompt = buildPrompt(partId);

        // Gemini API 요청 바디 구성
        GeminiRequest request = new GeminiRequest(
                List.of(new GeminiRequest.Content(
                        List.of(new GeminiRequest.Part(prompt))
                )),
                new GeminiRequest.GenerationConfig("application/json")
        );

        try {
            GeminiApiResponse response = webClient.post()
                    .uri("/v1beta/models/" + MODEL + ":generateContent")
                    .bodyValue(request)
                    .retrieve()
                    // HTTP 오류 응답(4xx/5xx)을 BusinessException으로 변환
                    .onStatus(HttpStatusCode::isError,
                            resp -> Mono.error(new BusinessException(ErrorCode.QUESTION_GENERATION_FAILED)))
                    .bodyToMono(GeminiApiResponse.class)
                    .block();

            if (response == null
                    || response.candidates() == null
                    || response.candidates().isEmpty()) {
                throw new BusinessException(ErrorCode.QUESTION_GENERATION_FAILED);
            }

            // responseMimeType: "application/json" 설정 시 text 필드가 곧 JSON
            String jsonText = response.candidates().get(0).content().parts().get(0).text();
            return parseQuestionData(jsonText);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Gemini API 호출 실패 (partId={}): {}", partId, e.getMessage());
            throw new BusinessException(ErrorCode.QUESTION_GENERATION_FAILED);
        }
    }

    /**
     * Gemini가 반환한 JSON 문자열을 파싱해서 content / imageKeyword 를 꺼낸다.
     */
    private QuestionData parseQuestionData(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            String content = node.path("content").asText();
            // imageKeyword는 Part 2에서만 존재 → 없으면 null
            String imageKeyword = node.has("imageKeyword")
                    ? node.path("imageKeyword").asText(null)
                    : null;
            return new QuestionData(content, imageKeyword);
        } catch (Exception e) {
            log.error("Gemini 응답 JSON 파싱 실패: {}", e.getMessage());
            throw new BusinessException(ErrorCode.QUESTION_GENERATION_FAILED);
        }
    }

    /**
     * 파트별 Gemini 프롬프트.
     * responseMimeType: "application/json" 이므로 반환 형식만 명시하면 된다.
     *
     * Part 1: 읽기용 영어 지문 생성 (비즈니스 공지/광고)
     * Part 2: 사진 묘사 지시문 + 이미지 검색 키워드 생성
     * Part 3: 인터뷰/설문 상황의 질문 1개 생성
     * Part 4: 일정/공고 등 정보 제공 후 질문 생성
     * Part 5: 의견 표현 주제 생성
     */
    private String buildPrompt(short partId) {
        return switch (partId) {
            case 1 -> """
                    You are a TOEIC Speaking test creator.
                    Generate one Part 1 "Read a Text Aloud" question.
                    Write a short English passage (2-3 sentences) from a business context \
                    such as an announcement, advertisement, or news bulletin.
                    The text should be clear and at an intermediate English level.
                    Return JSON: {"content": "the passage text"}
                    """;
            case 2 -> """
                    You are a TOEIC Speaking test creator.
                    Generate one Part 2 "Describe a Picture" question.
                    Write a brief instruction asking the test taker to describe a photograph.
                    Also provide a simple English keyword (1-2 words) for finding a relevant \
                    business or everyday life photo on Unsplash.
                    Return JSON: {"content": "the instruction text", "imageKeyword": "search keyword"}
                    """;
            case 3 -> """
                    You are a TOEIC Speaking test creator.
                    Generate one Part 3 "Respond to Questions" question.
                    Write a natural conversational question in a business or daily life context, \
                    as if asked during a telephone survey or in-person interview.
                    Return JSON: {"content": "the question"}
                    """;
            case 4 -> """
                    You are a TOEIC Speaking test creator.
                    Generate one Part 4 "Respond to Questions Using Information Provided" question.
                    First provide a short schedule, advertisement, or announcement (2-4 lines), \
                    then write a specific question about that information.
                    Return JSON: {"content": "the information and question combined"}
                    """;
            case 5 -> """
                    You are a TOEIC Speaking test creator.
                    Generate one Part 5 "Express an Opinion" question.
                    Present a relatable situation or statement about work or everyday life, \
                    and ask the test taker to express and support their opinion with reasons.
                    Return JSON: {"content": "the opinion prompt"}
                    """;
            default -> throw new BusinessException(ErrorCode.INVALID_PART_ID);
        };
    }

    // ── Gemini API 요청/응답 내부 레코드 ──────────────────────────────────────

    // 요청 바디 구조: {"contents": [{"parts": [{"text": "..."}]}], "generationConfig": {...}}
    record GeminiRequest(List<Content> contents, GenerationConfig generationConfig) {
        record Content(List<Part> parts) {}
        record Part(String text) {}
        record GenerationConfig(String responseMimeType) {}
    }

    // 응답 바디 구조: {"candidates": [{"content": {"parts": [{"text": "..."}]}}]}
    record GeminiApiResponse(List<Candidate> candidates) {
        record Candidate(CandidateContent content) {}
        record CandidateContent(List<CandidatePart> parts) {}
        record CandidatePart(String text) {}
    }

    // ── 파싱 결과 ─────────────────────────────────────────────────────────────

    /** Gemini 응답에서 꺼낸 문제 데이터. imageKeyword 는 Part 2에만 존재. */
    public record QuestionData(String content, String imageKeyword) {}
}
