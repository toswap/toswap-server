package com.toswap.toswap.repository;

import com.toswap.toswap.entity.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

import java.util.Optional;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    Optional<Feedback> findByPracticeSessionId(Long sessionId);

    /**
     * 시험 세션에 속한 모든 피드백 조회.
     * ExamSession 완료 시 점수 계산에 사용.
     * Spring Data JPA가 practiceSession → examSession.id 경로를 자동으로 조인 처리.
     */
    List<Feedback> findByPracticeSessionExamSessionId(Long examSessionId);
}
