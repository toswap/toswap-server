package com.toswap.toswap.dto.response;

import java.util.List;

/**
 * 시험 시작(POST /api/exam-sessions) 응답.
 *
 * ── 시험 진행 순서 ────────────────────────────────────────────────────────────
 *   parts 리스트가 Part 1 → 2 → 3 → 4 → 5 순서로 반환된다.
 *   프론트엔드는 parts[0].sessions → parts[1].sessions → ... 순으로 문제를 표시.
 *
 * ── 음성 제출 ─────────────────────────────────────────────────────────────────
 *   각 세션의 sessionId를 보관했다가 음성 녹음 후 POST /api/feedbacks 에 사용한다.
 *
 * ── Part별 구조 ───────────────────────────────────────────────────────────────
 *   Part 1 (2문제): questionGroupId=null, contextContent=null, sessions 2개
 *   Part 2 (1문제): questionGroupId=null, contextContent=null, sessions 1개
 *   Part 3 (3문제): questionGroupId≠null, contextContent(서베이 안내), sessions 3개
 *   Part 4 (3문제): questionGroupId≠null, contextContent(문서/표), sessions 3개
 *   Part 5 (2문제): questionGroupId≠null, contextContent(의견 주제), sessions 2개
 *   → 총 11 sessions
 */
public record ExamStartResponse(
        Long examSessionId,
        List<PartSection> parts
) {

    /**
     * 파트 단위 섹션.
     * Part 3/4/5는 contextContent를 먼저 읽게 하고, sessions 순서대로 질문을 진행한다.
     */
    public record PartSection(
            Short partId,
            Long questionGroupId,      // Part 1/2: null
            String contextContent,     // Part 1/2: null. Part 3/4/5: 공통 배경 텍스트
            List<SessionItem> sessions
    ) {}

    /**
     * 개별 세션 아이템.
     * sequenceNo: Part 1/2는 null (독립 문제), Part 3/4/5는 1~3.
     * imageUrl: Part 2에만 존재 (Unsplash 이미지 URL).
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
