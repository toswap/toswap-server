package com.toswap.toswap.dto.response;

import com.toswap.toswap.entity.User;

/**
 * 로그인한 유저 정보 응답 DTO.
 * hasEmail 필드로 프론트엔드에서 추가 정보 입력 여부를 판단할 수 있다.
 */
public record UserResponse(
        Long id,
        String name,
        String email,
        String profileImageUrl,
        String provider,
        boolean hasEmail   // false면 프론트에서 /additional-info 페이지로 유도
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getProfileImageUrl(),
                user.getProvider().name().toLowerCase(),
                user.hasEmail()
        );
    }
}
