package com.toswap.toswap.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 문제 생성 요청 DTO.
 * partId: TOEIC Speaking 파트 번호 (1~5)
 *   - Part 1: 소리내어 읽기 (Read Aloud)
 *   - Part 2: 사진 묘사 (Describe a Picture) → 이미지 포함
 *   - Part 3: 질문 응답 (Respond to Questions)
 *   - Part 4: 정보 활용 응답 (Respond Using Information)
 *   - Part 5: 의견 표현 (Express an Opinion)
 */
public record QuestionGenerateRequest(

        @NotNull(message = "파트 ID는 필수입니다.")
        @Min(value = 1, message = "파트 ID는 1 이상이어야 합니다.")
        @Max(value = 5, message = "파트 ID는 5 이하여야 합니다.")
        Integer partId
) {
}
