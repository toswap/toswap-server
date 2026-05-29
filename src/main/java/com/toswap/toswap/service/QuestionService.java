package com.toswap.toswap.service;

import com.toswap.toswap.dto.request.QuestionGenerateRequest;
import com.toswap.toswap.dto.response.QuestionResponse;
import com.toswap.toswap.dto.response.QuestionSetResponse;
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
 * ── 공개 API (Controller에서 호출) ──────────────────────────────────────────
 *   generate()         → DTO(QuestionSetResponse) 반환. Question API용.
 *   getById()          → 개별 질문 단건 조회.
 *   getGroupById()     → 그룹 전체 조회.
 *
 * ── 내부 공용 메서드 (PracticeSessionService 등 서비스 간 호출) ─────────────
 *   generateAndSaveSingleQuestion()  → Question 엔티티 반환. Part 1/2 세션 생성 시 사용.
 *   generateAndSaveGroupQuestions()  → QuestionGroup 엔티티 반환. Part 3/4/5 세션 생성 시 사용.
 *
 * ── generate()가 내부 메서드를 래핑하는 구조인 이유 ─────────────────────────
 *   PracticeSessionService는 생성된 Question/QuestionGroup 엔티티를 직접 FK로
 *   참조해야 한다 (PracticeSession.question = question). DTO에서는 ID만 있어서
 *   재조회가 필요해지는 비효율이 생기므로, 엔티티 자체를 반환하는 내부 메서드를 공유한다.
 */
@Service
@RequiredArgsConstructor
public class QuestionService {

    private final QuestionRepository questionRepository;
    private final QuestionGroupRepository questionGroupRepository;
    private final GeminiService geminiService;
    private final UnsplashService unsplashService;

    // ══════════════════════════════════════════════════════════════════════════
    // 공개 API (Controller용 DTO 반환)
    // ══════════════════════════════════════════════════════════════════════════

    @Transactional
    public QuestionSetResponse generate(QuestionGenerateRequest request) {
        short partId = request.partId().shortValue();
        return switch (partId) {
            case 1, 2 -> QuestionSetResponse.fromSingle(generateAndSaveSingleQuestion(partId));
            case 3, 4, 5 -> QuestionSetResponse.fromGroup(generateAndSaveGroupQuestions(partId));
            default -> throw new BusinessException(ErrorCode.INVALID_PART_ID);
        };
    }

    @Transactional(readOnly = true)
    public QuestionResponse getById(Long questionId) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.QUESTION_NOT_FOUND));
        return QuestionResponse.from(question);
    }

    @Transactional(readOnly = true)
    public QuestionSetResponse getGroupById(Long groupId) {
        QuestionGroup group = questionGroupRepository.findByIdWithQuestions(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.QUESTION_NOT_FOUND));
        return QuestionSetResponse.fromGroup(group);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 내부 공용 메서드 (PracticeSessionService 등에서 호출)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Part 1/2: Gemini로 문제를 생성하고 Question 엔티티를 저장 후 반환.
     * 호출 시점의 트랜잭션에 참여한다(PROPAGATION_REQUIRED).
     */
    @Transactional
    public Question generateAndSaveSingleQuestion(short partId) {
        GeminiService.QuestionData data = geminiService.generateQuestion(partId);

        String imageUrl = null;
        if (partId == 2 && data.imageKeyword() != null && !data.imageKeyword().isBlank()) {
            imageUrl = unsplashService.searchImage(data.imageKeyword());
        }

        short[] timing = getSingleTiming(partId);
        return questionRepository.save(Question.builder()
                .partId(partId)
                .content(data.content())
                .imageUrl(imageUrl)
                .imageKeyword(data.imageKeyword())
                .prepSeconds(timing[0])
                .responseSeconds(timing[1])
                .build());
    }

    /**
     * Part 3/4/5: Gemini로 그룹 문제를 생성하고 QuestionGroup + Question들을 저장 후 반환.
     * 반환된 QuestionGroup에는 questions 컬렉션이 JOIN FETCH로 로딩된 상태다.
     */
    @Transactional
    public QuestionGroup generateAndSaveGroupQuestions(short partId) {
        GeminiService.QuestionGroupData groupData = geminiService.generateQuestionGroup(partId);

        // 공통 배경 저장
        QuestionGroup group = questionGroupRepository.save(QuestionGroup.builder()
                .partId(partId)
                .contextContent(groupData.contextContent())
                .build());

        // 각 질문 저장 (sequenceNo 1-based)
        List<GeminiService.QuestionItemData> items = groupData.questions();
        List<Question> questions = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            short seqNo = (short) (i + 1);
            short[] timing = getGroupTiming(partId, seqNo);
            questions.add(Question.builder()
                    .partId(partId)
                    .questionGroup(group)
                    .sequenceNo(seqNo)
                    .content(items.get(i).content())
                    .prepSeconds(timing[0])
                    .responseSeconds(timing[1])
                    .build());
        }
        List<Question> savedQuestions = questionRepository.saveAll(questions);

        // ── Hibernate 1차 캐시 문제 우회 ───────────────────────────────────────
        // QuestionGroup은 @Builder.Default로 questions=new ArrayList<>()가 초기화된
        // 채 1차 캐시에 올라가 있다. 이 상태에서 findByIdWithQuestions의 JOIN FETCH를
        // 실행해도 Hibernate는 이미 '로딩 완료'된 컬렉션을 덮어쓰지 않아 빈 리스트가 반환된다.
        //
        // 해결책: 새로 저장된 Question들을 in-memory 리스트에 직접 추가한다.
        // DB 관계(question.group_id FK)는 이미 올바르게 저장되었으므로
        // 이후 쿼리로 다시 조회하면 정상 반환된다. 순서는 sequenceNo 오름차순을 유지한다.
        group.getQuestions().addAll(savedQuestions);

        return group;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 타이밍 헬퍼
    // ══════════════════════════════════════════════════════════════════════════

    private short[] getSingleTiming(short partId) {
        return switch (partId) {
            case 1 -> new short[]{45, 45};
            case 2 -> new short[]{45, 30};
            default -> throw new BusinessException(ErrorCode.INVALID_PART_ID);
        };
    }

    /**
     * Part 3/4: Q1, Q2는 응답 15초 / Q3는 30초 (더 상세한 설명 필요)
     * Part 5: 모든 질문 응답 60초 (의견 + 근거 표현)
     */
    private short[] getGroupTiming(short partId, short sequenceNo) {
        if (partId == 5) {
            return new short[]{45, 60};
        }
        return (sequenceNo == 3) ? new short[]{3, 30} : new short[]{3, 15};
    }
}
