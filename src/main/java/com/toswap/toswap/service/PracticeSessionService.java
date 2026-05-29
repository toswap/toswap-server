package com.toswap.toswap.service;

import com.toswap.toswap.dto.request.PracticeSessionStartRequest;
import com.toswap.toswap.dto.response.PracticeHistoryItemResponse;
import com.toswap.toswap.dto.response.PracticeSessionDetailResponse;
import com.toswap.toswap.dto.response.PracticeStartResponse;
import com.toswap.toswap.entity.*;
import com.toswap.toswap.exception.BusinessException;
import com.toswap.toswap.exception.ErrorCode;
import com.toswap.toswap.repository.PracticeSessionRepository;
import com.toswap.toswap.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.IntStream;

/**
 * 연습 세션 생성 · 조회 서비스.
 *
 * ── 연습 세션의 생애주기 ────────────────────────────────────────────────────
 *
 * 1. start()  → 문제 생성 + 세션 생성 (PENDING 상태)
 * 2. (프론트) 사용자가 음성 녹음 후 제출
 * 3. (Feedback API) 음성 파일 수신 → 세션 PROCESSING → Gemini 평가 → DONE/ERROR
 *
 * ── Part별 세션 생성 개수 ───────────────────────────────────────────────────
 *
 * Part 1/2: 1개 생성. question=단독문제, questionGroup=null
 * Part 3/4: 3개 생성. question=Q1·Q2·Q3, questionGroup=공유그룹
 * Part 5:   2개 생성. question=Q1·Q2, questionGroup=공유그룹
 *
 * ── 히스토리 그루핑 전략 ────────────────────────────────────────────────────
 *
 * getHistory()는 DB에서 세션 목록을 평탄하게 가져온 뒤,
 * questionGroup 기준으로 서버에서 그루핑해서 반환한다.
 * → 프론트는 파트별로 "한 번의 연습 = 한 줄" 로 히스토리를 표시 가능.
 */
@Service
@RequiredArgsConstructor
public class PracticeSessionService {

    private final PracticeSessionRepository practiceSessionRepository;
    private final UserRepository userRepository;
    private final QuestionService questionService;

