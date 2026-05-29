package com.toswap.toswap.service;

import com.toswap.toswap.dto.request.ProfileUpdateRequest;
import com.toswap.toswap.dto.response.UserResponse;
import com.toswap.toswap.entity.User;
import com.toswap.toswap.exception.BusinessException;
import com.toswap.toswap.exception.ErrorCode;
import com.toswap.toswap.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;

    /**
     * 현재 로그인한 유저 정보를 조회한다.
     * userId는 SecurityContext의 CustomOAuth2User에서 꺼낸 값.
     */
    @Transactional(readOnly = true)
    public UserResponse getMe(Long userId) {
        User user = findUserById(userId);
        return UserResponse.from(user);
    }

    /**
     * 카카오 로그인 후 이메일 미제공 유저가 이메일을 추가로 입력할 때 호출.
     * 이미 이메일이 등록된 유저는 수정 불가 (중복 이메일 방지 포함).
     */
    @Transactional
    public UserResponse updateProfile(Long userId, ProfileUpdateRequest request) {
        User user = findUserById(userId);

        // 이미 이메일이 있는 유저는 이 엔드포인트로 변경 불가
        if (user.hasEmail()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        // 이메일 중복 체크
        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        user.updateEmail(request.email());
        return UserResponse.from(user);
    }

    private User findUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }
}
