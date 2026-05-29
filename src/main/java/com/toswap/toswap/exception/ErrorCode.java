package com.toswap.toswap.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 공통
    INVALID_REQUEST(400, "잘못된 요청입니다."),
    UNAUTHORIZED(401, "인증이 필요합니다."),
    FORBIDDEN(403, "접근 권한이 없습니다."),
    NOT_FOUND(404, "리소스를 찾을 수 없습니다."),
    INTERNAL_SERVER_ERROR(500, "서버 내부 오류가 발생했습니다."),

    // 유저
    EMAIL_ALREADY_EXISTS(400, "이미 사용 중인 이메일입니다."),

    // 문제
    QUESTION_NOT_FOUND(404, "문제를 찾을 수 없습니다."),
    QUESTION_GENERATION_FAILED(500, "문제 생성에 실패했습니다."),
    INVALID_PART_ID(400, "파트 ID는 1~5 사이여야 합니다."),

    // 연습 세션
    PRACTICE_SESSION_NOT_FOUND(404, "연습 세션을 찾을 수 없습니다."),
    PRACTICE_SESSION_ALREADY_DONE(400, "이미 완료된 연습 세션입니다.");

    private final int status;
    private final String message;
}
