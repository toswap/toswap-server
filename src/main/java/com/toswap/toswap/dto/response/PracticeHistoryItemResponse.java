package com.toswap.toswap.dto.response;

import com.toswap.toswap.entity.PracticeSessionStatus;

import java.time.LocalDateTime;

/**
 * 연습 기록 목록(GET /api/practice-sessions) 의 개별 항목 DTO.
 *
 * ── 그루핑 규칙 ─────────────────────────────────────────────────────────────
 *
 * Part 1/2: 세션 1개 → 기록 항목 1개
 *   firstSessionId = 해당 세션의 ID
 *   questionGroupId = null
 *   totalQuestions = 1
 *
 * Part 3/4/5: 세션 N개 (같은 questionGroup) → 기록 항목 1개
 *   firstSessionId = Q1 세션의 ID (연습 화면 복원 시 이 ID로 시작)
 *   questionGroupId = 공유 그룹 ID
 *   totalQuestions = 2 또는 3
 *
 * ── overallStatus 계산 ──────────────────────────────────────────────────────
 *   그룹 내 모든 세션이 DONE  → DONE
 *   하나라도 ERROR           → ERROR
 *   그 외 (일부만 완료 포함)  → PENDING
 */
public record PracticeHistoryItemResponse(
        Short partId,
        Long firstSessionId,        // 연습 화면 진입 시 사용할 세션 ID (Q1 또는 단독 세션)
        Long questionGroupId,       // Part 1/2: null
        PracticeSessionStatus overallStatus,
        int totalQuestions,         // Part 1/2: 1, Part 3/4: 3, Part 5: 2
        int completedQuestions,     // DONE 상태인 세션 수
        LocalDateTime createdAt     // 그룹 중 가장 먼저 생성된 세션의 createdAt
) {}
