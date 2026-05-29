package com.toswap.toswap.service;

import com.toswap.toswap.dto.request.QuestionGenerateRequest;
import com.toswap.toswap.dto.response.QuestionSetResponse;
import com.toswap.toswap.dto.response.QuestionResponse;
import com.toswap.toswap.entity.Question;
import com.toswap.toswap.entity.QuestionGroup;
import com.toswap.toswap.exception.BusinessException;
import com.toswap.toswap.exception.ErrorCode;
import com.toswap.toswap.repository.QuestionGroupRepository;
import com.toswap.toswap.repository.QuestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 문제 생성 및 조회 서비스.
 *
 * ── 파트별 생성 흐름 ────────────────────────────────────────────────────────
 *
 * [Part 1 / Part 2] → generateSingleQuestion()
 *   1. GeminiService.generateQuestion(partId) 호출 → 문제 텍스트 생성
 *   2. Part 2라면 UnsplashService.searchImage() → 이미지 URL 추가
 *   3. Question 저장 → QuestionSetResponse.fromSingle() 반환
 *
 * [Part 3 / Part 4 / Part 5] → generateGroupQuestions()
 *   1. GeminiService.generateQuestionGroup(partId) 호출
 *      → 공통 배경(contextContent) + 질문 2~3개를 한 번에 생성
 *   2. QuestionGroup 먼저 저장
 *   3. 각 질문(Question)을 sequenceNo(1,2,3)와 각기 다른 타이밍으로 저장
 *   4. QuestionSetResponse.fromGroup() 반환
 */
@Service
@RequiredArgsConstructor
public class QuestionService {

    private final QuestionRepository questionRepository;
    private final QuestionGroupRepository questionGroupRepository;
    private final GeminiService geminiService;
    private final UnsplashService unsplashService;

    // ══════════════════════════════════════════════════════════════════════════
    // 문제 생성
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 파트별 문제 세트를 생성하고 DB에 저장한다.
     * Part 1/2는 독립 문제 1개, Part 3/4/5는 공통 배경 + 질문 그룹을 반환한다.
     */
    @Transactional
    public QuestionSetResponse generate(QuestionGenerateRequest request) {
        short partId = request.partId().shortValue();

        return switch (partId) {
            case 1, 2 -> generateSingleQuestion(partId);
            case 3, 4, 5 -> generateGroupQuestions(partId);
            default -> throw new BusinessException(ErrorCode.INVALID_PART_ID);
        };
    }

    // ── Part 1/2: 독립 문제 1개 생성 ──────────────────────────────────────────

    private QuestionSetResponse generateSingleQuestion(short partId) {
        GeminiService.QuestionData data = geminiService.generateQuestion(partId);

        // Part 2: Gemini가 제안한 imageKeyword로 Unsplash에서 이미지 검색
        String imageUrl = null;
        if (partId == 2 && data.imageKeyword() != null && !data.imageKeyword().isBlank()) {
            imageUrl = unsplashService.searchImage(data.imageKeyword());
        }

        short[] timing = getSingleTiming(partId);
        Question question = Question.builder()
                .partId(partId)
                .content(data.content())
                .imageUrl(imageUrl)
                .imageKeyword(data.imageKeyword())
                .prepSeconds(timing[0])
                .responseSeconds(timing[1])
                .build();

        return QuestionSetResponse.fromSingle(questionRepository.save(question));
    }

    // ── Part 3/4/5: 그룹 문제 생성 ────────────────────────────────────────────

    private QuestionSetResponse generateGroupQuestions(short partId) {
        GeminiService.QuestionGroupData groupData = geminiService.generateQuestionGroup(partId);

        // QuestionGroup(공통 배경)을 먼저 저장
        QuestionGroup group = QuestionGroup.builder()
                .partId(partId)
                .contextContent(groupData.contextContent())
                .build();
        QuestionGroup savedGroup = questionGroupRepository.save(group);

        // 각 질문을 sequenceNo 1,2,3으로 저장
        // 타이밍은 파트와 순서(sequenceNo)에 따라 다르다
        List<Question> questions = new ArrayList<>();
        List<GeminiService.QuestionItemData> items = groupData.questions();

        for (int i = 0; i < items.size(); i++) {
            short sequenceNo = (short) (i + 1);  // 1-based
            short[] timing = getGroupTiming(partId, sequenceNo);

            questions.add(Question.builder()
                    .partId(partId)
                    .questionGroup(savedGroup)
                    .sequenceNo(sequenceNo)
                    .content(items.get(i).content())
                    .prepSeconds(timing[0])
                    .responseSeconds(timing[1])
                    .build());
        }

        questionRepository.saveAll(questions);

        // 저장 후 JOIN FETCH로 다시 조회 (questions 컬렉션이 로딩된 상태로 반환)
        QuestionGroup groupWithQuestions = questionGroupRepository
                .findByIdWithQuestions(savedGroup.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.QUESTION_NOT_FOUND));

        return QuestionSetResponse.fromGroup(groupWithQuestions);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 문제 조회
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 개별 질문 단건 조회.
     * Part 3/4/5 질문이라면 응답에 sequenceNo가 포함된다.
     */
    @Transactional(readOnly = true)
    public QuestionResponse getById(Long questionId) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.QUESTION_NOT_FOUND));
        return QuestionResponse.from(question);
    }

    /**
     * 그룹(공통 배경 + 하위 질문 전체) 조회.
     * Part 3/4/5 문제를 다시 풀 때 또는 연습 세션에서 그룹 전체를 불러올 때 사용.
     */
    @Transactional(readOnly = true)
    public QuestionSetResponse getGroupById(Long groupId) {
        QuestionGroup group = questionGroupRepository.findByIdWithQuestions(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.QUESTION_NOT_FOUND));
        return QuestionSetResponse.fromGroup(group);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 타이밍 헬퍼
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Part 1/2 독립 문제 타이밍.
     * returns short[]{prepSeconds, responseSeconds}
     */
    private short[] getSingleTiming(short partId) {
        return switch (partId) {
            case 1 -> new short[]{45, 45};  // Read a Text Aloud
            case 2 -> new short[]{45, 30};  // Describe a Picture
            default -> throw new BusinessException(ErrorCode.INVALID_PART_ID);
        };
    }

    /**
     * Part 3/4/5 그룹 내 개별 질문 타이밍.
     *
     * Part 3 / Part 4:
     *   Q1, Q2 → 준비 3s / 답변 15s  (비교적 단순한 질문)
     *   Q3     → 준비 3s / 답변 30s  (상세 설명 필요)
     *
     * Part 5:
     *   Q1, Q2 → 준비 45s / 답변 60s (의견 + 근거 표현)
     *
     * returns short[]{prepSeconds, responseSeconds}
     */
    private short[] getGroupTiming(short partId, short sequenceNo) {
        if (partId == 5) {
            return new short[]{45, 60};
        }
        // Part 3, 4: 마지막(3번) 질문만 답변 시간이 30초
        return (sequenceNo == 3)
                ? new short[]{3, 30}
                : new short[]{3, 15};
    }
}
