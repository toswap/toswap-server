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

import com.toswap.toswap.dto.response.ImprovementItem;

import org.springframework.http.MediaType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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

    // 문제 생성 + 음성 평가 공통 모델: 500 RPD 무료, 오디오 입력 지원 확인
    private static final String MODEL = "gemini-3.1-flash-lite";
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
    // 음성 평가 (Feedback API용)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 음성 파일을 받아 TOEIC Speaking 기준으로 평가한다.
     *
     * Gemini의 멀티모달 기능(inline_data)으로 오디오를 직접 전송한다.
     * 브라우저 MediaRecorder는 보통 audio/webm;codecs=opus 형식을 사용한다.
     * Gemini 2.5 Flash가 지원하는 포맷: WAV, MP3, AIFF, AAC, OGG, FLAC, WebM/Opus
     *
     * @param audioBytes 녹음된 음성 바이트 배열
     * @param mimeType   오디오 MIME 타입 (예: "audio/webm", "audio/wav")
     * @param ctx        평가 컨텍스트 (파트, 질문 내용, 공통 배경)
     */
    public FeedbackData evaluateAudio(byte[] audioBytes, String mimeType, EvaluationContext ctx) {
        String prompt = buildEvaluationPrompt(ctx);
        String jsonText = callGeminiWithAudio(prompt, audioBytes, mimeType, ctx.partId());
        return parseFeedbackData(jsonText);
    }

    private String buildEvaluationPrompt(EvaluationContext ctx) {
        String partName = switch (ctx.partId()) {
            case 1 -> "Read a Text Aloud";
            case 2 -> "Describe a Picture";
            case 3 -> "Respond to Questions";
            case 4 -> "Respond to Questions Using Information Provided";
            case 5 -> "Express an Opinion";
            default -> "Unknown";
        };

        StringBuilder sb = new StringBuilder();
        sb.append("""
                You are an expert TOEIC Speaking test evaluator with deep knowledge \
                of the official TOEIC Speaking scoring rubric.

                == Test Information ==
                Part %d: %s

                """.formatted(ctx.partId(), partName));

        // Part 3/4/5: 공통 배경 포함
        if (ctx.contextContent() != null && !ctx.contextContent().isBlank()) {
            sb.append("== Context/Document Given to Test Taker ==\n");
            sb.append(ctx.contextContent()).append("\n\n");
        }

        sb.append("== Question ==\n");
        sb.append(ctx.questionContent()).append("\n\n");

        sb.append("""
                == 평가 지침 ==
                당신은 ETS 공식 TOEIC Speaking 수석 심사관입니다.
                실제 시험과 동일한 기준으로 음성 응답을 엄격하고 정확하게 평가하세요.
                모든 피드백은 한국어로 작성하세요.

                [채점 철학]
                - 관대한 채점은 응시자의 성장을 방해합니다. 실제 시험 수준의 기준을 적용하세요.
                - 단, 실제로 뛰어난 응답에는 높은 점수(9~10점)를 줄 수 있습니다. \
                  완벽한 응답이라면 10점도 가능합니다. 점수 범위(1~10)를 모두 활용하세요.
                - 평균적인 한국인 영어 학습자의 응답은 5~6점대로 평가하는 것이 적절합니다.
                - 특별한 강점 없이 무난한 수준이라면 7점을 초과하지 마세요.

                [점수 기준 — 각 항목 1~10점]
                - scorePronunciation(발음)
                    10: 모든 자음·모음이 원어민 수준으로 정확하고 명료
                     7: 대부분 명료하나 몇 가지 비원어민 발음 패턴 존재
                     5: 청취자가 집중해야 이해 가능한 발음 오류가 반복됨
                     3: 잦은 오발음으로 의사소통에 지장
                     1: 대부분의 단어를 알아듣기 어려움

                - scoreIntonation(억양)
                    10: 완전히 자연스러운 영어 리듬·강세·문장 억양
                     7: 대체로 자연스럽지만 단조롭거나 부자연스러운 구간 있음
                     5: 모국어 억양이 강하게 드러나 어색함
                     3: 거의 단조롭거나 강세 위치가 일관되게 잘못됨
                     1: 영어 억양 패턴을 찾아보기 어려움

                - scoreGrammar(문법)
                    10: 복잡한 문장 구조를 오류 없이 사용
                     7: 기본 문법은 정확하나 복잡한 구조에서 산발적 오류
                     5: 단순 문장 위주, 시제·관사·전치사 오류가 반복됨
                     3: 문법 오류가 많아 의미 파악이 어려운 경우 있음
                     1: 문법 체계가 거의 없음

                - scoreVocabulary(어휘)
                    10: 맥락에 정확한 고급 어휘를 다양하게 구사
                     7: 적절한 어휘를 사용하지만 다양성이 제한적
                     5: 기본 어휘 위주, 반복이 많고 표현이 단순함
                     3: 어휘 부족으로 의미 전달에 한계
                     1: 최소한의 단어만 사용하거나 부적절한 어휘 다수

                - scoreFluency(유창성)
                    10: 자연스러운 속도, 불필요한 멈춤 없음
                     7: 대체로 유창하나 간헐적인 망설임·수정 있음
                     5: 잦은 멈춤(uh, um)이나 반복으로 흐름이 끊김
                     3: 긴 침묵이나 재시작이 잦아 의사소통 저해
                     1: 거의 말을 잇지 못함

                - scoreContent(내용)
                    10: 질문을 완벽히 이해하고 논리적·풍부한 답변, 구체적 근거 포함
                     7: 질문에 적절히 답하나 근거·구체성이 다소 부족
                     5: 핵심은 언급하지만 내용이 단순하거나 일부 빗나감
                     3: 질문과 관련성이 낮거나 매우 불완전한 답변
                     1: 질문에 대한 응답이 없거나 전혀 관련 없음

                [추가 항목]
                - transcript: 발화 내용을 빠짐없이 그대로 전사 (영어 응답이면 영어로)
                - scoreOverall: 종합 점수 (1~10). content에 가장 높은 가중치 적용.
                  각 항목 점수를 단순 평균하지 말고, 전체적인 의사소통 능력을 종합적으로 판단하세요.
                - strengths: 이 응답에서 실제로 관찰된 잘한 점 2~3개.
                  구체적인 발화 내용을 인용하여 완전한 한국어 문장으로 서술하세요.
                  (칭찬을 위한 칭찬이 아닌, 실제 강점만 기재)
                - improvements: 개선이 필요한 영역 2~3개. 각 항목은 다음 세 필드를 포함:
                    * area: 평가 영역 (발음 / 억양 / 문법 / 어휘 / 유창성 / 내용 중 하나)
                    * issue: 이 응답에서 발견된 구체적인 문제점 (1~2문장, 반드시 발화 예시 인용)
                    * suggestion: 개선을 위한 실용적이고 구체적인 조언 (1~2문장, 연습 방법 포함)
                - detailedComment: 심사관의 관점에서 핵심 피드백을 담은 2~4문장 한국어 코멘트.
                  격려는 최소화하고 현재 수준과 개선 방향에 집중하세요.

                반드시 아래 JSON 형식으로만 응답하세요:
                {
                  "transcript": "...",
                  "scorePronunciation": 7,
                  "scoreIntonation": 6,
                  "scoreGrammar": 8,
                  "scoreVocabulary": 7,
                  "scoreFluency": 6,
                  "scoreContent": 8,
                  "scoreOverall": 7,
                  "strengths": [
                    "문장 구조가 명확하여 답변의 요점이 잘 전달되었습니다.",
                    "어휘 선택이 다양하고 적절했습니다."
                  ],
                  "improvements": [
                    {
                      "area": "문법",
                      "issue": "'I go store' 처럼 관사와 전치사가 생략된 경우가 있었습니다.",
                      "suggestion": "명사 앞에 관사(a/the)와 전치사(to/at/in)를 넣는 연습을 하세요. 예: 'I go to the store.'"
                    }
                  ],
                  "detailedComment": "..."
                }
                """);

        return sb.toString();
    }

    /**
     * 음성 평가 결과 JSON 파싱.
     */
    private FeedbackData parseFeedbackData(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);

            List<String> strengths = new ArrayList<>();
            node.path("strengths").forEach(n -> strengths.add(n.asText()));

            // improvements: 구조화된 객체 배열 파싱
            // area(영역) + issue(문제점) + suggestion(개선 방법)
            List<ImprovementItem> improvements = new ArrayList<>();
            node.path("improvements").forEach(n -> improvements.add(new ImprovementItem(
                    n.path("area").asText(""),
                    n.path("issue").asText(""),
                    n.path("suggestion").asText("")
            )));

            return new FeedbackData(
                    node.path("transcript").asText(""),
                    (short) node.path("scorePronunciation").asInt(5),
                    (short) node.path("scoreIntonation").asInt(5),
                    (short) node.path("scoreGrammar").asInt(5),
                    (short) node.path("scoreVocabulary").asInt(5),
                    (short) node.path("scoreFluency").asInt(5),
                    (short) node.path("scoreContent").asInt(5),
                    (short) node.path("scoreOverall").asInt(5),
                    strengths,
                    improvements,
                    node.path("detailedComment").asText("")
            );
        } catch (Exception e) {
            log.error("Gemini 피드백 JSON 파싱 실패: {}", e.getMessage());
            throw new BusinessException(ErrorCode.FEEDBACK_EVALUATION_FAILED);
        }
    }

    /**
     * 오디오 평가: Gemini File API에 먼저 업로드 후 URI로 generateContent 호출.
     *
     * inline_data(base64 직접 첨부) 방식은 일부 모델에서 500 에러를 유발한다.
     * File API 방식은 업로드 URI를 참조하므로 더 안정적이다.
     *
     * 업로드 흐름:
     *   1. POST /upload/v1beta/files → 업로드 세션 시작, x-goog-upload-url 수신
     *   2. PUT {uploadUrl} → 오디오 바이트 전송, file URI 수신
     *   3. POST /v1beta/models/{model}:generateContent → file_data URI 참조로 평가 요청
     */
    private String callGeminiWithAudio(String prompt, byte[] audioBytes, String mimeType, short partId) {
        try {
            // Step 1: 업로드 세션 시작
            var initEntity = webClient.post()
                    .uri("/upload/v1beta/files")
                    .header("X-Goog-Upload-Protocol", "resumable")
                    .header("X-Goog-Upload-Command", "start")
                    .header("X-Goog-Upload-Header-Content-Length", String.valueOf(audioBytes.length))
                    .header("X-Goog-Upload-Header-Content-Type", mimeType)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("file", Map.of()))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError,
                            resp -> resp.bodyToMono(String.class)
                                    .doOnNext(body -> log.error("Gemini 파일 업로드 세션 오류 (partId={}, status={}): {}",
                                            partId, resp.statusCode(), body))
                                    .then(Mono.error(new BusinessException(ErrorCode.FEEDBACK_EVALUATION_FAILED))))
                    .toBodilessEntity()
                    .block();

            String uploadUrl = Objects.requireNonNull(initEntity)
                    .getHeaders().getFirst("x-goog-upload-url");
            if (uploadUrl == null) {
                log.error("Gemini 파일 업로드 URL 수신 실패 (partId={})", partId);
                throw new BusinessException(ErrorCode.FEEDBACK_EVALUATION_FAILED);
            }

            // Step 2: 오디오 바이트 업로드 (절대 URL이므로 별도 WebClient 사용)
            @SuppressWarnings("unchecked")
            Map<String, Object> uploadResult = (Map<String, Object>) WebClient.create().put()
                    .uri(uploadUrl)
                    .header("Content-Length", String.valueOf(audioBytes.length))
                    .header("X-Goog-Upload-Command", "upload, finalize")
                    .header("X-Goog-Upload-Offset", "0")
                    .contentType(MediaType.parseMediaType(mimeType))
                    .bodyValue(audioBytes)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            @SuppressWarnings("unchecked")
            Map<String, Object> fileInfo = (Map<String, Object>) Objects.requireNonNull(uploadResult).get("file");
            String fileUri = (String) fileInfo.get("uri");
            String fileName = (String) fileInfo.get("name"); // "files/xxxxxx" 형태
            log.info("Gemini 파일 업로드 완료 (partId={}, fileUri={})", partId, fileUri);

            // Step 2.5: 파일이 ACTIVE 상태가 될 때까지 대기 (PROCESSING 중 generateContent 호출 시 500 에러)
            waitUntilFileActive(fileName);

            // Step 3: 파일 URI로 generateContent 호출
            var fileDataPart = Map.of(
                    "file_data", Map.of(
                            "mime_type", mimeType,
                            "file_uri", fileUri
                    )
            );
            var textPart = Map.of("text", prompt);

            var requestBody = Map.of(
                    "contents", List.of(Map.of("parts", List.of(textPart, fileDataPart))),
                    "generationConfig", Map.of("responseMimeType", "application/json")
            );

            GeminiApiResponse response = webClient.post()
                    .uri("/v1beta/models/" + MODEL + ":generateContent")
                    .bodyValue(requestBody)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError,
                            resp -> resp.bodyToMono(String.class)
                                    .doOnNext(body -> log.error("Gemini 음성 평가 API 오류 (partId={}, status={}): {}",
                                            partId, resp.statusCode(), body))
                                    .then(Mono.error(new BusinessException(ErrorCode.FEEDBACK_EVALUATION_FAILED))))
                    .bodyToMono(GeminiApiResponse.class)
                    .block();

            if (response == null
                    || response.candidates() == null
                    || response.candidates().isEmpty()) {
                throw new BusinessException(ErrorCode.FEEDBACK_EVALUATION_FAILED);
            }

            return response.candidates().get(0).content().parts().get(0).text();

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Gemini 음성 평가 API 호출 실패 (partId={}): {}", partId, e.getMessage());
            throw new BusinessException(ErrorCode.FEEDBACK_EVALUATION_FAILED);
        }
    }

    /**
     * 파일이 ACTIVE 상태가 될 때까지 폴링한다.
     * 업로드 직후 파일은 PROCESSING 상태일 수 있으며, 이 상태에서 generateContent를 호출하면 500 에러가 발생한다.
     * 최대 20초(1초 간격 × 20회) 대기하며, 그 이후에도 ACTIVE가 되지 않으면 그냥 진행한다.
     */
    private void waitUntilFileActive(String fileName) {
        if (fileName == null) return;

        for (int i = 0; i < 20; i++) {
            try {
                Thread.sleep(1_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> fileStatus = webClient.get()
                    .uri("/v1beta/" + fileName)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (fileStatus == null) continue;
            String state = (String) fileStatus.get("state");
            log.info("파일 상태 확인 (fileName={}, state={}, 시도={})", fileName, state, i + 1);

            if ("ACTIVE".equals(state)) return;
            if ("FAILED".equals(state)) {
                log.error("Gemini 파일 처리 실패: {}", fileName);
                throw new BusinessException(ErrorCode.FEEDBACK_EVALUATION_FAILED);
            }
        }
        log.warn("파일 ACTIVE 대기 시간 초과, 강제 진행 (fileName={})", fileName);
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
                            resp -> resp.bodyToMono(String.class)
                                    .doOnNext(body -> log.error("Gemini 문제 생성 API 오류 (partId={}, status={}): {}",
                                            partId, resp.statusCode(), body))
                                    .then(Mono.error(new BusinessException(ErrorCode.QUESTION_GENERATION_FAILED))))
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
     * 음성 평가 컨텍스트.
     * FeedbackService가 GeminiService.evaluateAudio() 호출 시 전달한다.
     */
    public record EvaluationContext(
            short partId,
            String questionContent,
            String contextContent   // Part 1/2: null, Part 3/4/5: 공통 배경 텍스트
    ) {}

    /**
     * Gemini 음성 평가 결과.
     * 모든 점수는 1~10 척도.
     * FeedbackService가 이 데이터를 받아 Feedback 엔티티를 생성한다.
     */
    public record FeedbackData(
            String transcript,
            short scorePronunciation,
            short scoreIntonation,
            short scoreGrammar,
            short scoreVocabulary,
            short scoreFluency,
            short scoreContent,
            short scoreOverall,
            List<String> strengths,
            List<ImprovementItem> improvements,  // 구조화된 개선 항목
            String detailedComment
    ) {}

    /**
     * Part 3/4/5 Gemini 결과.
     * contextContent: 공통 배경 텍스트
     * questions: 파트별 질문 목록 (Part 3/4: 3개, Part 5: 2개)
     */
    public record QuestionGroupData(String contextContent, List<QuestionItemData> questions) {}

    /** 그룹 내 개별 질문 데이터. */
    public record QuestionItemData(String content) {}
}
