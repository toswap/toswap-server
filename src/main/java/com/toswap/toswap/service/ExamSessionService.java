package com.toswap.toswap.service;

import com.toswap.toswap.dto.response.*;
import com.toswap.toswap.entity.*;
import com.toswap.toswap.exception.BusinessException;
import com.toswap.toswap.exception.ErrorCode;
import com.toswap.toswap.repository.ExamSessionRepository;
import com.toswap.toswap.repository.PracticeSessionRepository;
import com.toswap.toswap.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/**
 * 시험 세션(전체 시험 모드) 서비스.
 *
 * ── TOEIC Speaking 시험 구성 ────────────────────────────────────────────────
 *
 *   Part 1  Read a Text Aloud          2문제 (독립 문제, prep=45s, resp=45s)
 *   Part 2  Describe a Picture         1문제 (독립 문제, prep=45s, resp=30s)
 *   Part 3  Respond to Questions       3문제 (그룹, Q1·Q2 resp=15s, Q3 resp=30s)
 *   Part 4  Respond to Questions       3문제 (그룹, Q1·Q2 resp=15s, Q3 resp=30s)
 *           Using Information Provided
 *   Part 5  Express an Opinion         2문제 (그룹, prep=45s, resp=60s)
 *   ─────────────────────────────────  총 11 PracticeSession 생성
 *
 * ── 시험 생애주기 ────────────────────────────────────────────────────────────
 *
 *   1. start()      → ExamSession(IN_PROGRESS) + PracticeSession 11개(PENDING)
 *   2. 프론트가 각 세션의 음성을 순서대로 제출 (Feedback API)
 *      → 각 PracticeSession: PENDING → PROCESSING → DONE
 *   3. (Feedback API가) 모든 세션 DONE 확인 후 checkAndComplete() 호출
 *      → ExamSession: IN_PROGRESS → EVALUATING → COMPLETED (점수 계산 완료)
 *   4. abandon()    → ExamSession: IN_PROGRESS → ABANDONED
 *
 * ── 중복 시험 방지 ───────────────────────────────────────────────────────────
 *   IN_PROGRESS 시험이 이미 있으면 새 시험을 시작할 수 없다 (400 에러).
 *   명시적으로 abandon() 후 새 시험을 시작해야 한다.
 */
@Service
@RequiredArgsConstructor
public class ExamSessionService {

    private final ExamSessionRepository examSessionRepository;
    private final PracticeSessionRepository practiceSessionRepository;
    private final UserRepository userRepository;
    private final QuestionService questionService;

