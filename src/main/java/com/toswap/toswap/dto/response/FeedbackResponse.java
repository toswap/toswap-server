package com.toswap.toswap.dto.response;

import com.toswap.toswap.entity.Feedback;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 피드백 응답 DTO.
 *
 * ── 점수 척도 ─────────────────────────────────────────────────────────────────
 *   score* 필드: 1~10 척도 (TOEIC Speaking 공식 채점 기준 기반)
 *   toeicLevel: scoreOverall을 기반으로 계산된 TOEIC Speaking 수준 참고값.
 *               시험 전체 점수(0~200)가 아닌 이 문항의 수준 지표.
 *
 * ── improvements 구조 ─────────────────────────────────────────────────────────
 *   단순 짧은 구문이 아닌, 영역(area) + 문제점(issue) + 개선법(suggestion)으로 구성.
 *   예:
 *     area: "문법"
 *     issue: "'I go store'처럼 전치사가 생략되었습니다."
 *     suggestion: "전치사 'to'를 넣어 'I go to the store.'로 연습해보세요."
 *
 * ── 시험 모드 최종 점수 ──────────────────────────────────────────────────────
 *   개별 문항의 이 피드백과 별개로, 시험 11문제가 모두 완료되면
 *   GET /api/exam-sessions/{id}/result 에서 예상 TOEIC 점수(0~200)를 확인할 수 있다.
 */
public record FeedbackResponse(
        Long feedbackId,
        Long sessionId,
        String transcript,              // Gemini가 전사한 발화 텍스트
        Short scorePronunciation,       // 발음 (1~10)
        Short scoreIntonation,          // 억양/리듬 (1~10)
        Short scoreGrammar,             // 문법 (1~10)
        Short scoreVocabulary,          // 어휘 (1~10)
        Short scoreFluency,             // 유창성 (1~10)
        Short scoreContent,             // 내용/답변 적합성 (1~10)
        Short scoreOverall,             // 종합 (1~10)
        String toeicLevel,              // 이 문항 기준 수준 (예: "중급 (Intermediate)")
        List<String> strengths,         // 잘한 점 2~3개 (구체적인 완성 문장)
        List<ImprovementItem> improvements, // 개선 항목 2~3개 (area + issue + suggestion)
        String detailedComment,         // 전체 코멘트 (2~4문장)
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
                calculateToeicLevel(f.getScoreOverall()),
                f.getStrengths(),
                f.getImprovements(),
                f.getDetailedComment(),
                f.getCreatedAt()
        );
    }

    /**
     * scoreOverall (1~10)을 TOEIC Speaking 수준 설명으로 변환한다.
     * 시험 전체 점수(0~200)가 아닌 이 문항의 상대적 수준을 나타내는 참고값이다.
     */
    private static String calculateToeicLevel(short scoreOverall) {
        return switch (scoreOverall) {
            case 1, 2 -> "입문 (Novice)";
            case 3, 4 -> "초급 (Elementary)";
            case 5, 6 -> "중급 (Intermediate)";
            case 7, 8 -> "중고급 (Advanced)";
            default   -> "고급 (Expert)";  // 9, 10
        };
    }
}
