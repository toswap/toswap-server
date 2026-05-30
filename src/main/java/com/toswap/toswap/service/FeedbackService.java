package com.toswap.toswap.service;

import com.toswap.toswap.dto.response.FeedbackResponse;
import com.toswap.toswap.entity.*;
import com.toswap.toswap.exception.BusinessException;
import com.toswap.toswap.exception.ErrorCode;
import com.toswap.toswap.repository.ExamSessionRepository;
import com.toswap.toswap.repository.FeedbackRepository;
import com.toswap.toswap.repository.PracticeSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 음성 평가 서비스.
 *
 * ── 제출 흐름 ────────────────────────────────────────────────────────────────
 *
 * 1. 세션 검증
 *    - 세션이 존재하고 본인 소유인지 확인
 *    - 이미 평가된 세션(DONE/ERROR)이거나 Feedback이 이미 있으면 400 에러
 *
 * 2. PROCESSING 상태로 전환
 *    - session.startProcessing(audioPath) → DB에 즉시 반영
 *    - 클라이언트가 "평가 중" UI를 보여줄 수 있도록 빠르게 상태 변경
 *
 * 3. Gemini 음성 평가 (핵심 비즈니스 로직)
 *    - 오디오 바이트 + 질문 컨텍스트 → GeminiService.evaluateAudio()
 *    - 6개 기준 점수 + 전사 텍스트 + 코멘트 반환
 *
 * 4. Feedback 엔티티 저장 + 세션 DONE 전환
 *
 * 5. 시험 모드 완료 체크 (optional)
 *    - 세션이 ExamSession 소속이면, 해당 시험의 모든 세션이 DONE인지 확인
 *    - 모두 완료 → 평균 점수 계산 → ExamSession COMPLETED 처리
 *
 * ── 오디오 저장 정책 ─────────────────────────────────────────────────────────
 *   현재: S3 미연동이므로 파일명만 audioPath에 기록, 바이트는 Gemini에 직접 전달.
 *   운영: S3 업로드 후 URL을 audioPath에 저장하는 방식으로 확장 예정.
 *
 * ── 점수 체계 ────────────────────────────────────────────────────────────────
 *   Gemini 반환: 각 기준 1~10 (scoreOverall 포함)
 *   시험 최종 점수: scoreOverall 평균 × 20 → 20~200 (TOEIC Speaking 점수 체계)
 *   레벨 변환: calculateLevel() 참고
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackService {

    private final PracticeSessionRepository practiceSessionRepository;
    private final FeedbackRepository feedbackRepository;
    private final ExamSessionRepository examSessionRepository;
    private final GeminiService geminiService;

    // ══════════════════════════════════════════════════════════════════════════
    // 음성 제출 및 평가
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 음성 파일을 제출하고 Gemini AI 평가를 받는다.
     *
     * @param sessionId 평가할 연습 세션 ID
     * @param audio     녹음된 음성 파일 (multipart)
     * @param userId    인증된 사용자 ID
     */
    @Transactional
    public FeedbackResponse submit(Long sessionId, MultipartFile audio, Long userId) {
        // 1. 세션 조회 (question, questionGroup, examSession 모두 로드)
        PracticeSession session = practiceSessionRepository
                .findByIdAndUserIdForFeedback(sessionId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRACTICE_SESSION_NOT_FOUND));

        // 2. 중복 제출 방지
        if (session.getStatus() == PracticeSessionStatus.DONE
                || session.getStatus() == PracticeSessionStatus.PROCESSING) {
            throw new BusinessException(ErrorCode.FEEDBACK_ALREADY_EXISTS);
        }
        // 이미 Feedback 레코드가 있는 경우도 방지 (ERROR → 재시도 허용)
        if (feedbackRepository.findByPracticeSessionId(sessionId).isPresent()) {
            throw new BusinessException(ErrorCode.FEEDBACK_ALREADY_EXISTS);
        }

        // 3. 오디오 바이트 추출
        byte[] audioBytes;
        try {
            audioBytes = audio.getBytes();
        } catch (IOException e) {
            log.error("오디오 파일 읽기 실패 (sessionId={}): {}", sessionId, e.getMessage());
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        // MIME 타입 결정 (브라우저 MediaRecorder 기본값: audio/webm)
        // "audio/webm;codecs=opus" 같은 codecs 파라미터를 제거해야 Gemini가 인식함
        String rawMime = (audio.getContentType() != null && !audio.getContentType().isBlank())
                ? audio.getContentType()
                : "audio/webm";
        String mimeType = rawMime.split(";")[0].trim();

        log.info("음성 평가 시작 (sessionId={}, mimeType={}, audioSize={}bytes)",
                sessionId, mimeType, audioBytes.length);

        // 4. PROCESSING 상태 전환 (파일명을 audioPath에 기록)
        session.startProcessing(audio.getOriginalFilename());

        // 5. Gemini 음성 평가 (실패 시 ERROR 처리)
        GeminiService.FeedbackData feedbackData;
        try {
            GeminiService.EvaluationContext ctx = buildEvaluationContext(session);
            feedbackData = geminiService.evaluateAudio(audioBytes, mimeType, ctx);
        } catch (Exception e) {
            // 평가 실패 시 ERROR로 전환 후 예외 재발생
            session.markError();
            log.error("Gemini 음성 평가 실패 (sessionId={}): {}", sessionId, e.getMessage());
            throw new BusinessException(ErrorCode.FEEDBACK_EVALUATION_FAILED);
        }

        // 6. Feedback 엔티티 저장
        Feedback feedback = feedbackRepository.save(Feedback.builder()
                .practiceSession(session)
                .transcript(feedbackData.transcript())
                .scorePronunciation(feedbackData.scorePronunciation())
                .scoreIntonation(feedbackData.scoreIntonation())
                .scoreGrammar(feedbackData.scoreGrammar())
                .scoreVocabulary(feedbackData.scoreVocabulary())
                .scoreFluency(feedbackData.scoreFluency())
                .scoreContent(feedbackData.scoreContent())
                .scoreOverall(feedbackData.scoreOverall())
                .strengths(feedbackData.strengths())
                .improvements(feedbackData.improvements())
                .detailedComment(feedbackData.detailedComment())
                .build());

        // 7. 세션 DONE 전환
        session.complete();

        // 8. 시험 모드: 모든 세션 완료 여부 확인 및 ExamSession 처리
        if (session.getExamSession() != null) {
            checkAndCompleteExam(session.getExamSession());
        }

        return FeedbackResponse.from(feedback);
    }

    /**
     * 세션에서 Gemini 평가 컨텍스트를 구성한다.
     * Part 3/4/5는 questionGroup의 contextContent를 포함한다.
     */
    private GeminiService.EvaluationContext buildEvaluationContext(PracticeSession session) {
        Question question = session.getQuestion();
        String contextContent = (session.getQuestionGroup() != null)
                ? session.getQuestionGroup().getContextContent()
                : null;

        return new GeminiService.EvaluationContext(
                question.getPartId(),
                question.getContent(),
                contextContent
        );
    }

    /**
     * 시험 세션의 모든 연습 세션이 완료됐는지 확인하고,
     * 완료된 경우 피드백 점수 평균으로 TOEIC 예상 점수를 계산해서 시험을 COMPLETED 처리한다.
     *
     * ── 점수 계산 ──────────────────────────────────────────────────────────────
     *   Feedback.scoreOverall (1~10) 평균 × 20 = predictedScore (20~200)
     *   점수 구간별 레벨:
     *     20~60  → Level 1~2 (Novice)
     *     80~100 → Level 3~4 (Intermediate Low)
     *     110~130 → Level 5 (Intermediate High)
     *     140~160 → Level 6 (Advanced Low)
     *     170~190 → Level 7 (Advanced Mid)
     *     200    → Level 8 (Advanced High)
     */
    private void checkAndCompleteExam(ExamSession examSession) {
        List<PracticeSession> examSessions =
                practiceSessionRepository.findByExamSessionIdWithDetails(examSession.getId());

        boolean allDone = examSessions.stream()
                .allMatch(s -> s.getStatus() == PracticeSessionStatus.DONE);

        if (!allDone) {
            return; // 아직 제출 안 된 세션이 있음
        }

        // 모든 피드백의 scoreOverall 평균 계산
        List<Feedback> feedbacks =
                feedbackRepository.findByPracticeSessionExamSessionId(examSession.getId());

        if (feedbacks.size() != examSessions.size()) {
            // 아직 일부 피드백이 저장되지 않음 (동시성 엣지케이스)
            log.warn("시험 완료 체크: 피드백 수({})와 세션 수({}) 불일치. examSessionId={}",
                    feedbacks.size(), examSessions.size(), examSession.getId());
            return;
        }

        double avgOverall = feedbacks.stream()
                .mapToInt(f -> f.getScoreOverall())
                .average()
                .orElse(5.0);

        // 1~10 스케일 → TOEIC 점수 (10점 단위 반올림)
        int rawScore = (int) Math.round(avgOverall * 20);
        short predictedScore = (short) (Math.round(rawScore / 10.0) * 10);   // 10점 단위
        short predictedLevel = calculateLevel(predictedScore);

        examSession.complete(predictedScore, predictedLevel);
        log.info("시험 완료: examSessionId={}, score={}, level={}",
                examSession.getId(), predictedScore, predictedLevel);
    }

    /**
     * TOEIC Speaking 점수(0~200)를 레벨(1~8)로 변환한다.
     */
    private short calculateLevel(short score) {
        if (score <= 60)  return 1;
        if (score <= 80)  return 2;
        if (score <= 100) return 3;
        if (score <= 120) return 4;
        if (score <= 140) return 5;
        if (score <= 160) return 6;
        if (score <= 180) return 7;
        return 8;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 조회
    // ══════════════════════════════════════════════════════════════════════════

    /** 피드백 ID로 단건 조회 */
    @Transactional(readOnly = true)
    public FeedbackResponse getById(Long feedbackId, Long userId) {
        Feedback feedback = feedbackRepository.findById(feedbackId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FEEDBACK_NOT_FOUND));

        // 본인 세션의 피드백인지 확인 (보안: 타인 피드백 조회 방어)
        if (!feedback.getPracticeSession().getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.FEEDBACK_NOT_FOUND);
        }

        return FeedbackResponse.from(feedback);
    }

    /** 세션 ID로 피드백 조회 */
    @Transactional(readOnly = true)
    public FeedbackResponse getBySessionId(Long sessionId, Long userId) {
        // 세션이 본인 것인지 먼저 확인
        practiceSessionRepository.findByIdAndUserIdWithDetails(sessionId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRACTICE_SESSION_NOT_FOUND));

        return feedbackRepository.findByPracticeSessionId(sessionId)
                .map(FeedbackResponse::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.FEEDBACK_NOT_FOUND));
    }
}
