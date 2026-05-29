package com.toswap.toswap.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 연습 세션 시작 요청.
 * partId만 받으면 서버에서 Gemini로 문제를 자동 생성한다.
 */
public record PracticeSessionStartRequest(
        @NotNull(message = "partId는 필수입니다.")
        @Min(value = 1, message = "partId는 1 이상이어야 합니다.")
        @Max(value = 5, message = "partId는 5 이하여야 합니다.")
        Integer partId
) {}
