package com.toswap.toswap.dto.response;

import com.toswap.toswap.entity.ExamSession;
import com.toswap.toswap.entity.PracticeSession;
import com.toswap.toswap.entity.PracticeSessionStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 시험 결과(GET /api/exam-sessions/{id}/result) 응답.
 *
 * ── 점수/레벨 유효 시점 ───────────────────────────────────────────────────────
 *   status=COMPLETED 일 때만 predictedScore, predictedLevel 값이 채워진다.
 *   IN_PROGRESS/ABANDONED 상태에서는 null로 반환되며,
 *   completedSessions/totalSessions로 진행률을 파악할 수 있다.
 *
 * ── TOEIC Speaking 점수 체계 ───────────────────────────────────────────────────
 *   0~200점 (10점 단위), 레벨 1~8
 *   Feedback API 구현 후 각 세션의 scoreOverall 평균을 기반으로 ExamSession에 저장된다.
 *
 * ── 파트별 피드백 ────────────────────────────────────────────────────────────
 *   Feedback API 미구현 단계에서는 partResults 내 averageScore가 null이다.
 *   Feedback API 완성 후 각 파트 세션들의 scoreOverall 평균이 채워진다.
 */
public record ExamResultResponse(
        Long examSessionId,
        String status,
        Short predictedScore,           // COMPLETED 시 설정 (0~200), 미완료 시 null
        Short predictedLevel,           // COMPLETED 시 설정 (1~8), 미완료 시 null
        LocalDateTime completedAt,
        int totalSessions,
        int completedSessions,
        List<PartResult> partResults    // 파트별 완료 현황
) {

    /**
     * 파트별 결과 요약.
     * averageScore: 해당 파트 세션들의 scoreOverall 평균. Feedback 없으면 null.
     */
    public record PartResult(
            Short partId,
            int totalSessions,
            int completedSessions,
            Double averageScore         // Feedback API 완성 후 채워짐. 현재는 null.
    ) {}

    public static ExamResultResponse from(ExamSession exam, List<PracticeSession> sessions) {
        // partId 기준 그루핑 (파트 순서 유지)
        java.util.Map<Short, List<PracticeSession>> byPart = sessions.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        s -> s.getQuestion().getPartId(),
                        java.util.LinkedHashMap::new,
                        java.util.stream.Collectors.toList()
                ));

        List<PartResult> partResults = byPart.entrySet().stream()
                .map(entry -> {
                    List<PracticeSession> ps = entry.getValue();
                    int done = (int) ps.stream()
                            .filter(s -> s.getStatus() == PracticeSessionStatus.DONE)
                            .count();
                    // averageScore: Feedback API 구현 후 채울 예정
                    return new PartResult(entry.getKey(), ps.size(), done, null);
                })
                .toList();

        int totalDone = (int) sessions.stream()
                .filter(s -> s.getStatus() == PracticeSessionStatus.DONE)
                .count();

        return new ExamResultResponse(
                exam.getId(),
                exam.getStatus().name(),
                exam.getPredictedScore(),
                exam.getPredictedLevel(),
                exam.getCompletedAt(),
                sessions.size(),
                totalDone,
                partResults
        );
    }
}
