package com.toswap.toswap.dto.response;

import com.toswap.toswap.entity.Question;
import com.toswap.toswap.entity.QuestionGroup;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 문제 세트 응답 DTO. 파트에 관계없이 항상 이 형태로 반환된다.
 *
 * ── 프론트엔드 사용 가이드 ──────────────────────────────────────────────────
 *
 * Part 1/2 (독립 문제):
 *   groupId       = null
 *   contextContent = null
 *   questions      = [1개]
 *   → contextContent가 null이면 단독 문제로 처리
 *
 * Part 3 (서베이 인터뷰):
 *   groupId       = {id}
 *   contextContent = "Imagine you are talking with someone conducting a survey about..."
 *   questions      = [Q1(15s), Q2(15s), Q3(30s)] ← responseSeconds 각각 다름
 *   → 화면 상단에 contextContent 표시 후 questions를 순서대로 진행
 *
 * Part 4 (정보 제공 응답):
 *   groupId       = {id}
 *   contextContent = "Green Valley Festival Schedule\n10 AM - Opening Ceremony..."
 *   questions      = [Q1(15s), Q2(15s), Q3(30s)]
 *   → contextContent를 표/문서 형태로 렌더링 후 questions 진행
 *
 * Part 5 (의견 표현):
 *   groupId       = {id}
 *   contextContent = "Many companies now allow employees to work from home..."
 *   questions      = [Q1(60s), Q2(60s)]
 *   → contextContent 표시 후 questions 2개를 순서대로 진행
 */
public record QuestionSetResponse(
        Short partId,

        // Part 3/4/5만 존재. null이면 Part 1/2 독립 문제.
        Long groupId,

        // 공통 배경 텍스트. null이면 Part 1/2 독립 문제.
        String contextContent,

        // 질문 목록: Part 1/2=1개, Part 3/4=3개, Part 5=2개
        List<QuestionResponse> questions,

        // 세트(또는 단독 문제) 생성 시각
        LocalDateTime createdAt
) {

    /**
     * Part 1/2: 독립 문제 1개를 세트 형태로 감싸서 반환.
     * 프론트엔드가 모든 파트를 동일한 응답 구조로 처리할 수 있도록 통일한다.
     */
    public static QuestionSetResponse fromSingle(Question question) {
        return new QuestionSetResponse(
                question.getPartId(),
                null,
                null,
                List.of(QuestionResponse.from(question)),
                question.getCreatedAt()
        );
    }

    /**
     * Part 3/4/5: 그룹 (공통 배경 + 하위 질문 목록)을 세트로 변환.
     * 호출 전에 questions 컬렉션이 로딩된 상태여야 한다 (JOIN FETCH 사용 권장).
     */
    public static QuestionSetResponse fromGroup(QuestionGroup group) {
        List<QuestionResponse> questionResponses = group.getQuestions().stream()
                .map(QuestionResponse::from)
                .toList();

        return new QuestionSetResponse(
                group.getPartId(),
                group.getId(),
                group.getContextContent(),
                questionResponses,
                group.getCreatedAt()
        );
    }
}
