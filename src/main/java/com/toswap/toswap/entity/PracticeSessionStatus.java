package com.toswap.toswap.entity;

/**
 * 연습 세션 상태.
 *
 * PENDING    → 세션 생성 직후. 아직 음성 미제출.
 * PROCESSING → 음성 파일 수신 완료. Gemini AI 평가 진행 중.
 * DONE       → AI 평가 완료. 피드백 조회 가능.
 * ERROR      → AI 평가 실패. 재시도 필요.
 */
public enum PracticeSessionStatus {
    PENDING,
    PROCESSING,
    DONE,
    ERROR
}
