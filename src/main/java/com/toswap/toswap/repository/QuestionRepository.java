package com.toswap.toswap.repository;

import com.toswap.toswap.entity.Question;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    long countByPartIdAndQuestionGroupIsNull(Short partId);

    // 풀에서 1개 랜덤 추출 (Part 1/2 전용 — 그룹 미소속 문제만)
    @Query("SELECT q FROM Question q WHERE q.partId = :partId AND q.questionGroup IS NULL ORDER BY FUNCTION('random')")
    List<Question> findRandomStandalone(@Param("partId") Short partId, Pageable pageable);

    // 이미 사용한 ID를 제외하고 랜덤 추출 — 같은 시험 내 중복 질문 방지
    @Query("SELECT q FROM Question q WHERE q.partId = :partId AND q.questionGroup IS NULL AND q.id NOT IN :excludeIds ORDER BY FUNCTION('random')")
    List<Question> findRandomStandaloneExcluding(@Param("partId") Short partId, @Param("excludeIds") List<Long> excludeIds, Pageable pageable);
}
