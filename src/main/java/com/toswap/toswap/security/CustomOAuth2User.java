package com.toswap.toswap.security;

import com.toswap.toswap.entity.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * OAuth2 로그인 성공 후 Spring Security가 내부적으로 사용하는 인증 객체.
 * 카카오에서 받아온 attributes와 DB에 저장된 User 엔티티를 함께 보관한다.
 * SecurityContext에 저장되며, 인증 이후 컨트롤러에서 @AuthenticationPrincipal로 꺼낼 수 있다.
 */
@Getter
public class CustomOAuth2User implements OAuth2User {

    private final User user;
    // 카카오 API 응답 원본 (id, kakao_account 등 전체 필드 포함)
    private final Map<String, Object> attributes;

    public CustomOAuth2User(User user, Map<String, Object> attributes) {
        this.user = user;
        this.attributes = attributes;
    }

    // Spring Security가 권한 체크 시 사용. 현재는 모든 로그인 유저에게 ROLE_USER 부여
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    // Spring Security가 principal 식별자로 사용하는 값. DB의 user.id를 문자열로 반환
    @Override
    public String getName() {
        return String.valueOf(user.getId());
    }

    public Long getUserId() {
        return user.getId();
    }
}
