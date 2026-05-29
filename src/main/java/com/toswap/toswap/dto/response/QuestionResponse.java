package com.toswap.toswap.dto.response;

import com.toswap.toswap.entity.Question;

import java.time.LocalDateTime;

/**
 * 문제 응답 DTO.
 * imageUrl / imageKeyword 는 Part 2에만 존재하고 나머지 파트는 null.
 */
public record QuestionResponse(
        Long id,
        Short partId,
        String content,
        String imageUrl,
        String imageKeyword,
        Short prepSeconds,
        Short responseSeconds,
        LocalDateTime createdAt
) {
    public static QuestionResponse from(Question question) {
        return new QuestionResponse(
                question.getId(),
                question.getPartId(),
                question.getContent(),
                question.getImageUrl(),
                question.getImageKeyword(),
                question.getPrepSeconds(),
                question.getResponseSeconds(),
                question.getCreatedAt()
        );
    }
}
