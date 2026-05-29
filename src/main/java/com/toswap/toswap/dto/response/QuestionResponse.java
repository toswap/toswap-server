package com.toswap.toswap.dto.response;

import com.toswap.toswap.entity.Question;

import java.time.LocalDateTime;

/**
 * 개별 질문 응답 DTO.
 *
 * 단독으로 쓰이기도 하고 (GET /api/questions/{id}),
 * QuestionSetResponse 내부의 questions 리스트 원소로도 쓰인다.
 *
 * sequenceNo: 그룹 내 순서. Part 1/2는 null, Part 3/4/5는 1~3.
 *   프론트엔드가 이 값으로 "지금 몇 번째 질문인지"를 알 수 있다.
 */
public record QuestionResponse(
        Long id,
        Short partId,
        Short sequenceNo,   // 그룹 내 순서 (Part 1/2: null, Part 3/4/5: 1~3)
        String content,
        String imageUrl,    // Part 2 전용
        String imageKeyword, // Part 2 전용
        Short prepSeconds,
        Short responseSeconds,
        LocalDateTime createdAt
) {
    public static QuestionResponse from(Question question) {
        return new QuestionResponse(
                question.getId(),
                question.getPartId(),
                question.getSequenceNo(),
                question.getContent(),
                question.getImageUrl(),
                question.getImageKeyword(),
                question.getPrepSeconds(),
                question.getResponseSeconds(),
                question.getCreatedAt()
        );
    }
}
