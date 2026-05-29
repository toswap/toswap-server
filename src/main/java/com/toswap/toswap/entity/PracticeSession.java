package com.toswap.toswap.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * 단일 질문에 대한 연습 시도를 나타내는 엔티티.
 *
 * ── 파트별 생성 방식 ────────────────────────────────────────────────────────
 *
 * Part 1/2 (독립 문제):
 *   - PracticeSession 1개 생성
 *   - question  = 해당 Question
 *   - questionGroup = null
 *
 * Part 3/4/5 (그룹 문제):
 *   - PracticeSession N개 생성 (Q1→세션1, Q2→세션2, Q3→세션3)
 *   - question  = 각 개별 Question (Q1 or Q2 or Q3)
 *   - questionGroup = 공유 QuestionGroup (공통 배경 보유)
 *   → 같은 questionGroup을 가진 세션들이 "한 번의 Part 3 연습"을 구성
 *
 * ── 상태 흐름 ───────────────────────────────────────────────────────────────
 *   PENDING → (음성 제출) → PROCESSING → (AI 평가 완료) → DONE
 *                                       → (AI 평가 실패) → ERROR
 */
@Entity
@Table(name = "practice_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PracticeSession extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 이 세션의 소유자
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 이 세션이 답변할 개별 질문 (항상 존재)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    /**
     * Part 3/4/5에서 같은 연습 회차에 속한 세션들을 묶는 그룹.
     * Part 1/2는 null. Part 3/4/5는 같은 QuestionGroup을 공유.
     *
     * 예) Part 3 연습 시작 시:
     *   session_q1.questionGroup = group(id=5)
     *   session_q2.questionGroup = group(id=5)
     *   session_q3.questionGroup = group(id=5)
     * → groupId=5 로 조회하면 한 세트의 세 세션을 모두 찾을 수 있음
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_group_id")
    private QuestionGroup questionGroup;

    // 전체 시험 모드에서 소속 시험 세션 (일반 연습은 null)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_session_id")
    private ExamSession examSession;

    // 제출된 음성 파일 경로 (S3 URL 또는 로컬 경로). 아직 미제출이면 null.
    @Column(length = 500)
    private String audioPath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PracticeSessionStatus status = PracticeSessionStatus.PENDING;

    // ── 상태 변경 메서드 ──────────────────────────────────────────────────────

    /** 음성 파일 수신 → AI 평가 시작 */
    public void startProcessing(String audioPath) {
        this.audioPath = audioPath;
        this.status = PracticeSessionStatus.PROCESSING;
    }

    /** AI 평가 완료 */
    public void complete() {
        this.status = PracticeSessionStatus.DONE;
    }

    /** AI 평가 실패 */
    public void markError() {
        this.status = PracticeSessionStatus.ERROR;
    }
}
