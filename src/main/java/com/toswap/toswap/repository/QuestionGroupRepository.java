package com.toswap.toswap.repository;

import com.toswap.toswap.entity.QuestionGroup;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface QuestionGroupRepository extends JpaRepository<QuestionGroup, Long> {

    /**
     * 그룹과 하위 질문 목록을 한 번의 쿼리(JOIN FETCH)로 조회한다.
     *
     * 왜 필요한가:
     * - questions 컬렉션은 LAZY 로딩이므로, 기본 findById 후 questions에 접근하면
     *   N+1 쿼리가 발생한다 (그룹 1번 + 질문 N번).
     * - JOIN FETCH를 사용하면 단 1번의 쿼리로 그룹과 질문을 모두 가져온다.
     */
    @Query("SELECT qg FROM QuestionGroup qg LEFT JOIN FETCH qg.questions WHERE qg.id = :id")
    Optional<QuestionGroup> findByIdWithQuestions(@Param("id") Long id);

    long countByPartId(Short partId);

    // Step 1: 랜덤 그룹 ID만 추출 (questions 로딩 없이 — JOIN FETCH + Pageable 조합 방지)
    @Query("SELECT qg FROM QuestionGroup qg WHERE qg.partId = :partId ORDER BY FUNCTION('RAND')")
    List<QuestionGroup> findRandom(@Param("partId") Short partId, Pageable pageable);
    // Step 2: 추출한 ID로 findByIdWithQuestions() 호출해서 questions까지 로딩
}
