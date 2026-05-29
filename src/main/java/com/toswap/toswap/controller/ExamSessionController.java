package com.toswap.toswap.controller;

import com.toswap.toswap.dto.response.*;
import com.toswap.toswap.security.CustomOAuth2User;
import com.toswap.toswap.service.ExamSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "ExamSession", description = "시험 세션 API - TOEIC Speaking 전체 시험 시뮬레이션")
@RestController
@RequestMapping("/api/exam-sessions")
@RequiredArgsConstructor
public class ExamSessionController {

    private final ExamSessionService examSessionService;

    @Operation(
            summary = "시험 시작",
            description = """
                    TOEIC Speaking 전체 시험을 시작한다. Part 1~5 총 11문제를 한 번에 생성한다.

                    **⚠️ 응답까지 18~48초 소요 가능 (Gemini API 6회 호출)**

                    **시험 구성**
                    - Part 1 (Read a Text Aloud): 2문제, prep=45s, resp=45s
                    - Part 2 (Describe a Picture): 1문제, prep=45s, resp=30s
                    - Part 3 (Respond to Questions): 3문제, Q1·Q2 resp=15s / Q3 resp=30s
                    - Part 4 (Respond to Questions Using Information Provided): 3문제, 위와 동일
                    - Part 5 (Express an Opinion): 2문제, prep=45s, resp=60s

                    **진행 방법**
                    `parts` 배열을 순서대로 진행한다. 각 세션의 `sessionId`를 보관했다가
                    음성 제출 시(POST /api/feedbacks) 사용한다.

                    이미 진행 중인 시험이 있으면 400 에러. 먼저 포기(PATCH /abandon)해야 한다.
                    """
    )
    @ApiResponse(responseCode = "200", description = "시험 시작 성공")
    @ApiResponse(responseCode = "400", description = "이미 진행 중인 시험 있음")
    @ApiResponse(responseCode = "500", description = "Gemini AI 문제 생성 실패")
    @PostMapping
    public ResponseEntity<ExamStartResponse> start(
            @AuthenticationPrincipal CustomOAuth2User oAuth2User) {
        return ResponseEntity.ok(
                examSessionService.start(oAuth2User.getUserId())
        );
    }

    @Operation(
            summary = "시험 상태 조회",
            description = """
                    시험의 현재 진행 상태를 조회한다.

                    시험 재진입 시 어느 질문까지 제출했는지 파악하는 용도.
                    각 세션의 `status`를 확인해서 `PENDING`인 세션부터 이어서 진행하면 된다.

                    본인 시험이 아니면 404 반환.
                    """
    )
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "404", description = "시험을 찾을 수 없음")
    @GetMapping("/{examSessionId}")
    public ResponseEntity<ExamSessionStatusResponse> getStatus(
            @Parameter(description = "시험 세션 ID") @PathVariable Long examSessionId,
            @AuthenticationPrincipal CustomOAuth2User oAuth2User) {
        return ResponseEntity.ok(
                examSessionService.getStatus(examSessionId, oAuth2User.getUserId())
        );
    }

    @Operation(
            summary = "시험 결과 조회",
            description = """
                    시험 결과를 조회한다.

                    **status별 응답 내용**
                    - `COMPLETED`: predictedScore(0~200), predictedLevel(1~8) 포함
                    - `IN_PROGRESS`: 아직 진행 중. completedSessions/totalSessions로 진행률 파악
                    - `ABANDONED`: 포기된 시험. 점수 없음

                    Feedback API가 구현되기 전까지 partResults[].averageScore는 null이다.
                    """
    )
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "404", description = "시험을 찾을 수 없음")
    @GetMapping("/{examSessionId}/result")
    public ResponseEntity<ExamResultResponse> getResult(
            @Parameter(description = "시험 세션 ID") @PathVariable Long examSessionId,
            @AuthenticationPrincipal CustomOAuth2User oAuth2User) {
        return ResponseEntity.ok(
                examSessionService.getResult(examSessionId, oAuth2User.getUserId())
        );
    }

    @Operation(
            summary = "내 시험 목록",
            description = "로그인한 유저의 시험 기록을 최신순으로 반환한다."
    )
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping
    public ResponseEntity<List<ExamHistoryItemResponse>> getHistory(
            @AuthenticationPrincipal CustomOAuth2User oAuth2User) {
        return ResponseEntity.ok(
                examSessionService.getHistory(oAuth2User.getUserId())
        );
    }

    @Operation(
            summary = "시험 포기",
            description = """
                    진행 중인 시험을 포기한다. status가 IN_PROGRESS인 시험만 포기 가능.

                    포기 후 새 시험을 시작할 수 있다.
                    """
    )
    @ApiResponse(responseCode = "204", description = "포기 성공")
    @ApiResponse(responseCode = "400", description = "이미 종료된 시험 (COMPLETED 또는 ABANDONED)")
    @ApiResponse(responseCode = "404", description = "시험을 찾을 수 없음")
    @PatchMapping("/{examSessionId}/abandon")
    public ResponseEntity<Void> abandon(
            @Parameter(description = "시험 세션 ID") @PathVariable Long examSessionId,
            @AuthenticationPrincipal CustomOAuth2User oAuth2User) {
        examSessionService.abandon(examSessionId, oAuth2User.getUserId());
        return ResponseEntity.noContent().build();
    }
}
