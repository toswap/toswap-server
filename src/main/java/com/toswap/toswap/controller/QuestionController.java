package com.toswap.toswap.controller;

import com.toswap.toswap.dto.request.QuestionGenerateRequest;
import com.toswap.toswap.dto.response.QuestionResponse;
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

    @Operation(
            summary = "문제 생성",
            description = """
                    Gemini AI로 파트에 맞는 TOEIC Speaking 문제를 생성하고 DB에 저장한다.
                    AI API 호출이 포함되므로 응답까지 3~5초 소요될 수 있다.

                    - Part 1: 소리내어 읽을 영어 지문 생성
                    - Part 2: 사진 묘사 지시문 + Unsplash 이미지 URL 포함
                    - Part 3: 인터뷰/설문 상황의 질문
                    - Part 4: 일정/공고 정보 + 관련 질문
                    - Part 5: 의견 표현 주제
                    """
    )
    @ApiResponse(responseCode = "200", description = "문제 생성 성공")
    @ApiResponse(responseCode = "400", description = "partId가 1~5 범위 외")
    @ApiResponse(responseCode = "500", description = "Gemini API 호출 실패")
    @PostMapping("/generate")
    public ResponseEntity<QuestionResponse> generate(
            @RequestBody @Valid QuestionGenerateRequest request) {
        return ResponseEntity.ok(questionService.generate(request));
    }

    @Operation(summary = "문제 단건 조회", description = "ID로 저장된 문제를 조회한다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "404", description = "문제를 찾을 수 없음")
    @GetMapping("/{id}")
    public ResponseEntity<QuestionResponse> getById(
            @Parameter(description = "문제 ID") @PathVariable Long id) {
        return ResponseEntity.ok(questionService.getById(id));
    }
}
