package com.toswap.toswap.controller;

import com.toswap.toswap.dto.response.FeedbackResponse;
import com.toswap.toswap.security.CustomOAuth2User;
import com.toswap.toswap.service.FeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Feedback", description = "피드백 API - 음성 제출 및 AI 평가")
@RestController
@RequestMapping("/api/feedbacks")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;

    @Operation(
            summary = "음성 제출 및 AI 평가",
            description = """
                    녹음된 음성 파일을 제출하면 Gemini AI가 TOEIC Speaking 기준으로 평가한다.

                    **요청 형식**: `multipart/form-data`
                    - `sessionId` (Long, required): 평가할 연습 세션 ID
                    - `audio` (File, required): 녹음 파일. 권장 포맷: `audio/webm` (브라우저 MediaRecorder 기본값)

                    **지원 오디오 포맷**: WAV, MP3, AIFF, AAC, OGG, FLAC, WebM/Opus

                    **평가 기준** (각 1~10점):
                    - scorePronunciation: 발음 정확도
                    - scoreIntonation: 억양/리듬/강세
                    - scoreGrammar: 문법 정확도
                    - scoreVocabulary: 어휘 적합성
                    - scoreFluency: 유창성 (자연스러운 속도와 흐름)
                    - scoreContent: 답변 내용의 적절성과 완성도
                    - scoreOverall: 종합 점수 (content 가중치 가장 높음)

                    **⚠️ 응답까지 5~15초 소요 가능 (Gemini 음성 처리 시간)**

                    시험 모드 세션의 경우, 11개 세션이 모두 완료되면 자동으로 시험 점수가 계산된다.
                    """
    )
    @ApiResponse(responseCode = "200", description = "평가 완료")
    @ApiResponse(responseCode = "400", description = "이미 평가된 세션 / 유효하지 않은 요청")
    @ApiResponse(responseCode = "404", description = "세션을 찾을 수 없음")
    @ApiResponse(responseCode = "500", description = "Gemini AI 음성 평가 실패", content = @Content)
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FeedbackResponse> submit(
            @Parameter(description = "평가할 세션 ID") @RequestParam Long sessionId,
            @Parameter(description = "녹음 파일 (audio/webm 권장)")
            @RequestPart MultipartFile audio,
            @AuthenticationPrincipal CustomOAuth2User oAuth2User) {
        return ResponseEntity.ok(
                feedbackService.submit(sessionId, audio, oAuth2User.getUserId())
        );
    }

    @Operation(
            summary = "피드백 단건 조회",
            description = "피드백 ID로 평가 결과를 조회한다. 본인 피드백이 아니면 404 반환."
    )
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "404", description = "피드백을 찾을 수 없음")
    @GetMapping("/{feedbackId}")
    public ResponseEntity<FeedbackResponse> getById(
            @Parameter(description = "피드백 ID") @PathVariable Long feedbackId,
            @AuthenticationPrincipal CustomOAuth2User oAuth2User) {
        return ResponseEntity.ok(
                feedbackService.getById(feedbackId, oAuth2User.getUserId())
        );
    }

    @Operation(
            summary = "세션 기준 피드백 조회",
            description = """
                    세션 ID로 해당 세션의 평가 결과를 조회한다.
                    `feedbackId`를 모를 때 세션 ID로 조회하는 용도.
                    """
    )
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "404", description = "세션 또는 피드백을 찾을 수 없음")
    @GetMapping("/session/{sessionId}")
    public ResponseEntity<FeedbackResponse> getBySessionId(
            @Parameter(description = "연습 세션 ID") @PathVariable Long sessionId,
            @AuthenticationPrincipal CustomOAuth2User oAuth2User) {
        return ResponseEntity.ok(
                feedbackService.getBySessionId(sessionId, oAuth2User.getUserId())
        );
    }
}
