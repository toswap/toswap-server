package com.toswap.toswap.dto.response;

/**
 * 피드백 개선 항목. Feedback.improvements 필드에 JSONB로 저장된다.
 *
 * area:       어떤 평가 영역인지 (발음, 억양, 문법, 어휘, 유창성, 내용)
 * issue:      이 응답에서 발견된 구체적인 문제점 (가능하면 발화 예시 인용)
 * suggestion: 문제를 개선하기 위한 구체적인 조언
 */
public record ImprovementItem(
        String area,
        String issue,
        String suggestion
) {}
