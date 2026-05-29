package com.toswap.toswap.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * TOEIC Speaking 개별 질문 엔티티.
 *
 * ── Part 1/2: 독립 문제 ─────────────────────────────────────────────────────
 *   questionGroup = null  (그룹에 속하지 않음)
 *   sequenceNo    = null
 *
 *   Part 1 - Read a Text Aloud
 *     content   = 읽을 영어 지문 (비즈니스 공지/광고 등)
 *     prepSeconds = 45, responseSeconds = 45
 *
 *   Part 2 - Describe a Picture
 *     content      = "Please describe the following photograph."
 *     imageUrl     = Unsplash에서 가져온 사진 URL
 *     imageKeyword = Unsplash 검색 키워드 (예: "office meeting")
 *     prepSeconds = 45, responseSeconds = 30
 *
 * ── Part 3/4/5: 그룹 소속 문제 ─────────────────────────────────────────────
 *   questionGroup = 소속 QuestionGroup (공통 배경 포함)
 *   sequenceNo    = 그룹 내 순서 (1, 2, 3)
 *
 *   Part 3 - Respond to Questions
 *     sequenceNo 1, 2: prepSeconds = 3, responseSeconds = 15
 *     sequenceNo 3:    prepSeconds = 3, responseSeconds = 30  ← 마지막 질문은 더 길게
 *
 *   Part 4 - Respond to Questions Using Information Provided
 *     sequenceNo 1, 2: prepSeconds = 3, responseSeconds = 15
 *     sequenceNo 3:    prepSeconds = 3, responseSeconds = 30
 *
 *   Part 5 - Express an Opinion
 *     sequenceNo 1, 2: prepSeconds = 45, responseSeconds = 60
 */
@Entity
@Table(name = "questions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Question extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 파트 번호 (1~5)
    @Column(nullable = false)
    private Short partId;

    /**
     * 소속 그룹. Part 3/4/5 질문은 반드시 그룹에 속하고, Part 1/2는 null.
     *
     * FetchType.LAZY: Question 단독 조회 시 QuestionGroup을 자동으로 로딩하지 않음.
     * 그룹 정보가 필요하면 QuestionGroupRepository.findByIdWithQuestions()로 한번에 조회.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private QuestionGroup questionGroup;

    /**
     * 그룹 내 순서 번호 (1, 2, 3).
     * Part 1/2는 null.
     *
     * 이 값이 중요한 이유:
     * - Part 3/4에서 Q1, Q2는 답변 시간 15초, Q3는 30초로 다르다.
     * - 준비 시간/답변 시간을 sequenceNo에 따라 다르게 저장해야 한다.
     */
    @Column
    private Short sequenceNo;

    // 질문 본문 텍스트
    // Part 1: 읽을 지문 전체
    // Part 2: 묘사 지시문 ("Please describe the following photograph.")
    // Part 3/4/5: 질문 문장 그 자체
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    // Part 2 전용: Unsplash에서 검색한 이미지 URL. 다른 파트는 null.
    @Column(length = 500)
    private String imageUrl;

    // Part 2 전용: Unsplash 검색에 사용한 키워드. 이미지 재검색 등 추후 활용 가능.
    @Column(length = 100)
    private String imageKeyword;

    // 문제를 보고 답변을 준비하는 시간 (초)
    @Column(nullable = false)
    private Short prepSeconds;

    // 실제로 말하는 답변 시간 (초)
    @Column(nullable = false)
    private Short responseSeconds;
}
