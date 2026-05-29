package com.toswap.toswap.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * 카카오 로그인 후 이메일 미제공 유저의 추가 정보 입력 요청 DTO.
 */
public record ProfileUpdateRequest(

        @NotBlank(message = "이메일을 입력해주세요.")
        @Email(message = "올바른 이메일 형식이 아닙니다.")
        String email
) {
}
