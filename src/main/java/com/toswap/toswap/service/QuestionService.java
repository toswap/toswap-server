package com.toswap.toswap.service;

import com.toswap.toswap.dto.request.QuestionGenerateRequest;
import com.toswap.toswap.dto.response.QuestionResponse;
import com.toswap.toswap.entity.Question;
import com.toswap.toswap.exception.BusinessException;
import com.toswap.toswap.exception.ErrorCode;
import com.toswap.toswap.repository.QuestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 문제 생성 및 조회 서비스.
 *
 * 문제 생성 흐름:
 * 1. GeminiService로 파트별 문제 텍스트 + imageKeyword 생성
 * 2. Part 2인 경우 UnsplashService로 imageKeyword 검색 → imageUrl 획득
 * 3. Question 엔티티 저장 후 응답 반환
 */
@Service
@RequiredArgsConstructor
public class QuestionService {

    private final QuestionRepository questionRepository;
    private final GeminiService geminiService;
    private final UnsplashService unsplashService;

    /**
     * TOEIC Speaking 파트에 맞는 문제를 AI로 생성하고 DB에 저장한다.
     * Part 2에서만 Unsplash 이미지 검색이 추가로 실행된다.
     */
    @Transactional
    public QuestionResponse generate(QuestionGenerateRequest request) {
        short partId = request.partId().shortValue();

        // Gemini로 문제 내용 생성
        GeminiService.QuestionData questionData = geminiService.generateQuestion(partId);

        // Part 2: Gemini가 제공한 imageKeyword로 Unsplash 이미지 검색
        // 다른 파트: 이미지 불필요 → null
        String imageUrl = null;
        String imageKeyword = questionData.imageKeyword();
        if (partId == 2 && imageKeyword != null && !imageKeyword.isBlank()) {
            imageUrl = unsplashService.searchImage(imageKeyword);
        }

        short[] timing = getPartTiming(partId);

        Question question = Question.builder()
                .partId(partId)
                .content(questionData.content())
                .imageUrl(imageUrl)
                .imageKeyword(imageKeyword)
                .prepSeconds(timing[0])
                .responseSeconds(timing[1])
                .build();

        return QuestionResponse.from(questionRepository.save(question));
    }

    /**
     * 문제 단건 조회.
     */
    @Transactional(readOnly = true)
    public QuestionResponse getById(Long questionId) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.QUESTION_NOT_FOUND));
        return QuestionResponse.from(question);
    }

    /**
     * TOEIC Speaking 파트별 준비 시간 / 답변 시간 (초 단위).
     *
     * Part 1 - Read Aloud:               prep=45s, response=45s
     * Part 2 - Describe a Picture:       prep=45s, response=30s
     * Part 3 - Respond to Questions:     prep= 3s, response=30s
     * Part 4 - Respond Using Info:       prep=45s, response=30s
     * Part 5 - Express an Opinion:       prep=45s, response=60s
     */
    private short[] getPartTiming(short partId) {
        return switch (partId) {
            case 1 -> new short[]{45, 45};
            case 2 -> new short[]{45, 30};
            case 3 -> new short[]{3, 30};
            case 4 -> new short[]{45, 30};
            case 5 -> new short[]{45, 60};
            default -> throw new BusinessException(ErrorCode.INVALID_PART_ID);
        };
    }
}
