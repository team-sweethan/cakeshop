# 글쓰기 — `GET /community/new`

> 이 파일의 형식과 검사 규칙은 `docs/community/SCREENS.md`에 있다.

- 상태: 목업 (조각 2에서 연결)
- 템플릿: `customer/community/form.html`
- 핸들러: `CommunityController.createForm`
- 접근: **로그인 필요.** 비로그인은 로그인 화면으로 보내진다 (DOMAIN.md 5). 단 `local` 프로필의 목업 미리보기(`publicPreview`)가 켜져 있으면 비로그인도 열린다 — `SecurityConfig`의 예외 목록에 `/community/new`가 들어 있다
- **저장 경로가 없다.** 화면만 열린다

> **이 화면은 아직 목업이다.** 컨트롤러가 뷰 이름만 반환하고, 등록 버튼은 `customer-mockup.js`가 가로채 목록으로 되돌려 보낸다.

```
┌─────────────────────────────────────────────────────┐
│ [공통 헤더 fragment]                                 │
├─────────────────────────────────────────────────────┤
│  [ 목업 안내 fragment ]                              │
│  글쓰기                                              │
│                                                     │
│  분류 *      [ 선택하세요        ▾ ]                 │
│  제목 *      [                    ]                 │
│  내용 *      [                    ]                 │
│  사진 첨부   [ 파일 선택 ]                           │
│                                                     │
│  [ 취소 ]                              [ 등록 ]      │
├─────────────────────────────────────────────────────┤
│ [공통 푸터 fragment]                                 │
└─────────────────────────────────────────────────────┘
```

## 화면 문자열

| 문자열 | 언제 보이나 | 고정한 테스트 |
|---|---|---|
| `mock-notice` | 로그인 상태에서 항상. 목업임을 알리는 공통 fragment | `CommunityScreenRenderingTests.communityCreateForm_stillRendersMockNotice` |
| `data-mock-form` | 항상. 이 속성이 있는 동안 폼은 서버로 전송되지 않는다 | `CommunityScreenRenderingTests.communityCreateForm_stillRendersMockNotice` |
| `글쓰기` | 항상 | `CommunityScreenRenderingTests.communityCreateForm_stillRendersMockNotice` |
| `선택하세요` | 항상 (분류 기본 선택지) | 없음 |
| `취소` | 항상 | 없음 |
| `등록` | 항상 | 없음 |

## 조각 2에서 반드시 고쳐야 할 거짓

| 지금 화면 | 무엇이 틀렸나 |
|---|---|
| 분류 선택지가 `후기`/`질문`/`자유`/`레시피` 하드코딩 | DB의 활성 카테고리는 `QNA`/`REVIEW`/`FREE` 셋뿐이다. `레시피`는 **비활성** 카테고리라 목록 필터에는 없는데 여기서는 고를 수 있다. 목록과 같은 `categories` 모델을 써야 한다 (DOMAIN.md 6.8) |
| `사진 첨부` 입력과 "최대 5장" 안내 | 이미지 업로드는 1차 범위 밖이다. 저장할 곳이 없다 |
| `local` 프로필에서 비로그인도 열린다 | 목업 미리보기 예외다. 저장 경로가 생기면 **비로그인이 폼을 열어 채우고 마지막에야 거절당하는** 흐름이 된다. `SecurityConfig`의 `publicPreview` 목록에서 `/community/new`를 뺄지 그때 결정한다 |
| 등록 버튼이 `customer-mockup.js`에 잡혀 있다 | 실제 `POST /community`로 보내면서 `data-mock-form`과 목업 안내를 함께 걷어내야 한다. 하나만 지우면 "저장된 것처럼 보이는데 안 되는" 화면이 남는다 |
| 제목 `maxlength="100"`만 있고 본문 길이 제한이 없다 | 본문은 1~5000자다 (DOMAIN.md 7). 화면 제한이 없으면 서버에서만 걸려 사용자가 긴 글을 다 쓰고 나서 거절당한다 |
