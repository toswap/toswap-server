package com.toswap.toswap.dto.response;

import com.toswap.toswap.entity.PracticeSession;
import com.toswap.toswap.entity.PracticeSessionStatus;
import com.toswap.toswap.entity.Question;
import com.toswap.toswap.entity.QuestionGroup;

import java.time.LocalDateTime;

/**
 * 연습 세션 단건 상세 조회 응답 DTO (GET /api/practice-sessions/{id}).
 *
 * 세션 상태 + 연결된 질문 정보 + 그룹 배경(있는 경우)을 모두 포함한다.
 * 주로 연습 화면을 복원하거나 피드백 확인 화면에서 사용한다.
 *
 * questionGroupId / contextContent:
 *   Part 1/2 → null (독립 문제, 배경 없음)
 *   Part 3/4/5 → 그룹 ID와 공통 배경 텍스트
 */
public record PracticeSessionDetailResponse(
        Long sessionId,
        PracticeSessionStatus status,
        Short partId,
        Short sequenceNo,       // Part 1/2: null / Part 3/4/5: 1~3
        Long questionGroupId,
        String contextContent,
        Long questionId,
        String content,
        String imageUrl,        // Part 2 전용
        Short prepSeconds,
        Short responseSeconds,
        LocalDateTime createdAt
) {
    public static PracticeSessionDetailResponse from(PracticeSession session) {
        Question q = session.getQuestion();
        QuestionGroup group = session.getQuestionGroup();

        return new PracticeSessionDetailResponse(
                session.getId(),
                session.getStatus(),
                q.getPartId(),
                q.getSequenceNo(),
                group != null ? group.getId() : null,
                group != null ? group.getContextContent() : null,
                q.getId(),
                q.getContent(),
                q.getImageUrl(),
                q.getPrepSeconds(),
                q.getResponseSeconds(),
                session.getCreatedAt()
        );
    }
}
