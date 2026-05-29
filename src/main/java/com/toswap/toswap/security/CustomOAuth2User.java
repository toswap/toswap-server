package com.toswap.toswap.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * OAuth2 로그인 성공 후 SecurityContext에 저장되는 인증 객체.
 * Redis 세션에 직렬화되어 저장되므로 Serializable을 구현해야 한다.
 *
 * User 엔티티를 직접 보관하면 JPA 지연 로딩 + 직렬화 문제가 발생하므로
 * 필요한 값(userId, name, hasEmail)만 꺼내서 저장한다.
 */
public class CustomOAuth2User implements OAuth2User, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Long userId;
    private final String name;
    // 이메일 미제공 유저(카카오 비즈앱 미등록)를 추가 정보 입력 페이지로 유도하기 위해 보관
    private final boolean hasEmail;

    // attributes는 카카오 API 원본 응답 - 직렬화 보장이 어려운 중첩 Map을 포함할 수 있어 transient 처리
    // 로그인 이후에는 사용하지 않으므로 직렬화하지 않아도 무방
    private final transient Map<String, Object> attributes;

    public CustomOAuth2User(Long userId, String name, boolean hasEmail, Map<String, Object> attributes) {
        this.userId = userId;
        this.name = name;
        this.hasEmail = hasEmail;
        this.attributes = attributes;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    // Spring Security가 principal 식별자로 사용하는 값
    @Override
    public String getName() {
        return String.valueOf(userId);
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public Long getUserId() {
        return userId;
    }

    public boolean hasEmail() {
        return hasEmail;
    }
}
