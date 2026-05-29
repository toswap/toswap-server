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

import java.util.ArrayList;
import java.util.List;

/**
 * Gemini AI API 연동 서비스.
 *
 * ── 파트별 호출 방식 ────────────────────────────────────────────────────────
 *
 * Part 1/2: generateQuestion(partId) 호출
 *   → Gemini가 질문 1개 생성 → QuestionData(content, imageKeyword) 반환
 *
 * Part 3/4/5: generateQuestionGroup(partId) 호출
 *   → Gemini가 공통 배경(contextContent) + 질문 2~3개를 한 번에 생성
 *   → QuestionGroupData(contextContent, List<QuestionItemData>) 반환
 *
 * ── Gemini 응답 방식 ────────────────────────────────────────────────────────
 *   responseMimeType: "application/json" 으로 설정하면
 *   candidates[0].content.parts[0].text 에 JSON 문자열이 직접 담겨온다.
 *   별도 마크다운(```json```) 파싱 없이 바로 objectMapper.readTree() 사용 가능.
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

    // ══════════════════════════════════════════════════════════════════════════
    // Part 1/2: 독립 문제 1개 생성
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Part 1/2 용 독립 문제를 생성한다.
     *
     * @param partId 1 또는 2
     * @return content(문제 텍스트), imageKeyword(Part 2만 존재)
     */
    public QuestionData generateQuestion(short partId) {
        String prompt = buildSinglePrompt(partId);
        String jsonText = callGemini(prompt, partId);
        return parseSingleQuestion(jsonText);
    }

    private QuestionData parseSingleQuestion(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            String content = node.path("content").asText();
            String imageKeyword = node.has("imageKeyword")
                    ? node.path("imageKeyword").asText(null)
                    : null;
            return new QuestionData(content, imageKeyword);
        } catch (Exception e) {
            log.error("Gemini 단일 문제 JSON 파싱 실패: {}", e.getMessage());
            throw new BusinessException(ErrorCode.QUESTION_GENERATION_FAILED);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Part 3/4/5: 그룹 문제 생성 (공통 배경 + 질문 목록)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Part 3/4/5 용 그룹 문제를 생성한다. Gemini를 한 번만 호출해서
     * 공통 배경과 질문 2~3개를 한꺼번에 받아온다.
     *
     * @param partId 3, 4, 또는 5
     * @return contextContent(공통 배경) + questions(질문 목록)
     */
    public QuestionGroupData generateQuestionGroup(short partId) {
        String prompt = buildGroupPrompt(partId);
        String jsonText = callGemini(prompt, partId);
        return parseGroupQuestion(jsonText, partId);
    }

    private QuestionGroupData parseGroupQuestion(String json, short partId) {
        try {
            JsonNode node = objectMapper.readTree(json);
            String contextContent = node.path("contextContent").asText();

            JsonNode questionsNode = node.path("questions");
            List<QuestionItemData> questions = new ArrayList<>();
            for (JsonNode q : questionsNode) {
                questions.add(new QuestionItemData(q.path("content").asText()));
            }

            // 파트별 기대 질문 수 검증
            int expectedCount = (partId == 5) ? 2 : 3;
            if (questions.size() != expectedCount) {
                log.warn("Gemini 그룹 문제 개수 불일치: partId={}, expected={}, actual={}",
                        partId, expectedCount, questions.size());
                // 불일치해도 서비스는 유지 (부분적으로 사용)
            }

            return new QuestionGroupData(contextContent, questions);
        } catch (Exception e) {
            log.error("Gemini 그룹 문제 JSON 파싱 실패 (partId={}): {}", partId, e.getMessage());
            throw new BusinessException(ErrorCode.QUESTION_GENERATION_FAILED);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Gemini API 공통 호출
    // ══════════════════════════════════════════════════════════════════════════

    private String callGemini(String prompt, short partId) {
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
                    .onStatus(HttpStatusCode::isError,
                            resp -> Mono.error(new BusinessException(ErrorCode.QUESTION_GENERATION_FAILED)))
                    .bodyToMono(GeminiApiResponse.class)
                    .block();

            if (response == null
                    || response.candidates() == null
                    || response.candidates().isEmpty()) {
                throw new BusinessException(ErrorCode.QUESTION_GENERATION_FAILED);
            }

            return response.candidates().get(0).content().parts().get(0).text();

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Gemini API 호출 실패 (partId={}): {}", partId, e.getMessage());
            throw new BusinessException(ErrorCode.QUESTION_GENERATION_FAILED);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 프롬프트 빌더
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Part 1/2: 독립 문제 1개를 위한 프롬프트.
     */
    private String buildSinglePrompt(short partId) {
        return switch (partId) {
            case 1 -> """
                    You are a TOEIC Speaking test creator.
                    Generate one Part 1 "Read a Text Aloud" passage.

                    Requirements:
                    - 2~3 sentences in length
                    - Business context: announcement, advertisement, or news bulletin
                    - Clear language at an intermediate English level
                    - Suitable for reading aloud (no complex symbols or abbreviations)

                    Return JSON: {"content": "the passage text"}
                    """;

            case 2 -> """
                    You are a TOEIC Speaking test creator.
                    Generate one Part 2 "Describe a Picture" question.

                    Requirements:
                    - Write a brief instruction asking the test taker to describe a photograph
                    - The photo should depict a real-life or business scene
                    - Provide a simple English keyword (1~2 words) to search a relevant photo on Unsplash

                    Return JSON: {"content": "the instruction text", "imageKeyword": "search keyword"}
                    """;

            default -> throw new BusinessException(ErrorCode.INVALID_PART_ID);
        };
    }

    /**
     * Part 3/4/5: 그룹 문제 세트를 위한 프롬프트.
     *
     * 각 파트의 실제 TOEIC Speaking 시험 구조를 정확히 재현하도록 프롬프트를 설계했다.
     */
    private String buildGroupPrompt(short partId) {
        return switch (partId) {

            /*
             * Part 3 - Respond to Questions
             *
             * 실제 시험 구조:
             *   시험관이 응시자에게 "당신이 [주제]에 대한 설문에 응하고 있다고 상상하세요."라고 안내한다.
             *   그 뒤 3개의 질문을 연속으로 묻는다.
             *   Q1/Q2는 답변 15초, Q3는 더 상세한 설명이 필요하여 답변 30초.
             *
             * 프롬프트 설계 의도:
             *   - contextContent: 자연스러운 서베이 상황 안내문 (응시자에게 읽어주는 형태)
             *   - Q1: 단순 사실/선호도 (예: "How often do you...?")
             *   - Q2: 경험/습관 (예: "What do you usually...?")
             *   - Q3: 이유 설명 또는 비교 (예: "Why do you prefer...? Please explain in detail.")
             */
            case 3 -> """
                    You are a TOEIC Speaking test creator.
                    Generate one Part 3 "Respond to Questions" set.

                    Structure:
                    - contextContent: An introduction sentence telling the test taker to imagine
                      they are being interviewed for a survey about a specific everyday or
                      business topic (e.g., travel, shopping, work habits, technology use).
                      Format: "Imagine you are talking on the phone with someone who is
                      conducting a survey about [topic]."
                    - questions: Exactly 3 questions in increasing difficulty
                        questions[0]: Simple preference or factual question (15-second response)
                        questions[1]: Question about habits or personal experience (15-second response)
                        questions[2]: Question requiring detailed explanation or comparison
                                      (30-second response) — include "Please explain." or
                                      "Please give details." at the end

                    Return JSON:
                    {
                      "contextContent": "Imagine you are talking on the phone...",
                      "questions": [
                        {"content": "Q1 text"},
                        {"content": "Q2 text"},
                        {"content": "Q3 text with explanation request"}
                      ]
                    }
                    """;

            /*
             * Part 4 - Respond to Questions Using Information Provided
             *
             * 실제 시험 구조:
             *   응시자에게 표/일정표/광고/공지 등의 문서가 주어진다.
             *   문서를 보면서 3개의 질문에 답한다.
             *   Q1/Q2는 문서에서 직접 찾을 수 있는 단순 조회 (15초),
             *   Q3는 여러 정보를 종합해서 추론하거나 계산이 필요 (30초).
             *
             * 프롬프트 설계 의도:
             *   - contextContent: 실제 시험처럼 텍스트 포맷의 문서
             *     (이벤트 일정, 상품 비교표, 가게 안내, 여행 일정 등)
             *   - Q1: 문서에서 바로 찾을 수 있는 단순 사실
             *   - Q2: 또 다른 단순 조회 또는 간단한 추론
             *   - Q3: 여러 항목을 비교하거나 조건에 맞는 답을 찾는 복합 추론
             */
            case 4 -> """
                    You are a TOEIC Speaking test creator.
                    Generate one Part 4 "Respond to Questions Using Information Provided" set.

                    Structure:
                    - contextContent: A realistic text-based document. Choose one type:
                        * Event or conference schedule (with time, venue, speaker)
                        * Product comparison table (with features, prices)
                        * Store or restaurant announcement (hours, services, promotions)
                        * Travel or tour itinerary (dates, locations, activities)
                      Format the document clearly with line breaks for readability.
                      The document must contain enough detail to support 3 meaningful questions.
                    - questions: Exactly 3 questions
                        questions[0]: Simple lookup — answer directly found in the document
                                      (15-second response)
                        questions[1]: Another simple lookup or basic inference
                                      (15-second response)
                        questions[2]: Complex question requiring combining 2+ pieces of
                                      information or reasoning about conditions
                                      (30-second response)

                    Return JSON:
                    {
                      "contextContent": "the document text with line breaks",
                      "questions": [
                        {"content": "Q1 simple lookup"},
                        {"content": "Q2 another lookup"},
                        {"content": "Q3 complex reasoning question"}
                      ]
                    }
                    """;

            /*
             * Part 5 - Express an Opinion
             *
             * 실제 시험 구조:
             *   응시자에게 사회적/직장 관련 주제나 상황이 제시된다.
             *   2개의 질문에 각각 의견을 표현한다 (준비 45초, 답변 60초).
             *   Q1은 주 의견, Q2는 더 심층적인 의견 (이유, 반론, 결론 등).
             *
             * 프롬프트 설계 의도:
             *   - contextContent: 명확한 관점을 요구하는 상황 설명
             *   - Q1: "Do you agree or disagree...?" 또는 "What do you think...?"
             *   - Q2: Q1의 연장선에서 더 구체적인 근거/예시/비교를 요구
             */
            case 5 -> """
                    You are a TOEIC Speaking test creator.
                    Generate one Part 5 "Express an Opinion" set.

                    Structure:
                    - contextContent: A situation statement about a workplace or social topic
                      that invites a clear opinion. Should be 1~2 sentences.
                      Examples of topics: remote work, AI in the workplace, work-life balance,
                      online vs in-person education, social media's effect on communication.
                    - questions: Exactly 2 questions
                        questions[0]: Main opinion question — ask the test taker to agree or
                                      disagree, or express their view with supporting reasons
                                      (60-second response). End with "Please give reasons."
                        questions[1]: A deeper follow-up — ask for specific examples, potential
                                      drawbacks, future implications, or comparison with
                                      alternatives (60-second response).
                                      End with "Please explain in detail."

                    Return JSON:
                    {
                      "contextContent": "the situation statement",
                      "questions": [
                        {"content": "Q1 main opinion question"},
                        {"content": "Q2 deeper follow-up question"}
                      ]
                    }
                    """;

            default -> throw new BusinessException(ErrorCode.INVALID_PART_ID);
        };
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Gemini API 요청/응답 내부 레코드
    // ══════════════════════════════════════════════════════════════════════════

    record GeminiRequest(List<Content> contents, GenerationConfig generationConfig) {
        record Content(List<Part> parts) {}
        record Part(String text) {}
        record GenerationConfig(String responseMimeType) {}
    }

    record GeminiApiResponse(List<Candidate> candidates) {
        record Candidate(CandidateContent content) {}
        record CandidateContent(List<CandidatePart> parts) {}
        record CandidatePart(String text) {}
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 파싱 결과 공개 레코드 (QuestionService에서 사용)
    // ══════════════════════════════════════════════════════════════════════════

    /** Part 1/2 Gemini 결과. imageKeyword는 Part 2에만 존재. */
    public record QuestionData(String content, String imageKeyword) {}

    /**
     * Part 3/4/5 Gemini 결과.
     * contextContent: 공통 배경 텍스트
     * questions: 파트별 질문 목록 (Part 3/4: 3개, Part 5: 2개)
     */
    public record QuestionGroupData(String contextContent, List<QuestionItemData> questions) {}

    /** 그룹 내 개별 질문 데이터. */
    public record QuestionItemData(String content) {}
}
