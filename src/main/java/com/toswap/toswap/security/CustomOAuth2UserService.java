package com.toswap.toswap.security;

import com.toswap.toswap.entity.Provider;
import com.toswap.toswap.entity.User;
import com.toswap.toswap.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * OAuth2 로그인 흐름에서 카카오 사용자 정보를 처리하는 서비스.
 *
 * 흐름:
 * 1. 카카오 인증 완료 → Spring Security가 이 서비스의 loadUser() 호출
 * 2. 카카오 API 응답에서 사용자 정보 추출
 * 3. DB에 해당 유저가 있으면 프로필 업데이트, 없으면 신규 저장
 * 4. CustomOAuth2User 반환 → SecurityContext에 저장됨
 */
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        // 부모 클래스가 카카오 userinfo 엔드포인트를 호출해서 사용자 정보를 가져옴
        OAuth2User oAuth2User = super.loadUser(userRequest);

        // application.yaml의 registration 키 (kakao, google 등)
        String registrationId = userRequest.getClientRegistration().getRegistrationId();

        if ("kakao".equals(registrationId)) {
            return processKakaoUser(oAuth2User);
        }

        throw new OAuth2AuthenticationException("Unsupported provider: " + registrationId);
    }

    /**
     * 카카오 응답 구조:
     * {
     *   "id": 12345678,                          // 카카오 고유 사용자 ID
     *   "kakao_account": {
     *     "profile": {
     *       "nickname": "홍길동",
     *       "profile_image_url": "https://..."
     *     }
     *     // 비즈앱 미등록 시 email 필드 없음
     *   }
     * }
     */
    @SuppressWarnings("unchecked")
    private OAuth2User processKakaoUser(OAuth2User oAuth2User) {
        Map<String, Object> attributes = oAuth2User.getAttributes();

        // 카카오 고유 ID (provider_id로 DB에 저장)
        String providerId = String.valueOf(attributes.get("id"));

        Map<String, Object> kakaoAccount = (Map<String, Object>) attributes.get("kakao_account");
        Map<String, Object> profile = (Map<String, Object>) kakaoAccount.get("profile");

        String nickname = (String) profile.get("nickname");
        String profileImageUrl = (String) profile.get("profile_image_url");

        // 기존 유저면 프로필 업데이트, 신규 유저면 저장 (이메일은 추후 추가 정보 입력으로 수집)
        User user = userRepository.findByProviderAndProviderId(Provider.KAKAO, providerId)
                .map(existing -> {
                    existing.updateProfile(nickname, profileImageUrl);
                    return existing;
                })
                .orElseGet(() -> userRepository.save(User.builder()
                        .provider(Provider.KAKAO)
                        .providerId(providerId)
                        .name(nickname)
                        .profileImageUrl(profileImageUrl)
                        .build()));

        return new CustomOAuth2User(user, attributes);
    }
}
