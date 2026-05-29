package com.toswap.toswap.dto.response;

import com.toswap.toswap.entity.Feedback;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 피드백 응답 DTO.
 *
 * ── 점수 척도 ─────────────────────────────────────────────────────────────────
 *   모든 개별 점수(score*)는 1~10 척도.
 *   scoreOverall: content 가중치가 가장 높은 종합 점수 (1~10).
 *
 * ── 시험 모드 vs 연습 모드 ─────────────────────────────────────────────────────
 *   두 모드 모두 동일한 FeedbackResponse를 반환한다.
 *   시험 모드일 경우 백엔드에서 자동으로 ExamSession 완료 처리가 이루어진다
 *   (모든 11개 세션이 DONE 상태가 되면 예상 점수가 계산된다).
 */
public record FeedbackResponse(
        Long feedbackId,
        Long sessionId,
        String transcript,                  // Gemini가 전사한 발화 텍스트
        Short scorePronunciation,           // 발음 (1~10)
        Short scoreIntonation,              // 억양/리듬 (1~10)
        Short scoreGrammar,                 // 문법 (1~10)
        Short scoreVocabulary,              // 어휘 (1~10)
        Short scoreFluency,                 // 유창성 (1~10)
        Short scoreContent,                 // 내용/답변 적합성 (1~10)
        Short scoreOverall,                 // 종합 (1~10)
        List<String> strengths,             // 잘한 점 2~3개
        List<String> improvements,          // 개선할 점 2~3개
        String detailedComment,             // 상세 코멘트 (1단락)
        LocalDateTime evaluatedAt
) {

    public static FeedbackResponse from(Feedback f) {
        return new FeedbackResponse(
                f.getId(),
                f.getPracticeSession().getId(),
                f.getTranscript(),
                f.getScorePronunciation(),
                f.getScoreIntonation(),
                f.getScoreGrammar(),
                f.getScoreVocabulary(),
                f.getScoreFluency(),
                f.getScoreContent(),
                f.getScoreOverall(),
                f.getStrengths(),
                f.getImprovements(),
                f.getDetailedComment(),
                f.getCreatedAt()
        );
    }
}
