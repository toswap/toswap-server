package com.toswap.toswap.repository;

import com.toswap.toswap.entity.Question;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    long countByPartIdAndQuestionGroupIsNull(Short partId);

    // ORDER BY RAND()로 풀에서 1개 랜덤 추출 (Part 1/2 전용 — 그룹 미소속 문제만)
    @Query("SELECT q FROM Question q WHERE q.partId = :partId AND q.questionGroup IS NULL ORDER BY FUNCTION('RAND')")
    List<Question> findRandomStandalone(@Param("partId") Short partId, Pageable pageable);
}
