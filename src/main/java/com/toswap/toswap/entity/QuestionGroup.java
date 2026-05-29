package com.toswap.toswap.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * TOEIC Speaking Part 3 / 4 / 5의 "문제 세트" 엔티티.
 *
 * ── TOEIC Speaking 파트별 구조 ──────────────────────────────────────────────
 *
 * Part 1 (Read a Text Aloud, 2문항)
 *   → 각 지문이 독립적. QuestionGroup 없음. Question 단독 존재.
 *
 * Part 2 (Describe a Picture, 1문항)
 *   → 사진 1장 + 묘사 지시문. QuestionGroup 없음. Question 단독 존재.
 *
 * Part 3 (Respond to Questions, 3문항)  ← 이 엔티티 사용
 *   contextContent: "Imagine you are talking with someone conducting a survey about [주제]."
 *   questions[0] → Q1: 단순한 질문 (prep 3s / response 15s)
 *   questions[1] → Q2: 중간 난이도 (prep 3s / response 15s)
 *   questions[2] → Q3: 심화 질문   (prep 3s / response 30s)
 *
 * Part 4 (Respond to Questions Using Information Provided, 3문항)  ← 이 엔티티 사용
 *   contextContent: 표/일정표/광고 등의 텍스트 문서
 *   questions[0] → Q1: 단순 조회 (prep 3s / response 15s)
 *   questions[1] → Q2: 단순 조회 또는 추론 (prep 3s / response 15s)
 *   questions[2] → Q3: 복합 추론 (prep 3s / response 30s)
 *
 * Part 5 (Express an Opinion, 2문항)  ← 이 엔티티 사용
 *   contextContent: 의견을 표현해야 할 상황 설명 (예: "Many companies allow remote work...")
 *   questions[0] → Q1: 주 의견 표현 (prep 45s / response 60s)
 *   questions[1] → Q2: 심화 의견 표현 (prep 45s / response 60s)
 *
 * ── 총 11문항 계산 ─────────────────────────────────────────────────────────
 *   Part 1: 2문항 + Part 2: 1문항 + Part 3: 3문항 + Part 4: 3문항 + Part 5: 2문항 = 11문항
 */
@Entity
@Table(name = "question_groups")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class QuestionGroup extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Part 번호 (3, 4, 5만 그룹을 가짐)
    @Column(nullable = false)
    private Short partId;

    /**
     * 공통 배경 텍스트.
     *
     * Part 3: 서베이/인터뷰 상황 안내
     *   예) "Imagine you are talking on the phone with someone who is conducting
     *        a survey about online shopping habits."
     *
     * Part 4: 일정표/표/광고 원문
     *   예) "Green Valley Music Festival\nDate: August 10\n2:00 PM - Main Stage..."
     *
     * Part 5: 의견을 요구하는 상황 설명
     *   예) "Many companies now allow employees to work from home permanently."
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String contextContent;

    /**
     * Part 4 전용: 문서를 이미지(표 스크린샷 등)로 제공할 경우의 URL.
     * 현재는 텍스트 문서만 사용하므로 null. 추후 확장을 위해 컬럼을 예약해둔다.
     */
    @Column(length = 500)
    private String contextImageUrl;

    /**
     * 이 그룹에 속한 질문 목록.
     *
     * - cascade = ALL: 그룹 삭제 시 하위 질문들도 함께 삭제
     * - @OrderBy: DB 조회 시 sequenceNo 기준 정렬 → Q1 → Q2 → Q3 순서 보장
     * - fetch = LAZY: 그룹만 필요할 때 질문 목록을 불필요하게 로딩하지 않음
     *   (질문 포함 조회가 필요한 경우 QuestionGroupRepository.findByIdWithQuestions 사용)
     */
    @Builder.Default
    @OneToMany(mappedBy = "questionGroup", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("sequenceNo ASC")
    private List<Question> questions = new ArrayList<>();
}
