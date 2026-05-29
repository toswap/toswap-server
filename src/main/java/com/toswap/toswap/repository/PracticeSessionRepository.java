package com.toswap.toswap.repository;

import com.toswap.toswap.entity.PracticeSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PracticeSessionRepository extends JpaRepository<PracticeSession, Long> {

    /**
     * 세션 단건 조회 (question, questionGroup JOIN FETCH).
     *
     * 서비스 레이어에서 question.content, questionGroup.contextContent 등에
     * 접근할 때 N+1이 발생하지 않도록 한 쿼리로 함께 가져온다.
     * userId 조건을 함께 확인해서 다른 사용자의 세션은 조회하지 못하게 막는다.
     */
    @Query("""
            SELECT ps FROM PracticeSession ps
            JOIN FETCH ps.question
            LEFT JOIN FETCH ps.questionGroup
            WHERE ps.id = :id AND ps.user.id = :userId
            """)
    Optional<PracticeSession> findByIdAndUserIdWithDetails(
            @Param("id") Long id,
            @Param("userId") Long userId);

    /**
     * 사용자의 연습 기록 목록 (최신순, question/questionGroup 포함).
     *
     * examSession이 null인 것만 조회 → 일반 연습 세션만 반환 (시험 세션 제외).
     * question, questionGroup을 JOIN FETCH해서 히스토리 변환 시 추가 쿼리 없이 처리.
     */
    @Query("""
            SELECT ps FROM PracticeSession ps
            JOIN FETCH ps.question
            LEFT JOIN FETCH ps.questionGroup
            WHERE ps.user.id = :userId AND ps.examSession IS NULL
            ORDER BY ps.createdAt DESC
            """)
    List<PracticeSession> findHistoryByUserId(@Param("userId") Long userId);

    // 시험 세션 내의 모든 연습 세션 조회 (ExamSession 생성 시 사용)
    List<PracticeSession> findByExamSessionId(Long examSessionId);
}
