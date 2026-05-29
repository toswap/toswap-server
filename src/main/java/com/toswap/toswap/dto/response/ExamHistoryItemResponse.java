package com.toswap.toswap.dto.response;

import com.toswap.toswap.entity.ExamSession;

import java.time.LocalDateTime;

/**
 * 시험 목록(GET /api/exam-sessions) 의 개별 항목 DTO.
 *
 * predictedScore/predictedLevel은 status=COMPLETED 일 때만 값이 있다.
 */
public record ExamHistoryItemResponse(
        Long examSessionId,
        String status,              // IN_PROGRESS, EVALUATING, COMPLETED, ABANDONED
        Short predictedScore,       // COMPLETED 시 설정, 그 외 null
        Short predictedLevel,       // COMPLETED 시 설정, 그 외 null
        LocalDateTime startedAt,
        LocalDateTime completedAt
) {

    public static ExamHistoryItemResponse from(ExamSession exam) {
        return new ExamHistoryItemResponse(
                exam.getId(),
                exam.getStatus().name(),
                exam.getPredictedScore(),
                exam.getPredictedLevel(),
                exam.getCreatedAt(),
                exam.getCompletedAt()
        );
    }
}
