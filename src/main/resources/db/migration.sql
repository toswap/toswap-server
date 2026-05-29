-- ============================================================
-- TOEIC Speaking 문제 구조 개선 마이그레이션
--
-- 변경 내용:
--   1. question_groups 테이블 추가
--      - Part 3/4/5의 "공통 배경" (서베이 상황, 표/일정표, 의견 주제)을 저장
--   2. questions 테이블에 group_id, sequence_no 컬럼 추가
--      - Part 1/2: group_id = NULL (독립 문제)
--      - Part 3/4/5: group_id = 그룹 ID, sequence_no = 그룹 내 순서(1,2,3)
-- ============================================================

-- 1. 공통 배경을 담는 그룹 테이블
CREATE TABLE question_groups (
    id           BIGSERIAL    PRIMARY KEY,
    part_id      SMALLINT     NOT NULL,       -- 파트 번호 (3, 4, 5)
    context_content      TEXT NOT NULL,       -- 공통 배경 텍스트
    context_image_url    VARCHAR(500),        -- Part 4 이미지 문서용 (현재는 null, 추후 확장)
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- 2. questions 테이블에 그룹 연결 컬럼 추가
ALTER TABLE questions
    ADD COLUMN group_id     BIGINT REFERENCES question_groups(id) ON DELETE CASCADE,
    ADD COLUMN sequence_no  SMALLINT;   -- 그룹 내 순서: 1, 2, 3

-- 인덱스: 그룹 ID로 질문 목록 조회 시 성능
CREATE INDEX idx_questions_group_id ON questions (group_id);

-- ============================================================
-- practice_sessions 테이블 변경
--   question_group_id 추가: Part 3/4/5 세션들을 같은 그룹으로 묶기 위함
--   Part 1/2는 NULL, Part 3/4/5는 question_groups.id를 참조
-- ============================================================
ALTER TABLE practice_sessions
    ADD COLUMN question_group_id BIGINT REFERENCES question_groups(id) ON DELETE SET NULL;

CREATE INDEX idx_practice_sessions_group_id ON practice_sessions (question_group_id);
