package com.toswap.toswap.controller;

import com.toswap.toswap.dto.request.PracticeSessionStartRequest;
import com.toswap.toswap.dto.response.PracticeHistoryItemResponse;
import com.toswap.toswap.dto.response.PracticeSessionDetailResponse;
import com.toswap.toswap.dto.response.PracticeStartResponse;
import com.toswap.toswap.security.CustomOAuth2User;
import com.toswap.toswap.service.PracticeSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "PracticeSession", description = "연습 세션 API - 파트별 TOEIC Speaking 연습 관리")
@RestController
@RequestMapping("/api/practice-sessions")
@RequiredArgsConstructor
public class PracticeSessionController {

    private final PracticeSessionService practiceSessionService;

    @Operation(
            summary = "연습 시작",
            description = """
                    파트를 선택하면 Gemini AI가 문제를 생성하고 연습 세션을 시작한다.
                    문제 생성 때문에 응답까지 3~8초 소요될 수 있다.

                    **응답 구조**
                    - `sessions`: 질문 목록. 각 항목의 `sessionId`를 보관했다가
                      음성 제출 시(POST /api/feedbacks) 사용한다.
                    - Part 3/4/5: `contextContent`를 먼저 읽은 뒤 `sessions`를 순서대로 진행.
                    - Part 3/4: sessions[0]·[1]의 responseSeconds=15, sessions[2]는 30.
                    - Part 5: 모든 sessions의 responseSeconds=60.
                    """
    )
    @ApiResponse(responseCode = "200", description = "연습 세션 시작 성공")
    @ApiResponse(responseCode = "400", description = "partId 유효성 오류")
    @ApiResponse(responseCode = "500", description = "Gemini AI 문제 생성 실패")
    @PostMapping
    public ResponseEntity<PracticeStartResponse> start(
            @RequestBody @Valid PracticeSessionStartRequest request,
            @AuthenticationPrincipal CustomOAuth2User oAuth2User) {
        return ResponseEntity.ok(
                practiceSessionService.start(request, oAuth2User.getUserId())
        );
    }

    @Operation(
            summary = "세션 상세 조회",
            description = """
                    세션 ID로 특정 연습 세션의 상태와 문제 내용을 조회한다.
                    연습 화면 복원이나 피드백 확인 화면 진입 시 사용.
                    본인 세션이 아니면 404 반환.
                    """
    )
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "404", description = "세션을 찾을 수 없음 (없거나 타인 소유)")
    @GetMapping("/{sessionId}")
    public ResponseEntity<PracticeSessionDetailResponse> getDetail(
            @Parameter(description = "세션 ID") @PathVariable Long sessionId,
            @AuthenticationPrincipal CustomOAuth2User oAuth2User) {
        return ResponseEntity.ok(
                practiceSessionService.getDetail(sessionId, oAuth2User.getUserId())
        );
    }

    @Operation(
            summary = "내 연습 기록 목록",
            description = """
                    로그인한 유저의 연습 기록을 최신순으로 반환한다.
                    Part 3/4/5처럼 같은 그룹의 세션은 1개 항목으로 묶여서 반환된다.

                    **overallStatus 해석**
                    - PENDING: 아직 음성 미제출 (연습 미완료)
                    - DONE: 모든 질문 음성 제출 + 피드백 완료
                    - ERROR: AI 평가 실패
                    """
    )
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping
    public ResponseEntity<List<PracticeHistoryItemResponse>> getHistory(
            @AuthenticationPrincipal CustomOAuth2User oAuth2User) {
        return ResponseEntity.ok(
                practiceSessionService.getHistory(oAuth2User.getUserId())
        );
    }
}
