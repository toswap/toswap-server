package com.toswap.toswap.controller;

import com.toswap.toswap.dto.request.QuestionGenerateRequest;
import com.toswap.toswap.dto.response.QuestionResponse;
import com.toswap.toswap.dto.response.QuestionSetResponse;
import com.toswap.toswap.service.QuestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Question", description = "문제 API - Gemini AI로 TOEIC Speaking 문제 생성")
@RestController
@RequestMapping("/api/questions")
@RequiredArgsConstructor
public class QuestionController {

    private final QuestionService questionService;

    /**
     * POST /api/questions/generate
     *
     * partId에 따라 반환 구조가 달라진다:
     *
     * Part 1/2 → questions 1개, groupId/contextContent = null
     * Part 3/4 → questions 3개, contextContent = 서베이 상황 또는 문서
     * Part 5   → questions 2개, contextContent = 의견 표현 상황
     */
    @Operation(
            summary = "문제 세트 생성",
            description = """
                    Gemini AI로 파트에 맞는 TOEIC Speaking 문제를 생성하고 DB에 저장한다.
                    AI API 호출이 포함되므로 응답까지 3~8초 소요될 수 있다.

                    **응답 구조**
                    - `groupId`: Part 3/4/5만 존재. null이면 Part 1/2 독립 문제.
                    - `contextContent`: 공통 배경. null이면 독립 문제.
                      - Part 3: 서베이 상황 안내 ("Imagine you are talking with someone...")
                      - Part 4: 일정표/표/광고 텍스트
                      - Part 5: 의견 표현 주제 설명
                    - `questions`: 질문 목록
                      - Part 1/2: 1개
                      - Part 3/4: 3개 (Q1·Q2는 responseSeconds=15, Q3는 30)
                      - Part 5: 2개 (모두 responseSeconds=60)

                    **파트별 타이밍**
                    | Part | 유형 | 준비 | Q1·Q2 답변 | Q3 답변 |
                    |------|------|------|-----------|--------|
                    | 1 | 소리내어 읽기 | 45s | 45s | - |
                    | 2 | 사진 묘사 | 45s | 30s | - |
                    | 3 | 질문 응답 (3문항) | 3s | 15s | 30s |
                    | 4 | 정보 활용 (3문항) | 3s | 15s | 30s |
                    | 5 | 의견 표현 (2문항) | 45s | 60s | 60s |
                    """
    )
    @ApiResponse(responseCode = "200", description = "문제 세트 생성 성공")
    @ApiResponse(responseCode = "400", description = "partId가 1~5 범위 외")
    @ApiResponse(responseCode = "500", description = "Gemini API 호출 실패")
    @PostMapping("/generate")
    public ResponseEntity<QuestionSetResponse> generate(
            @RequestBody @Valid QuestionGenerateRequest request) {
        return ResponseEntity.ok(questionService.generate(request));
    }

    @Operation(
            summary = "개별 질문 단건 조회",
            description = "질문 ID로 개별 Question을 조회한다. sequenceNo가 있으면 그룹 소속 문제."
    )
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "404", description = "문제를 찾을 수 없음")
    @GetMapping("/{id}")
    public ResponseEntity<QuestionResponse> getById(
            @Parameter(description = "질문 ID") @PathVariable Long id) {
        return ResponseEntity.ok(questionService.getById(id));
    }

    @Operation(
            summary = "문제 그룹 조회",
            description = """
                    그룹 ID로 Part 3/4/5의 문제 세트 전체를 조회한다.
                    공통 배경(contextContent)과 소속 질문 목록이 함께 반환된다.
                    """
    )
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "404", description = "그룹을 찾을 수 없음")
    @GetMapping("/group/{groupId}")
    public ResponseEntity<QuestionSetResponse> getGroupById(
            @Parameter(description = "그룹 ID") @PathVariable Long groupId) {
        return ResponseEntity.ok(questionService.getGroupById(groupId));
    }
}