    // ══════════════════════════════════════════════════════════════════════════
    // 연습 시작
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 연습 세션을 시작한다.
     * Gemini API로 문제를 생성한 뒤 세션을 저장하므로 3~8초 소요될 수 있다.
     */
    @Transactional
    public PracticeStartResponse start(PracticeSessionStartRequest request, Long userId) {
        short partId = request.partId().shortValue();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));

        return (partId == 1 || partId == 2)
                ? startSinglePractice(partId, user)
                : startGroupPractice(partId, user);
    }

    // ── Part 1/2: 독립 문제 1개 ────────────────────────────────────────────

    private PracticeStartResponse startSinglePractice(short partId, User user) {
        // 1. Gemini로 문제 생성 + 저장
        Question question = questionService.generateAndSaveSingleQuestion(partId);

        // 2. 세션 1개 생성
        PracticeSession session = practiceSessionRepository.save(
                PracticeSession.builder()
                        .user(user)
                        .question(question)
                        // questionGroup = null (독립 문제)
                        .build()
        );

        return new PracticeStartResponse(
                partId,
                null,   // groupId 없음
                null,   // contextContent 없음
                List.of(toSessionItem(session, question))
        );
    }

    // ── Part 3/4/5: 그룹 문제 N개 ─────────────────────────────────────────

    private PracticeStartResponse startGroupPractice(short partId, User user) {
        // 1. Gemini로 그룹 문제(공통 배경 + 질문들) 생성 + 저장
        //    반환된 group.getQuestions()는 sequenceNo 오름차순으로 정렬되어 있음
        QuestionGroup group = questionService.generateAndSaveGroupQuestions(partId);
        List<Question> questions = group.getQuestions();

        // 2. 각 질문마다 세션 1개씩 생성 (saveAll로 한 번에 저장)
        List<PracticeSession> sessionEntities = questions.stream()
                .map(q -> PracticeSession.builder()
                        .user(user)
                        .question(q)
                        .questionGroup(group)  // 같은 그룹으로 묶음
                        .build())
                .toList();

        List<PracticeSession> savedSessions = practiceSessionRepository.saveAll(sessionEntities);

        // 3. 응답 구성: sessions 리스트는 questions 순서(sequenceNo)와 동일
        //    IntStream으로 인덱스를 유지하며 매핑
        List<PracticeStartResponse.SessionItem> sessionItems = IntStream
                .range(0, questions.size())
                .mapToObj(i -> toSessionItem(savedSessions.get(i), questions.get(i)))
                .toList();

        return new PracticeStartResponse(
                partId,
                group.getId(),
                group.getContextContent(),
                sessionItems
        );
    }

    // ── SessionItem 변환 헬퍼 ──────────────────────────────────────────────

    private PracticeStartResponse.SessionItem toSessionItem(PracticeSession session, Question q) {
        return new PracticeStartResponse.SessionItem(
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
     * 세션 단건 상세 조회.
     * 다른 사용자의 세션은 404로 처리 (보안: 세션 ID 추측 방어).
     */
    @Transactional(readOnly = true)
    public PracticeSessionDetailResponse getDetail(Long sessionId, Long userId) {
        PracticeSession session = practiceSessionRepository
                .findByIdAndUserIdWithDetails(sessionId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRACTICE_SESSION_NOT_FOUND));
        return PracticeSessionDetailResponse.from(session);
    }

    /**
     * 내 연습 기록 목록 조회 (최신순).
     *
     * 같은 questionGroup에 속한 세션들(Part 3/4/5)은 1개의 히스토리 항목으로 그루핑.
     * Part 1/2 독립 세션은 각각 1개의 항목.
     */
    @Transactional(readOnly = true)
    public List<PracticeHistoryItemResponse> getHistory(Long userId) {
        List<PracticeSession> sessions = practiceSessionRepository.findHistoryByUserId(userId);

        // 그루핑 키: 그룹 세션 → "g-{groupId}", 독립 세션 → "s-{sessionId}"
        // LinkedHashMap으로 createdAt DESC 순서 유지 (DB 정렬 결과 그대로)
        Map<String, List<PracticeSession>> grouped = new LinkedHashMap<>();
        for (PracticeSession session : sessions) {
            String key = session.getQuestionGroup() != null
                    ? "g-" + session.getQuestionGroup().getId()
                    : "s-" + session.getId();
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(session);
        }

        return grouped.values().stream()
                .map(this::toHistoryItem)
                .toList();
    }

    // ── 히스토리 항목 변환 ─────────────────────────────────────────────────

    private PracticeHistoryItemResponse toHistoryItem(List<PracticeSession> group) {
        // 그룹 내 첫 번째 세션 = Q1 (또는 Part 1/2 단독 세션)
        PracticeSession first = group.get(0);

        Long groupId = first.getQuestionGroup() != null
                ? first.getQuestionGroup().getId() : null;

        int doneCount = (int) group.stream()
                .filter(s -> s.getStatus() == PracticeSessionStatus.DONE)
                .count();

        // 전체 상태: 모두 완료 → DONE, 하나라도 에러 → ERROR, 그 외 → PENDING
        PracticeSessionStatus overallStatus;
        if (doneCount == group.size()) {
            overallStatus = PracticeSessionStatus.DONE;
        } else if (group.stream().anyMatch(s -> s.getStatus() == PracticeSessionStatus.ERROR)) {
            overallStatus = PracticeSessionStatus.ERROR;
        } else {
            overallStatus = PracticeSessionStatus.PENDING;
        }

        return new PracticeHistoryItemResponse(
                first.getQuestion().getPartId(),
                first.getId(),
                groupId,
                overallStatus,
                group.size(),
                doneCount,
                first.getCreatedAt()
        );
    }
}
