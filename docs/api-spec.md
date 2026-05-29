# ToSwap API 명세서

> **대상**: 프론트엔드 개발자 / FE AI Agent  
> **백엔드**: Spring Boot 4.x · PostgreSQL · Redis 세션 · Gemini AI  
> **기준일**: 2026-05-29

---

## 목차

1. [서버 기본 정보](#1-서버-기본-정보)
2. [인증 방식](#2-인증-방식)
3. [에러 응답 형식](#3-에러-응답-형식)
4. [TOEIC Speaking 구조 이해](#4-toeic-speaking-구조-이해)
5. [Auth API](#5-auth-api)
6. [연습 세션 API](#6-연습-세션-api)
7. [피드백 API](#7-피드백-api)
8. [시험 세션 API](#8-시험-세션-api)
9. [문제 API (보조)](#9-문제-api-보조)
10. [전체 서비스 플로우](#10-전체-서비스-플로우)

---

## 1. 서버 기본 정보

| 항목 | 값 |
|------|-----|
| 로컬 Base URL | `http://localhost:8080` |
| Swagger UI | `http://localhost:8080/swagger-ui.html` |
| Content-Type | `application/json` (파일 업로드 시 `multipart/form-data`) |
| 세션 방식 | 서버 세션 + 쿠키 (JWT 아님) |

---

## 2. 인증 방식

### 세션 쿠키 기반 인증

JWT를 사용하지 않습니다. 카카오 OAuth2 로그인 후 서버가 **세션 쿠키**를 발급하며, 이후 모든 API 요청에 이 쿠키가 자동으로 포함되어야 합니다.

### FE 필수 설정

```javascript
// axios 사용 시
axios.defaults.withCredentials = true;

// fetch 사용 시
fetch(url, { credentials: 'include' });
```

`withCredentials: true` 없이는 쿠키가 전송되지 않아 모든 `/api/**` 요청이 **401**을 반환합니다.

### 로그인 흐름

```
1. FE → 브라우저를 아래 URL로 이동시킴 (링크 또는 window.location)
   GET http://localhost:8080/oauth2/authorization/kakao

2. 카카오 로그인 완료 후 백엔드가 FE로 리다이렉트
   - 이메일 있는 유저 → http://localhost:3000 (메인 페이지)
   - 이메일 없는 유저 → http://localhost:3000/additional-info (이메일 입력 페이지)

3. 세션 쿠키가 브라우저에 자동 저장됨
4. 이후 모든 API 요청에 쿠키 자동 포함
```

> **주의**: 로그인은 `/api/`가 아닌 `/oauth2/authorization/kakao`로 시작합니다.  
> 이 URL은 백엔드가 직접 카카오로 리다이렉트 처리하므로 FE에서 별도 로직 불필요합니다.

### 인증 필요 여부

| 경로 | 인증 필요 |
|------|----------|
| `/oauth2/**` | ❌ |
| `/login/oauth2/**` | ❌ |
| `/swagger-ui/**`, `/v3/api-docs/**` | ❌ |
| `/api/**` | ✅ 필수 |

---

## 3. 에러 응답 형식

모든 에러는 동일한 JSON 구조로 반환됩니다.

```json
{
  "status": 400,
  "code": "PRACTICE_SESSION_ALREADY_DONE",
  "message": "이미 완료된 연습 세션입니다."
}
```

### 주요 에러 코드

| code | status | 설명 |
|------|--------|------|
| `UNAUTHORIZED` | 401 | 로그인 필요 (쿠키 없음 또는 세션 만료) |
| `FORBIDDEN` | 403 | 권한 없음 |
| `QUESTION_NOT_FOUND` | 404 | 문제를 찾을 수 없음 |
| `PRACTICE_SESSION_NOT_FOUND` | 404 | 연습 세션 없음 (또는 타인 세션 접근) |
| `PRACTICE_SESSION_ALREADY_DONE` | 400 | 이미 완료된 세션 |
| `FEEDBACK_ALREADY_EXISTS` | 400 | 이미 평가된 세션에 중복 제출 |
| `FEEDBACK_EVALUATION_FAILED` | 500 | Gemini AI 음성 평가 실패 |
| `EXAM_SESSION_NOT_FOUND` | 404 | 시험 세션 없음 |
| `EXAM_ALREADY_IN_PROGRESS` | 400 | 이미 진행 중인 시험 있음 |
| `EXAM_SESSION_ALREADY_FINISHED` | 400 | 이미 종료된 시험 포기 시도 |
| `QUESTION_GENERATION_FAILED` | 500 | Gemini AI 문제 생성 실패 |
| `INVALID_PART_ID` | 400 | 파트 ID가 1~5 범위 벗어남 |

---

## 4. TOEIC Speaking 구조 이해

FE 화면 설계를 위해 반드시 숙지해야 합니다.

### 전체 구성 (11문제)

| 파트 | 이름 | 문제 수 | 준비(prep) | 답변(resp) | 특징 |
|------|------|---------|-----------|-----------|------|
| Part 1 | Read a Text Aloud | 2문제 | 45초 | 45초 | 독립 문제, 지문 읽기 |
| Part 2 | Describe a Picture | 1문제 | 45초 | 30초 | 독립 문제, 사진 묘사 (이미지 URL 포함) |
| Part 3 | Respond to Questions | 3문제 | 3초 | Q1·Q2: 15초 / Q3: 30초 | 공통 배경(서베이 상황) 공유 |
| Part 4 | Respond to Questions Using Information Provided | 3문제 | 3초 | Q1·Q2: 15초 / Q3: 30초 | 공통 배경(표/문서) 공유 |
| Part 5 | Express an Opinion | 2문제 | 45초 | 60초 | 공통 배경(의견 주제) 공유 |

### 핵심 개념: 공통 배경 (contextContent)

- **Part 1/2**: 독립 문제. `contextContent = null`
- **Part 3/4/5**: 2~3개 질문이 하나의 배경을 공유. 화면 상단에 `contextContent`를 고정 표시하고 질문들을 순서대로 진행.

### FE 화면 전환 패턴

```
[Part 1/2]
┌─────────────────────────────┐
│  질문 텍스트                  │  ← content
│  (Part 2: 이미지 표시)        │  ← imageUrl
│                              │
│  준비 타이머: 45초             │
│  → 답변 타이머: 30초          │
└─────────────────────────────┘

[Part 3/4/5]
┌─────────────────────────────┐
│  [공통 배경]                  │  ← contextContent (고정)
│  Imagine you are talking... │
│                              │
│  Q1. 첫 번째 질문             │  ← sessions[0].content
│  준비 3초 → 답변 15초         │
│                              │
│  Q2. 두 번째 질문             │  (전환)
│  준비 3초 → 답변 15초         │
│                              │
│  Q3. 세 번째 질문             │  (전환)
│  준비 3초 → 답변 30초         │
└─────────────────────────────┘
```

---

## 5. Auth API

### GET `/api/auth/me` — 내 정보 조회

로그인 상태 확인 및 유저 정보 조회. 앱 초기 진입 시 호출하여 로그인 여부를 판단합니다.

**응답 200**
```json
{
  "id": 1,
  "name": "홍길동",
  "email": "hong@example.com",
  "profileImageUrl": null,
  "provider": "kakao",
  "hasEmail": true
}
```

| 필드 | 타입 | 설명 |
|------|------|------|
| `id` | Long | 유저 ID |
| `name` | String | 카카오 닉네임 |
| `email` | String \| null | 이메일 (없으면 null) |
| `profileImageUrl` | String \| null | 프로필 이미지 URL |
| `provider` | String | `"kakao"` |
| `hasEmail` | boolean | **false이면 `/additional-info`로 이동 유도** |

**에러**
- `401`: 로그인 안 된 상태

---

### POST `/api/auth/logout` — 로그아웃

서버 세션 삭제 및 Redis 세션 데이터 제거.

**응답 200** (body 없음)

---

### PATCH `/api/auth/profile` — 이메일 등록

카카오에서 이메일을 제공하지 않은 유저가 직접 이메일을 입력할 때 사용.  
`hasEmail = false`인 경우에만 호출. 이미 이메일이 있으면 400.

**요청 Body**
```json
{
  "email": "user@example.com"
}
```

**응답 200** — `UserResponse` (위 `/me`와 동일 구조)

**에러**
- `400 EMAIL_ALREADY_EXISTS`: 이미 이메일 있음
- `400 INVALID_REQUEST`: 이메일 형식 오류

---

## 6. 연습 세션 API

파트별로 문제 1세트를 생성하고 연습하는 모드.

### POST `/api/practice-sessions` — 연습 시작

파트를 선택하면 Gemini AI가 문제를 생성하고 세션을 반환합니다.  
**문제 생성에 3~8초 소요될 수 있습니다.**

**요청 Body**
```json
{
  "partId": 3
}
```

| 필드 | 타입 | 유효값 | 설명 |
|------|------|--------|------|
| `partId` | Integer | 1~5 | 연습할 파트 번호 |

**응답 200 — Part 1/2 예시 (Part 1)**
```json
{
  "partId": 1,
  "questionGroupId": null,
  "contextContent": null,
  "sessions": [
    {
      "sessionId": 10,
      "questionId": 5,
      "sequenceNo": null,
      "content": "Attention all passengers. The train to Seoul will depart from Platform 3 at 9:15 AM. Please have your tickets ready for inspection.",
      "imageUrl": null,
      "prepSeconds": 45,
      "responseSeconds": 45
    }
  ]
}
```

**응답 200 — Part 2 예시**
```json
{
  "partId": 2,
  "questionGroupId": null,
  "contextContent": null,
  "sessions": [
    {
      "sessionId": 11,
      "questionId": 6,
      "sequenceNo": null,
      "content": "Please describe the photograph in as much detail as possible.",
      "imageUrl": "https://images.unsplash.com/photo-xxxxx",
      "prepSeconds": 45,
      "responseSeconds": 30
    }
  ]
}
```

**응답 200 — Part 3 예시 (공통 배경 + 3문제)**
```json
{
  "partId": 3,
  "questionGroupId": 7,
  "contextContent": "Imagine you are talking on the phone with someone who is conducting a survey about your online shopping habits.",
  "sessions": [
    {
      "sessionId": 12,
      "questionId": 7,
      "sequenceNo": 1,
      "content": "How often do you shop online?",
      "imageUrl": null,
      "prepSeconds": 3,
      "responseSeconds": 15
    },
    {
      "sessionId": 13,
      "questionId": 8,
      "sequenceNo": 2,
      "content": "What types of products do you usually buy online?",
      "imageUrl": null,
      "prepSeconds": 3,
      "responseSeconds": 15
    },
    {
      "sessionId": 14,
      "questionId": 9,
      "sequenceNo": 3,
      "content": "Why do you prefer online shopping over visiting physical stores? Please explain in detail.",
      "imageUrl": null,
      "prepSeconds": 3,
      "responseSeconds": 30
    }
  ]
}
```

**응답 필드 설명**

| 필드 | 설명 |
|------|------|
| `questionGroupId` | Part 3/4/5: 그룹 ID. Part 1/2: `null` |
| `contextContent` | Part 3/4/5: 상단 고정 배경 텍스트. Part 1/2: `null` |
| `sessions[].sessionId` | **음성 제출(POST /api/feedbacks) 시 필수**. 보관 필요 |
| `sessions[].sequenceNo` | Part 3/4/5: 1~3. Part 1/2: `null` |
| `sessions[].imageUrl` | Part 2: Unsplash 사진 URL. 나머지: `null` |
| `sessions[].prepSeconds` | 준비 시간(초) |
| `sessions[].responseSeconds` | 답변 시간(초) |

**에러**
- `400`: partId 유효성 오류
- `500 QUESTION_GENERATION_FAILED`: Gemini 문제 생성 실패

---

### GET `/api/practice-sessions/{sessionId}` — 세션 상세 조회

연습 화면 복원 또는 피드백 확인 화면 진입 시 사용.

**응답 200**
```json
{
  "sessionId": 12,
  "status": "DONE",
  "partId": 3,
  "sequenceNo": 1,
  "questionGroupId": 7,
  "contextContent": "Imagine you are talking on the phone...",
  "questionId": 7,
  "content": "How often do you shop online?",
  "imageUrl": null,
  "prepSeconds": 3,
  "responseSeconds": 15,
  "createdAt": "2026-05-29T10:30:00"
}
```

| `status` 값 | 의미 |
|-------------|------|
| `PENDING` | 음성 미제출 |
| `PROCESSING` | AI 평가 중 |
| `DONE` | 평가 완료 → 피드백 조회 가능 |
| `ERROR` | AI 평가 실패 → 재시도 가능 |

**에러**
- `404`: 세션 없음 또는 타인 소유

---

### GET `/api/practice-sessions` — 내 연습 기록

최신순으로 반환. Part 3/4/5의 같은 그룹 세션은 **1개 항목으로 묶여서** 반환됩니다.

**응답 200**
```json
[
  {
    "partId": 3,
    "firstSessionId": 12,
    "questionGroupId": 7,
    "overallStatus": "DONE",
    "totalQuestions": 3,
    "completedQuestions": 3,
    "createdAt": "2026-05-29T10:30:00"
  },
  {
    "partId": 1,
    "firstSessionId": 10,
    "questionGroupId": null,
    "overallStatus": "PENDING",
    "totalQuestions": 1,
    "completedQuestions": 0,
    "createdAt": "2026-05-29T09:00:00"
  }
]
```

| 필드 | 설명 |
|------|------|
| `firstSessionId` | 그룹의 첫 세션 ID (피드백 조회 진입점) |
| `overallStatus` | 그룹 전체 상태: 모두 DONE → `DONE` / 하나라도 ERROR → `ERROR` / 나머지 → `PENDING` |
| `totalQuestions` | 전체 질문 수 (Part1: 1, Part3/4: 3, Part5: 2) |
| `completedQuestions` | DONE 상태인 세션 수 |

> **주의**: 이 목록은 시험 모드 세션을 포함하지 않습니다 (연습 세션만 반환).

---

## 7. 피드백 API

음성 파일을 제출하면 Gemini AI가 평가하고 상세 피드백을 반환합니다.  
연습 세션과 시험 세션 **모두** 동일한 엔드포인트를 사용합니다.

### POST `/api/feedbacks` — 음성 제출 및 AI 평가

**⚠️ Gemini 음성 처리로 5~15초 소요 가능**

**요청**: `multipart/form-data`

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `sessionId` | Long (form param) | ✅ | 평가할 세션 ID |
| `audio` | File (multipart) | ✅ | 녹음 파일. 권장: `audio/webm` |

```javascript
// 브라우저 MediaRecorder 사용 예시
const formData = new FormData();
formData.append('sessionId', sessionId);       // Long 값
formData.append('audio', audioBlob, 'recording.webm');

const response = await axios.post('/api/feedbacks', formData, {
  headers: { 'Content-Type': 'multipart/form-data' },
  withCredentials: true
});
```

**지원 오디오 포맷**: WAV, MP3, AIFF, AAC, OGG, FLAC, WebM/Opus  
브라우저 `MediaRecorder` 기본값인 `audio/webm`을 권장합니다.

**응답 200**
```json
{
  "feedbackId": 1,
  "sessionId": 12,
  "transcript": "I usually shop online about two or three times a week. I find it very convenient because...",
  "scorePronunciation": 7,
  "scoreIntonation": 6,
  "scoreGrammar": 8,
  "scoreVocabulary": 7,
  "scoreFluency": 6,
  "scoreContent": 8,
  "scoreOverall": 7,
  "toeicLevel": "중고급 (Advanced)",
  "strengths": [
    "답변 구조가 명확하고 요점이 잘 전달되었습니다.",
    "어휘 선택이 다양하고 적절하여 표현력이 돋보였습니다.",
    "질문의 핵심인 빈도에 대해 구체적인 수치로 답변한 점이 좋았습니다."
  ],
  "improvements": [
    {
      "area": "유창성",
      "issue": "문장과 문장 사이에 'um', 'uh' 같은 채움 표현이 자주 등장하여 답변의 흐름이 끊겼습니다.",
      "suggestion": "답변 전 3초 준비 시간을 활용해 핵심 키워드를 미리 떠올리세요. 채움 표현 대신 짧은 침묵을 활용하는 연습이 도움이 됩니다."
    },
    {
      "area": "억양",
      "issue": "문장 끝의 억양이 단조롭게 내려가는 패턴이 반복되어 자연스러운 영어 리듬이 부족했습니다.",
      "suggestion": "정보를 추가할 때(because, so, also)는 억양을 유지하고, 문장이 완전히 끝날 때만 내리는 연습을 해보세요."
    }
  ],
  "detailedComment": "전반적으로 질문의 의도를 정확히 파악하고 관련성 높은 답변을 제시하였습니다. 어휘와 문법 측면에서 안정적인 수준을 보여주었으며, 특히 구체적인 예시를 활용한 점이 돋보였습니다. 유창성을 높이기 위해 채움 표현을 줄이고, 자연스러운 억양 패턴을 연습하면 더욱 향상된 결과를 기대할 수 있습니다.",
  "evaluatedAt": "2026-05-29T10:35:00"
}
```

**응답 필드 설명**

| 필드 | 타입 | 설명 |
|------|------|------|
| `transcript` | String | Gemini가 전사한 발화 텍스트 |
| `score*` | Short (1~10) | 각 평가 기준 점수 |
| `scoreOverall` | Short (1~10) | 종합 점수 (content 가중치 최고) |
| `toeicLevel` | String | 이 문항 수준 참고값 (아래 표 참고) |
| `strengths` | String[] | 잘한 점 2~3개 (한국어 완성 문장) |
| `improvements` | Object[] | 개선 항목 2~3개 (구조화) |
| `improvements[].area` | String | 영역: `발음`/`억양`/`문법`/`어휘`/`유창성`/`내용` |
| `improvements[].issue` | String | 구체적인 문제점 (발화 내용 인용 가능) |
| `improvements[].suggestion` | String | 개선 방법 및 연습 팁 |
| `detailedComment` | String | 전체 코멘트 (한국어 2~4문장) |

**toeicLevel 기준**

| scoreOverall | toeicLevel |
|-------------|------------|
| 1~2 | 입문 (Novice) |
| 3~4 | 초급 (Elementary) |
| 5~6 | 중급 (Intermediate) |
| 7~8 | 중고급 (Advanced) |
| 9~10 | 고급 (Expert) |

> **시험 모드**: 시험 세션의 마지막 음성이 제출되어 11개 세션이 모두 DONE이 되면,  
> 백엔드가 자동으로 `ExamSession`을 COMPLETED 처리하고 TOEIC 예상 점수(0~200)를 계산합니다.  
> 시험 전체 점수는 `GET /api/exam-sessions/{id}/result`에서 확인합니다.

**에러**
- `400 FEEDBACK_ALREADY_EXISTS`: 이미 평가 완료된 세션
- `404 PRACTICE_SESSION_NOT_FOUND`: 세션 없음 또는 타인 소유
- `500 FEEDBACK_EVALUATION_FAILED`: Gemini AI 평가 실패 (세션이 ERROR로 전환됨)

---

### GET `/api/feedbacks/{feedbackId}` — 피드백 단건 조회

**응답 200** — 위 POST 응답과 동일한 구조

---

### GET `/api/feedbacks/session/{sessionId}` — 세션 기준 피드백 조회

`feedbackId`를 모를 때 `sessionId`로 조회합니다.

**응답 200** — 위 POST 응답과 동일한 구조

**에러**
- `404 PRACTICE_SESSION_NOT_FOUND`: 세션 없음
- `404 FEEDBACK_NOT_FOUND`: 해당 세션에 피드백 없음 (아직 제출 안 함)

---

## 8. 시험 세션 API

Part 1~5의 11문제를 처음부터 끝까지 실제 시험처럼 풀어보는 모드.

### POST `/api/exam-sessions` — 시험 시작

**⚠️ Gemini API를 6번 호출하므로 18~48초 소요 가능**  
(Part 1 ×2회, Part 2 ×1회, Part 3/4/5 ×3회)

이미 진행 중인 시험(IN_PROGRESS)이 있으면 `400 EXAM_ALREADY_IN_PROGRESS` 반환.  
먼저 `PATCH /api/exam-sessions/{id}/abandon`으로 포기 후 새 시험 시작 필요.

**요청**: Body 없음

**응답 200**
```json
{
  "examSessionId": 1,
  "parts": [
    {
      "partId": 1,
      "questionGroupId": null,
      "contextContent": null,
      "sessions": [
        {
          "sessionId": 1,
          "questionId": 1,
          "sequenceNo": null,
          "content": "Attention shoppers. Our store will be closing in 30 minutes...",
          "imageUrl": null,
          "prepSeconds": 45,
          "responseSeconds": 45
        },
        {
          "sessionId": 2,
          "questionId": 2,
          "sequenceNo": null,
          "content": "Welcome to the annual technology conference...",
          "imageUrl": null,
          "prepSeconds": 45,
          "responseSeconds": 45
        }
      ]
    },
    {
      "partId": 2,
      "questionGroupId": null,
      "contextContent": null,
      "sessions": [
        {
          "sessionId": 3,
          "questionId": 3,
          "sequenceNo": null,
          "content": "Please describe the photograph in as much detail as possible.",
          "imageUrl": "https://images.unsplash.com/photo-xxxxx",
          "prepSeconds": 45,
          "responseSeconds": 30
        }
      ]
    },
    {
      "partId": 3,
      "questionGroupId": 1,
      "contextContent": "Imagine you are talking on the phone with someone conducting a survey about your dining habits.",
      "sessions": [
        {
          "sessionId": 4, "questionId": 4, "sequenceNo": 1,
          "content": "How often do you eat at restaurants?",
          "imageUrl": null, "prepSeconds": 3, "responseSeconds": 15
        },
        {
          "sessionId": 5, "questionId": 5, "sequenceNo": 2,
          "content": "What type of cuisine do you prefer when dining out?",
          "imageUrl": null, "prepSeconds": 3, "responseSeconds": 15
        },
        {
          "sessionId": 6, "questionId": 6, "sequenceNo": 3,
          "content": "Why do you enjoy eating at restaurants rather than cooking at home? Please explain in detail.",
          "imageUrl": null, "prepSeconds": 3, "responseSeconds": 30
        }
      ]
    },
    {
      "partId": 4,
      "questionGroupId": 2,
      "contextContent": "City Library — Spring Reading Event\n\nDate: April 10–20\nVenue: Central Hall, 2nd Floor\nRegistration Fee: Free\nSchedule:\n  10:00 AM – Opening Ceremony\n  11:00 AM – Author Talk: Jane Kim\n  2:00 PM  – Book Club Discussion\n  4:00 PM  – Children's Story Hour",
      "sessions": [
        {
          "sessionId": 7, "questionId": 7, "sequenceNo": 1,
          "content": "Where is the event being held?",
          "imageUrl": null, "prepSeconds": 3, "responseSeconds": 15
        },
        {
          "sessionId": 8, "questionId": 8, "sequenceNo": 2,
          "content": "What time does the Author Talk begin?",
          "imageUrl": null, "prepSeconds": 3, "responseSeconds": 15
        },
        {
          "sessionId": 9, "questionId": 9, "sequenceNo": 3,
          "content": "I'd like to bring my 7-year-old child. What activities are available for children, and is there any cost?",
          "imageUrl": null, "prepSeconds": 3, "responseSeconds": 30
        }
      ]
    },
    {
      "partId": 5,
      "questionGroupId": 3,
      "contextContent": "Some companies now require employees to work from the office five days a week, while others continue to offer flexible remote work options.",
      "sessions": [
        {
          "sessionId": 10, "questionId": 10, "sequenceNo": 1,
          "content": "Do you think employees should be required to work in the office full-time? Please give reasons.",
          "imageUrl": null, "prepSeconds": 45, "responseSeconds": 60
        },
        {
          "sessionId": 11, "questionId": 11, "sequenceNo": 2,
          "content": "What are the potential drawbacks of mandatory full-time office work for both employees and companies? Please explain in detail.",
          "imageUrl": null, "prepSeconds": 45, "responseSeconds": 60
        }
      ]
    }
  ]
}
```

**응답 구조**

- `parts`: 항상 5개 (Part 1~5 순서 보장)
- `parts[].sessions`: 해당 파트의 세션 목록 (Parts에 따라 1~3개)
- 진행 순서: `parts[0].sessions` → `parts[1].sessions` → ... → `parts[4].sessions`

**에러**
- `400 EXAM_ALREADY_IN_PROGRESS`: 진행 중 시험 있음
- `500 QUESTION_GENERATION_FAILED`: Gemini 문제 생성 실패

---

### GET `/api/exam-sessions/{examSessionId}` — 시험 진행 상태 조회

시험 재진입 시 어느 질문까지 완료했는지 파악.

**응답 200**
```json
{
  "examSessionId": 1,
  "status": "IN_PROGRESS",
  "startedAt": "2026-05-29T10:00:00",
  "totalSessions": 11,
  "completedSessions": 3,
  "parts": [
    {
      "partId": 1,
      "questionGroupId": null,
      "contextContent": null,
      "totalSessions": 2,
      "completedSessions": 2,
      "sessions": [
        { "sessionId": 1, "sequenceNo": null, "questionContent": "Attention shoppers...", "status": "DONE" },
        { "sessionId": 2, "sequenceNo": null, "questionContent": "Welcome to the annual...", "status": "DONE" }
      ]
    },
    {
      "partId": 2,
      "questionGroupId": null,
      "contextContent": null,
      "totalSessions": 1,
      "completedSessions": 1,
      "sessions": [
        { "sessionId": 3, "sequenceNo": null, "questionContent": "Please describe...", "status": "DONE" }
      ]
    },
    {
      "partId": 3,
      "questionGroupId": 1,
      "contextContent": "Imagine you are talking...",
      "totalSessions": 3,
      "completedSessions": 0,
      "sessions": [
        { "sessionId": 4, "sequenceNo": 1, "questionContent": "How often do you eat at restaurants?", "status": "PENDING" },
        { "sessionId": 5, "sequenceNo": 2, "questionContent": "What type of cuisine...", "status": "PENDING" },
        { "sessionId": 6, "sequenceNo": 3, "questionContent": "Why do you enjoy eating...", "status": "PENDING" }
      ]
    }
  ]
}
```

| `status` 값 | 의미 |
|-------------|------|
| `IN_PROGRESS` | 진행 중 |
| `EVALUATING` | 모든 음성 제출 완료, 점수 계산 중 |
| `COMPLETED` | 완료, 점수 확인 가능 |
| `ABANDONED` | 포기 |

---

### GET `/api/exam-sessions/{examSessionId}/result` — 시험 결과 조회

**응답 200 — 완료된 시험**
```json
{
  "examSessionId": 1,
  "status": "COMPLETED",
  "predictedScore": 140,
  "predictedLevel": 6,
  "completedAt": "2026-05-29T11:30:00",
  "totalSessions": 11,
  "completedSessions": 11,
  "partResults": [
    { "partId": 1, "totalSessions": 2, "completedSessions": 2, "averageScore": null },
    { "partId": 2, "totalSessions": 1, "completedSessions": 1, "averageScore": null },
    { "partId": 3, "totalSessions": 3, "completedSessions": 3, "averageScore": null },
    { "partId": 4, "totalSessions": 3, "completedSessions": 3, "averageScore": null },
    { "partId": 5, "totalSessions": 2, "completedSessions": 2, "averageScore": null }
  ]
}
```

**응답 200 — 진행 중인 시험 (미완료)**
```json
{
  "examSessionId": 1,
  "status": "IN_PROGRESS",
  "predictedScore": null,
  "predictedLevel": null,
  "completedAt": null,
  "totalSessions": 11,
  "completedSessions": 5,
  "partResults": [...]
}
```

| 필드 | 설명 |
|------|------|
| `predictedScore` | 예상 TOEIC Speaking 점수 (20~200, 10점 단위). `COMPLETED` 시만 값 있음 |
| `predictedLevel` | 예상 레벨 (1~8). `COMPLETED` 시만 값 있음 |
| `averageScore` | 현재 `null` (Feedback 집계 기능 향후 추가 예정) |

**TOEIC Speaking 레벨 기준**

| 점수 | 레벨 |
|------|------|
| ~60 | 1 |
| ~80 | 2 |
| ~100 | 3 |
| ~120 | 4 |
| ~140 | 5 |
| ~160 | 6 |
| ~180 | 7 |
| 200 | 8 |

---

### GET `/api/exam-sessions` — 내 시험 목록

**응답 200**
```json
[
  {
    "examSessionId": 1,
    "status": "COMPLETED",
    "predictedScore": 140,
    "predictedLevel": 6,
    "startedAt": "2026-05-29T10:00:00",
    "completedAt": "2026-05-29T11:30:00"
  },
  {
    "examSessionId": 2,
    "status": "ABANDONED",
    "predictedScore": null,
    "predictedLevel": null,
    "startedAt": "2026-05-28T14:00:00",
    "completedAt": "2026-05-28T14:10:00"
  }
]
```

---

### PATCH `/api/exam-sessions/{examSessionId}/abandon` — 시험 포기

`IN_PROGRESS` 상태인 시험만 포기 가능.

**응답 204** (body 없음)

**에러**
- `400 EXAM_SESSION_ALREADY_FINISHED`: 이미 완료/포기된 시험
- `404 EXAM_SESSION_NOT_FOUND`: 시험 없음 또는 타인 소유

---

## 9. 문제 API (보조)

주로 연습/시험 세션 시작 시 내부적으로 사용되지만, 직접 호출도 가능합니다.

### POST `/api/questions/generate` — 문제 생성

```json
// 요청
{ "partId": 4 }

// 응답 — Part 1/2 (단일 문제)
{
  "groupId": null,
  "contextContent": null,
  "questions": [
    { "id": 1, "partId": 1, "sequenceNo": null, "content": "...", "imageUrl": null, "prepSeconds": 45, "responseSeconds": 45 }
  ]
}

// 응답 — Part 3/4/5 (그룹 문제)
{
  "groupId": 5,
  "contextContent": "Spring Conference Schedule\n...",
  "questions": [
    { "id": 10, "partId": 4, "sequenceNo": 1, "content": "Where is the event?", ... },
    { "id": 11, "partId": 4, "sequenceNo": 2, "content": "What time does it start?", ... },
    { "id": 12, "partId": 4, "sequenceNo": 3, "content": "I need to bring a colleague who...", ... }
  ]
}
```

---

## 10. 전체 서비스 플로우

### 연습 모드 플로우

```
1. POST /api/practice-sessions  { partId: 3 }
   → sessions 배열 반환. sessions[i].sessionId 보관.

2. FE: contextContent 표시 → 각 세션 순서대로 타이머 + 녹음

3. 각 세션 녹음 완료 후:
   POST /api/feedbacks  (multipart: sessionId + audio)
   → FeedbackResponse 반환 (점수 + strengths + improvements + toeicLevel)

4. 모든 세션 완료 후 결과 화면 표시
   (개별 피드백은 GET /api/feedbacks/session/{sessionId} 로 재조회 가능)
```

### 시험 모드 플로우

```
1. 진행 중 시험 확인
   GET /api/exam-sessions → status=IN_PROGRESS인 항목 있으면 재진입 안내

2. POST /api/exam-sessions  (body 없음)
   → 11개 세션 모두 포함된 parts 배열 반환 (18~48초 대기)

3. FE: parts 순서대로(Part1→5) 각 세션 타이머 + 녹음 진행

4. 각 세션 녹음 완료 후:
   POST /api/feedbacks  (multipart: sessionId + audio)
   → 개별 피드백 즉시 반환

5. 11번째 세션 제출 완료 시 백엔드가 자동으로:
   ExamSession status → COMPLETED
   예상 점수(0~200) 계산 및 저장

6. GET /api/exam-sessions/{examSessionId}/result
   → predictedScore, predictedLevel 표시

7. 포기 시: PATCH /api/exam-sessions/{examSessionId}/abandon
```

### 로그인 상태 확인 플로우 (앱 진입 시)

```
GET /api/auth/me
  ├─ 200 + hasEmail=true  → 메인 화면 진입
  ├─ 200 + hasEmail=false → /additional-info 페이지 (이메일 입력)
  │                         └─ PATCH /api/auth/profile { email: "..." }
  │                            → 완료 후 메인 화면
  └─ 401                  → 로그인 페이지
                            └─ window.location = "/oauth2/authorization/kakao"
```

---

## 부록: API 요약 표

| Method | Path | 설명 | 인증 |
|--------|------|------|------|
| GET | `/api/auth/me` | 내 정보 조회 | ✅ |
| POST | `/api/auth/logout` | 로그아웃 | ✅ |
| PATCH | `/api/auth/profile` | 이메일 등록 | ✅ |
| POST | `/api/practice-sessions` | 연습 시작 (문제 생성) | ✅ |
| GET | `/api/practice-sessions` | 내 연습 기록 | ✅ |
| GET | `/api/practice-sessions/{sessionId}` | 세션 상세 | ✅ |
| POST | `/api/feedbacks` | 음성 제출 + AI 평가 | ✅ |
| GET | `/api/feedbacks/{feedbackId}` | 피드백 조회 | ✅ |
| GET | `/api/feedbacks/session/{sessionId}` | 세션 기준 피드백 조회 | ✅ |
| POST | `/api/exam-sessions` | 시험 시작 | ✅ |
| GET | `/api/exam-sessions` | 내 시험 목록 | ✅ |
| GET | `/api/exam-sessions/{examSessionId}` | 시험 진행 상태 | ✅ |
| GET | `/api/exam-sessions/{examSessionId}/result` | 시험 결과 | ✅ |
| PATCH | `/api/exam-sessions/{examSessionId}/abandon` | 시험 포기 | ✅ |
| POST | `/api/questions/generate` | 문제 생성 (보조) | ✅ |
| GET | `/api/questions/{id}` | 문제 단건 조회 (보조) | ✅ |
| GET | `/api/questions/group/{groupId}` | 그룹 문제 조회 (보조) | ✅ |
