package com.toswap.toswap.dto.response;

import java.util.List;

/**
 * 연습 시작(POST /api/practice-sessions) 응답 DTO.
 *
 * ── 프론트엔드 사용 방법 ────────────────────────────────────────────────────
 *
 * [Part 1/2]
 *   questionGroupId = null, contextContent = null
 *   sessions = [1개]
 *   → 바로 sessions[0]의 content를 보여주고 타이머 시작
 *
 * [Part 3/4]
 *   questionGroupId = {id}, contextContent = "Imagine you are..."
 *   sessions = [Q1, Q2, Q3] (sequenceNo 1→2→3 순서 보장)
 *   → 상단에 contextContent 표시 후 sessions를 순서대로 진행
 *   → 각 sessions[i].sessionId를 음성 제출 시(POST /api/feedbacks) 사용
 *
 * [Part 5]
 *   questionGroupId = {id}, contextContent = "Many companies now allow..."
 *   sessions = [Q1, Q2]
 *   → contextContent 표시 후 sessions 2개 순서대로 진행
 *
 * ── 타이밍 ──────────────────────────────────────────────────────────────────
 *   각 SessionItem에 prepSeconds / responseSeconds가 개별로 담겨있다.
 *   Part 3/4의 Q1·Q2는 responseSeconds=15, Q3는 30임을 주의.
 */
public record PracticeStartResponse(
        Short partId,
        Long questionGroupId,   // Part 3/4/5만 존재, Part 1/2는 null
        String contextContent,  // Part 3/4/5만 존재, Part 1/2는 null
        List<SessionItem> sessions
) {

    /**
     * 세션 항목 - 질문 1개에 대응하는 세션 정보.
     *
     * sessionId: 음성 파일 제출 시 사용 (POST /api/feedbacks body에 포함)
     * sequenceNo: 그룹 내 순서. Part 1/2는 null.
     * imageUrl: Part 2 전용. 묘사할 사진 URL.
     */
    public record SessionItem(
            Long sessionId,
            Long questionId,
            Short sequenceNo,
            String content,
            String imageUrl,
            Short prepSeconds,
            Short responseSeconds
    ) {}
}
