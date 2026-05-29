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
    PRACTICE_SESSION_ALREADY_DONE(400, "이미 완료된 연습 세션입니다."),

    // 피드백
    FEEDBACK_NOT_FOUND(404, "피드백을 찾을 수 없습니다."),
    FEEDBACK_ALREADY_EXISTS(400, "이미 평가된 세션입니다."),
    FEEDBACK_EVALUATION_FAILED(500, "AI 음성 평가에 실패했습니다."),

    // 시험 세션
    EXAM_SESSION_NOT_FOUND(404, "시험 세션을 찾을 수 없습니다."),
    EXAM_ALREADY_IN_PROGRESS(400, "이미 진행 중인 시험이 있습니다. 먼저 현재 시험을 완료하거나 포기해주세요."),
    EXAM_SESSION_ALREADY_FINISHED(400, "이미 종료된 시험 세션입니다.");

    private final int status;
    private final String message;
}