    // ══════════════════════════════════════════════════════════════════════════
    // 시험 시작
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 시험을 시작한다. Gemini API를 6회 호출하므로 18~48초 소요될 수 있다.
     *   (Part 1 ×2회, Part 2 ×1회, Part 3/4/5 ×3회)
     *
     * 이미 IN_PROGRESS 시험이 있으면 400 에러를 반환한다.
     */
    @Transactional
    public ExamStartResponse start(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));

        // 이미 진행 중인 시험 있으면 시작 불가
        examSessionRepository.findByUserIdAndStatus(userId, ExamSessionStatus.IN_PROGRESS)
                .ifPresent(existing -> {
                    throw new BusinessException(ErrorCode.EXAM_ALREADY_IN_PROGRESS);
                });

        // 새 시험 세션 생성
        ExamSession examSession = examSessionRepository.save(
                ExamSession.builder().user(user).build()
        );

        List<ExamStartResponse.PartSection> parts = new ArrayList<>();

        // ── Part 1: 독립 문제 2개 ──────────────────────────────────────────
        parts.add(buildSinglePartSection((short) 1, 2, user, examSession));

        // ── Part 2: 독립 문제 1개 ──────────────────────────────────────────
        parts.add(buildSinglePartSection((short) 2, 1, user, examSession));

        // ── Part 3/4/5: 그룹 문제 ─────────────────────────────────────────
        parts.add(buildGroupPartSection((short) 3, user, examSession));
        parts.add(buildGroupPartSection((short) 4, user, examSession));
        parts.add(buildGroupPartSection((short) 5, user, examSession));

        return new ExamStartResponse(examSession.getId(), parts);
    }

    // ── 독립 문제(Part 1/2) 파트 섹션 생성 ────────────────────────────────

    /**
     * @param count Part 1: 2, Part 2: 1
     */
    private ExamStartResponse.PartSection buildSinglePartSection(
            short partId, int count, User user, ExamSession examSession) {

        List<ExamStartResponse.SessionItem> items = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Question question = questionService.generateAndSaveSingleQuestion(partId);
            PracticeSession session = practiceSessionRepository.save(
                    PracticeSession.builder()
                            .user(user)
                            .question(question)
                            .examSession(examSession)   // 시험 세션에 소속
                            // questionGroup = null (독립 문제)
                            .build()
            );
            items.add(toSessionItem(session, question));
        }
        return new ExamStartResponse.PartSection(partId, null, null, items);
    }

    // ── 그룹 문제(Part 3/4/5) 파트 섹션 생성 ──────────────────────────────

    private ExamStartResponse.PartSection buildGroupPartSection(
            short partId, User user, ExamSession examSession) {

        QuestionGroup group = questionService.generateAndSaveGroupQuestions(partId);
        List<Question> questions = group.getQuestions();

        List<PracticeSession> sessions = questions.stream()
                .map(q -> PracticeSession.builder()
                        .user(user)
                        .question(q)
                        .questionGroup(group)
                        .examSession(examSession)   // 시험 세션에 소속
                        .build())
                .toList();
        List<PracticeSession> savedSessions = practiceSessionRepository.saveAll(sessions);

        List<ExamStartResponse.SessionItem> items = IntStream.range(0, questions.size())
                .mapToObj(i -> toSessionItem(savedSessions.get(i), questions.get(i)))
                .toList();

        return new ExamStartResponse.PartSection(
                partId, group.getId(), group.getContextContent(), items);
    }

    private ExamStartResponse.SessionItem toSessionItem(PracticeSession session, Question q) {
        return new ExamStartResponse.SessionItem(
                session.getId(),
                q.getId(),
                q.getSequenceNo(),
                q.getContent(),
                q.getImageUrl(),
                q.getPrepSeconds(),
                q.getResponseSeconds()
        );
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 조회
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 시험 상태 조회.
     * 각 파트별 세션의 PENDING/DONE 현황을 반환해서 프론트가 이어하기를 할 수 있다.
     */
    @Transactional(readOnly = true)
    public ExamSessionStatusResponse getStatus(Long examSessionId, Long userId) {
        ExamSession exam = findExamByIdAndUser(examSessionId, userId);
        List<PracticeSession> sessions =
                practiceSessionRepository.findByExamSessionIdWithDetails(examSessionId);
        return ExamSessionStatusResponse.from(exam, sessions);
    }

    /**
     * 시험 결과 조회.
     * COMPLETED 시 predictedScore/predictedLevel 반환.
     * 미완료 시에도 현재 진행 상황(completedSessions)을 반환한다.
     */
    @Transactional(readOnly = true)
    public ExamResultResponse getResult(Long examSessionId, Long userId) {
        ExamSession exam = findExamByIdAndUser(examSessionId, userId);
        List<PracticeSession> sessions =
                practiceSessionRepository.findByExamSessionIdWithDetails(examSessionId);
        return ExamResultResponse.from(exam, sessions);
    }

    /** 내 시험 목록 (최신순) */
    @Transactional(readOnly = true)
    public List<ExamHistoryItemResponse> getHistory(Long userId) {
        return examSessionRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(ExamHistoryItemResponse::from)
                .toList();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 상태 변경
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 시험을 포기한다. IN_PROGRESS 상태인 시험만 포기 가능.
     */
    @Transactional
    public void abandon(Long examSessionId, Long userId) {
        ExamSession exam = findExamByIdAndUser(examSessionId, userId);

        if (exam.getStatus() != ExamSessionStatus.IN_PROGRESS) {
            throw new BusinessException(ErrorCode.EXAM_SESSION_ALREADY_FINISHED);
        }
        exam.abandon();
    }

    /**
     * 모든 세션이 완료되었을 때 시험을 COMPLETED 상태로 전환한다.
     * Feedback API에서 마지막 세션을 DONE 처리한 뒤 이 메서드를 호출한다.
     *
     * @param predictedScore Feedback 점수들의 평균을 기반으로 계산된 TOEIC 예상 점수
     * @param predictedLevel 점수에 해당하는 레벨 (1~8)
     */
    @Transactional
    public void checkAndComplete(Long examSessionId, Short predictedScore, Short predictedLevel) {
        examSessionRepository.findById(examSessionId).ifPresent(exam -> {
            List<PracticeSession> sessions =
                    practiceSessionRepository.findByExamSessionIdWithDetails(examSessionId);

            boolean allDone = sessions.stream()
                    .allMatch(s -> s.getStatus() == PracticeSessionStatus.DONE);

            if (allDone) {
                exam.complete(predictedScore, predictedLevel);
            }
        });
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 공통 헬퍼
    // ══════════════════════════════════════════════════════════════════════════

    private ExamSession findExamByIdAndUser(Long examSessionId, Long userId) {
        return examSessionRepository.findByIdAndUserId(examSessionId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EXAM_SESSION_NOT_FOUND));
    }
}
