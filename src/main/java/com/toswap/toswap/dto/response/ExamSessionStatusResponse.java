package com.toswap.toswap.dto.response;

import com.toswap.toswap.entity.ExamSession;
import com.toswap.toswap.entity.PracticeSession;
import com.toswap.toswap.entity.PracticeSessionStatus;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 시험 상태 조회(GET /api/exam-sessions/{id}) 응답.
 *
 * 시험 재진입 시 어느 질문까지 제출했는지 파악하는 용도.
 * sessions 내 status=PENDING인 첫 번째 세션부터 이어서 진행하면 된다.
 */
public record ExamSessionStatusResponse(
        Long examSessionId,
        String status,              // IN_PROGRESS, EVALUATING, COMPLETED, ABANDONED
        LocalDateTime startedAt,
        int totalSessions,          // 항상 11
        int completedSessions,      // DONE 상태인 세션 수
        List<PartStatus> parts
) {

    /** 파트별 진행 상태 요약 */
    public record PartStatus(
            Short partId,
            Long questionGroupId,
            String contextContent,          // Part 1/2: null
            int totalSessions,
            int completedSessions,
            List<SessionStatus> sessions
    ) {}

    /** 개별 세션 상태 */
    public record SessionStatus(
            Long sessionId,
            Short sequenceNo,               // Part 1/2: null
            String questionContent,
            String status                   // PENDING, PROCESSING, DONE, ERROR
    ) {}

    /**
     * 시험 엔티티 + 연습 세션 목록으로 응답 DTO를 생성한다.
     *
     * sessions는 partId ASC, sequenceNo ASC NULLS FIRST 정렬 상태로 들어온다고 가정한다.
     * (findByExamSessionIdWithDetails 쿼리가 이 순서를 보장한다)
     */
    public static ExamSessionStatusResponse from(ExamSession exam, List<PracticeSession> sessions) {
        // partId 기준 그루핑. LinkedHashMap으로 파트 순서(1→2→3→4→5) 유지
        Map<Short, List<PracticeSession>> byPart = sessions.stream()
                .collect(Collectors.groupingBy(
                        s -> s.getQuestion().getPartId(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<PartStatus> partStatuses = byPart.entrySet().stream()
                .map(entry -> toPartStatus(entry.getKey(), entry.getValue()))
                .toList();

        int totalDone = (int) sessions.stream()
                .filter(s -> s.getStatus() == PracticeSessionStatus.DONE)
                .count();

        return new ExamSessionStatusResponse(
                exam.getId(),
                exam.getStatus().name(),
                exam.getCreatedAt(),
                sessions.size(),
                totalDone,
                partStatuses
        );
    }

    private static PartStatus toPartStatus(Short partId, List<PracticeSession> partSessions) {
        Long groupId = partSessions.get(0).getQuestionGroup() != null
                ? partSessions.get(0).getQuestionGroup().getId() : null;
        String contextContent = partSessions.get(0).getQuestionGroup() != null
                ? partSessions.get(0).getQuestionGroup().getContextContent() : null;

        int done = (int) partSessions.stream()
                .filter(s -> s.getStatus() == PracticeSessionStatus.DONE)
                .count();

        List<SessionStatus> sessionStatuses = partSessions.stream()
                .map(s -> new SessionStatus(
                        s.getId(),
                        s.getQuestion().getSequenceNo(),
                        s.getQuestion().getContent(),
                        s.getStatus().name()
                ))
                .toList();

        return new PartStatus(partId, groupId, contextContent,
                partSessions.size(), done, sessionStatuses);
    }
}
