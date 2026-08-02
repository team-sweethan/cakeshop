# Community 화면 명세

> 도메인 규칙의 정본은 `docs/community/DOMAIN.md`, 진행 순서는 `docs/community/PLAN.md`다.
> 이 문서는 **화면에 무엇이 어떤 조건에서 보이는지**만 다룬다.

이 문서는 읽을거리가 아니라 **하네스**다. `CommunityScreenDocTests`가 다음을 검사한다.

1. 여기 적힌 템플릿 목록이 `templates/{customer,admin}/community/`의 실제 파일 목록과 같은가
2. 표의 `문자열`이 해당 템플릿에 실제로 들어 있는가 — **주석과 `th:text` 자리 표시는 걷어내고** 본다
3. `고정한 테스트` 칸에 적은 테스트가 **JUnit이 무조건 실행하는** 테스트인가 (`@Test`가 있고, `@Disabled`도 `@DisabledOnOs` 같은 조건부 비활성화도 없는가)
4. 그 테스트가 **그 문구가 응답에 있다고 단언하는가** — 메서드 본문의 `containsString("...")` 인자로 등장하는가 (`not(...)`으로 감싼 것과 주석은 뺀다)
5. `계획` 상태로 적은 화면의 템플릿이 **아직 없는가**

그래서 문구를 바꾸고 문서를 안 고치면 빌드가 깨지고, 있지도 않거나 꺼져 있거나 **엉뚱한** 테스트를 적어도 깨지고, 계획 화면을 만들고 상태를 안 바꿔도 깨진다.

4번이 없으면 3번만으로는 부족하다. 살아 있는 테스트를 아무렇게나 연결해도 통과하기 때문이다. 실제로 목록의 `좋아요`·`조회`가 두 문구를 전혀 assert하지 않는 테스트에 걸려 있었다. 그 상태에서는 해당 `span`에 `th:if="${false}"`를 붙여 **화면에서 사라지게 만들어도** 하네스가 전부 통과한다.

4번이 "본문에 등장하는가"가 아니라 "**있다고 단언하는가**"인 것도 같은 이유다. 등장만 보면 문구를 입력 fixture로 쓰거나 `not(containsString(...))`으로 **없다고** 단언해도 통과한다. 그러면 문서는 문구가 사라진 상태를 방어한다고 정반대로 주장하게 된다.

3번이 `@Disabled`뿐 아니라 **조건부** 비활성화까지 거절하는 것도 마찬가지다. `@DisabledOnOs`가 붙은 테스트는 CI에서 건너뛰는데, 문서는 그것이 화면을 지킨다고 적어 둔다. 조건이 어떻게 평가될지는 환경에 달렸으므로 `org.junit.jupiter.api.condition` 애너테이션은 전부 거절한다 — 화면을 무조건 지켜야 하는 검사에 환경 조건을 다는 것 자체가 문서와 어긋난다.

> `build.gradle`은 `docs/`와 **`src/test/java`**를 `test` 입력으로 등록한다. 이 검사는 컴파일된 클래스가 아니라 테스트 소스 원문을 읽으므로, 바이트코드가 같은 수정(공백, 괄호 자리)이 검사 결과를 바꾼다. 등록해 두지 않으면 Gradle이 `UP-TO-DATE`로 건너뛰어 로컬에만 잘못된 초록불이 남는다.

`고정한 테스트` 칸에는 쉼표로 여럿을 적을 수 있고, **하나라도** 문구를 확인하면 통과다. 한 문구를 서로 다른 층위에서 받치는 경우가 있어서다 — `(수정됨)`은 렌더링 테스트가 표시 자체를, 매퍼 테스트가 그 표시를 켜는 조건을 지킨다.

2번에서 걷어내는 두 가지는 **템플릿 원문에는 있지만 사용자는 보지 못하는 것**이다. HTML 주석은 규칙 설명이고, `<span th:text="'좋아요 ' + ...">좋아요 0</span>`의 `좋아요 0`은 표현식이 덮어쓰는 자리 표시다. 걷어내지 않으면 표현식을 `'추천 '`으로 바꿔 화면이 달라져도 자리 표시가 남아 검사가 통과한다.

**표 형식을 바꾸지 말 것.** 파서가 `- 상태:` / `- 템플릿:` 줄과, 첫 열이 `문자열`인 표를 읽는다. 화면을 추가하면 같은 형식으로 절을 하나 더 만든다.

### 상태 표기

| 표기 | 뜻 |
|---|---|
| `구현됨` | DB까지 연결되어 실제로 동작한다 |
| `목업` | 템플릿은 있지만 하드코딩이다. 화면이 **거짓말을 하고 있다** |
| `계획` | 템플릿이 아직 없다. 만들면서 이 표기를 지운다 |

## 이 문서가 못 잡는 것

문서 → 코드 방향만 검사한다. **템플릿에 새 블록을 넣고 문서에 안 적으면 아무도 모른다.** 조건부로만 보이는 블록(차단 안내, 빈 목록, 페이지네이션)은 평소 화면에 없어서 리뷰에서도 안 보이므로, 새로 만들 때 이 문서에 줄을 추가하는 것은 사람의 몫이다.

---

## 화면 지도

| 화면 | 주소 | 상태 | 조각 |
|---|---|---|---|
| 목록 | `GET /community` | 구현됨 | 1 |
| 상세 | `GET /community/{postId}` | 구현됨 | 1 |
| 글쓰기 | `GET /community/new` | 목업 | 2 |
| 수정 | `GET /community/{postId}/edit` | 계획 | 2 |
| 관리자 목록 | `GET /admin/community` | 목업 | 5 |
| 관리자 상세 | `GET /admin/community/{postId}` | 목업 | 5 |

**화면이 아닌 것.** 삭제·좋아요·신고·댓글은 새 페이지가 아니라 **상세 화면에 붙는 버튼과 구역**이다. 아래 "상세에 앞으로 붙는 것"에 정리했다.

---

## 목록 — `GET /community`

- 상태: 구현됨 (조각 1)
- 템플릿: `customer/community/list.html`
- 핸들러: `CommunityController.list`
- 접근: 공개. 비로그인도 전부 볼 수 있다 (DOMAIN.md 5)

```
┌─────────────────────────────────────────────────────┐
│ [공통 헤더 fragment]                                 │
├─────────────────────────────────────────────────────┤
│  커뮤니티                                  [ 글쓰기 ] │
│                                                     │
│  (전체) (질문) (후기) (자유)   ← 활성 카테고리만      │
│                                                     │
│  ┌───────────────────────────────────────────────┐  │
│  │ [질문] 딸기 케이크 보관법            좋아요 3  │  │
│  │ 케이크덕후 · 2026.03.01 · 댓글 2 · 조회 41    │  │
│  └───────────────────────────────────────────────┘  │
│  … 한 쪽에 최대 20건, 최신순 …                       │
│                                                     │
│           (이전) (1) (2) (3) (다음)  ← 2쪽 이상일 때 │
├─────────────────────────────────────────────────────┤
│ [공통 푸터 fragment]                                 │
└─────────────────────────────────────────────────────┘
```

### 모델

| 이름 | 타입 | 내용 |
|---|---|---|
| `pageResult` | `PageResult<PostListView>` | 현재 쪽의 글 목록과 쪽 정보 |
| `pageNavigation` | `PageNavigation` | 표시할 번호 구간(`startPage`~`endPage`)과 앞뒤 블록 존재 여부 |
| `categories` | `List<PostCategoryView>` | **활성** 카테고리만 (DOMAIN.md 6.8) |
| `selectedCategoryId` | `Long` | 선택된 필터. 없으면 `null` |

### 화면 문자열

| 문자열 | 언제 보이나 | 고정한 테스트 |
|---|---|---|
| `커뮤니티` | 항상 (제목) | `CommunityScreenRenderingTests.communityList_rendersPostRow` |
| `글쓰기` | 항상 (`/community/new`로 가는 버튼) | 없음 |
| `전체` | 항상 (필터 해제) | 없음 |
| `좋아요` | 글 한 줄마다 | `CommunityScreenRenderingTests.communityList_rendersPostRow` |
| `댓글` | 글 한 줄마다. 삭제된 댓글은 세지 않는다 (DOMAIN.md 4.4) | `CommunityScreenRenderingTests.communityList_rendersPostRow` |
| `조회` | 글 한 줄마다 | `CommunityScreenRenderingTests.communityList_rendersPostRow` |
| `아직 등록된 글이 없습니다.` | 이 쪽에 글이 하나도 없을 때 | `CommunityScreenRenderingTests.communityList_withoutPosts_rendersEmptyMessage` |
| `페이지 이동` | 2쪽 이상일 때 (`nav`의 `aria-label`) | `CommunityScreenRenderingTests.communityList_multiplePages_rendersPageNavigation` |
| `이전` | 2쪽 이상일 때. 앞 블록이 없으면 `btn--disabled`로 남는다 | `CommunityScreenRenderingTests.communityList_multiplePages_rendersPageNavigation` |
| `다음` | 2쪽 이상일 때. 뒤 블록이 없으면 `btn--disabled`로 남는다 | `CommunityScreenRenderingTests.communityList_multiplePages_rendersPageNavigation` |

### 눈으로는 안 잡히는 것

- **작성자 이름은 `post.authorName()`으로 낸다.** 탈퇴 회원이면 닉네임 대신 `탈퇴한 회원`이 나온다 (DOMAIN.md 8). 템플릿에서 `post.authorNickname`을 직접 쓰면 탈퇴 회원 닉네임이 그대로 노출되는데, 화면은 멀쩡해 보인다. → `communityList_withdrawnAuthor_showsPlaceholderName`
- **필터와 쪽 번호는 주소로 표현한다.** 새로고침·뒤로가기·링크 공유에서 유지되어야 하므로 자바스크립트 상태로 두지 않는다.
- **페이지 번호는 `startPage`~`endPage`만 그린다.** 전체 쪽 수만큼 번호를 뿌리면 글이 늘수록 화면이 무너진다.
- **`이전`/`다음`은 한 쪽씩이 아니라 번호 블록 단위로 이동한다.** `PageNavigation`의 정의이며, 한 쪽씩 이동하도록 바꾸면 다른 화면과 동작이 달라진다.
- **본문 미리보기가 없다.** `content`는 TEXT라 목록에서 SELECT하지 않는다 (DOMAIN.md 6.1). 미리보기를 넣으려면 쿼리부터 바뀐다.

---

## 상세 — `GET /community/{postId}`

- 상태: 구현됨 (조각 1)
- 템플릿: `customer/community/detail.html`
- 핸들러: `CommunityController.detail`
- 접근: 공개. 단 노출 여부는 글 상태에 따라 갈린다 (DOMAIN.md 4.3)

```
┌─────────────────────────────────────────────────────┐
│ [공통 헤더 fragment]                                 │
├─────────────────────────────────────────────────────┤
│  [ ← 목록 ]                                          │
│                                                     │
│  ┌───────────────────────────────────────────────┐  │
│  │ 관리자가 차단한 게시글입니다.                  │  │  ← BLOCKED + 작성자 본인
│  │ 광고성 게시물                                  │  │
│  │ 이 글은 다른 회원에게 보이지 않습니다.         │  │
│  └───────────────────────────────────────────────┘  │
│                                                     │
│  [질문]                                             │
│  딸기 케이크 보관법                                  │
│  케이크덕후 · 2026.03.01 · 조회 42 (수정됨)          │
│  ───────────────────────────────────────────────    │
│  본문. 줄바꿈은 그대로 살아난다.                     │
│                                                     │
│  좋아요 3                                            │
│  ───────────────────────────────────────────────    │
│  댓글                                                │
│  ┌───────────────────────────────────────────────┐  │
│  │ 댓글 기능은 준비 중입니다.        ← 조각 3      │  │
│  └───────────────────────────────────────────────┘  │
├─────────────────────────────────────────────────────┤
│ [공통 푸터 fragment]                                 │
└─────────────────────────────────────────────────────┘
```

### 모델

| 이름 | 타입 | 내용 |
|---|---|---|
| `post` | `PostDetailView` | 글 본문과 상태. `blocked`, `edited`, `authorName()`은 뷰가 계산한다 |

### 무엇이 보이는가 (DOMAIN.md 4.3)

| 글 상태 | 비로그인·다른 회원 | 작성자 본인 |
|---|---|---|
| `PUBLISHED` | 정상 노출 | 정상 노출 |
| `BLOCKED` | **404** | 차단 안내 + 사유와 함께 노출 |
| `DELETED` | **404** | **404** (삭제는 종착 상태) |
| 없는 글 | 404 | 404 |

셋을 같은 404로 응답하는 것이 핵심이다. 403은 "그 자리에 글이 있다"는 사실을 흘린다. 관리자도 이 고객 화면에서는 일반 회원과 똑같이 취급한다.

### 화면 문자열

| 문자열 | 언제 보이나 | 고정한 테스트 |
|---|---|---|
| `← 목록` | 항상 | 없음 |
| `조회` | 항상 | `CommunityScreenRenderingTests.communityDetail_rendersContent` |
| `좋아요` | 항상 (숫자만. 누르는 버튼은 조각 4) | 없음 |
| `댓글` | 항상 (구역 제목) | 없음 |
| `white-space:pre-wrap` | 본문 영역. 사용자에게는 **줄바꿈이 살아난 본문**으로 보인다 | `CommunityScreenRenderingTests.communityDetail_rendersContent` |
| `(수정됨)` | `post.edited`, 즉 `updatedAt > createdAt`일 때 | `CommunityScreenRenderingTests.communityDetail_editedPost_showsEditedMark`, `CommunityMapperTests.increaseViewCount_doesNotMarkPostAsEdited` |
| `관리자가 차단한 게시글입니다.` | `BLOCKED` + 작성자 본인일 때만 | `CommunityScreenRenderingTests.communityDetail_blockedPost_author_showsBlockedReason` |
| `이 글은 다른 회원에게 보이지 않습니다.` | 위와 같은 조건 | `CommunityScreenRenderingTests.communityDetail_blockedPost_author_showsBlockedReason` |
| `댓글 기능은 준비 중입니다.` | 항상 (조각 3에서 실제 댓글로 교체) | 없음 |

### 눈으로는 안 잡히는 것

- **본문과 제목은 `th:text`로 낸다. `th:utext`를 쓰지 않는다** (DOMAIN.md 7). 바꿔도 정상 글에서는 화면이 똑같아 보이고, 누군가 `<script>`를 저장한 순간에만 드러난다. → `communityDetail_htmlInContent_isEscaped`, `communityDetail_titleWithHtml_isEscaped`
- **줄바꿈은 `<br>` 치환이 아니라 CSS로 살린다.** 치환하려면 `th:utext`가 필요해져서 위 규칙과 충돌한다.
- **차단 안내 블록은 평소 화면에 절대 안 나온다.** "차단된 글을 작성자가 연다"는 조건에서만 그려지므로, 표현식이 깨져도 사람 눈으로는 영원히 발견되지 않는다. 렌더링 테스트가 유일한 방어선이다.
- **차단된 글에는 작성자도 아무 조치를 할 수 없다.** 사유만 전달하고 수정·삭제 버튼을 주지 않는다 (DOMAIN.md 4.2 — `BLOCKED → DELETED` 금지).
- **조회수는 노출되는 글에만 오른다.** 그리고 조회수가 올라도 `(수정됨)`이 켜지면 안 된다 — `posts.updated_at`이 `ON UPDATE CURRENT_TIMESTAMP`라 SQL에서 명시적으로 보존한다 (DOMAIN.md 6.2, 6.3).

### 상세에 앞으로 붙는 것

새 페이지가 생기는 게 아니라 **이 화면이 채워진다.** 조각을 진행하며 이 표의 줄을 위 문자열 표로 옮긴다.

| 무엇 | 조각 | 화면에 어떻게 나타나나 | 미리 정해진 것 |
|---|---|---|---|
| 수정·삭제 버튼 | 2 | 상단 `← 목록` 줄 오른쪽. **작성자 본인에게만.** 차단된 글에는 작성자에게도 주지 않는다 | 삭제는 `DELETED` 전이. 확인 창을 거친다 |
| 댓글 목록·작성 | 3 | `댓글 기능은 준비 중입니다.` 자리를 대체 | 1단계만. 삭제된 댓글은 자리 표시로 남고 개수에서 빠진다 (DOMAIN.md 4.4) |
| 좋아요 버튼 | 4 | 지금 숫자만 있는 `좋아요 N` 자리 | POST/DELETE 분리, 둘 다 멱등. **"내가 눌렀는지" 표시 여부는 DOMAIN.md 9의 보류 항목** |
| 신고 버튼 | 5 | 본문 아래 | 중복 신고는 에러. 취소 불가 |

---

## 글쓰기 — `GET /community/new`

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

### 화면 문자열

| 문자열 | 언제 보이나 | 고정한 테스트 |
|---|---|---|
| `mock-notice` | 로그인 상태에서 항상. 목업임을 알리는 공통 fragment | `CommunityScreenRenderingTests.communityCreateForm_stillRendersMockNotice` |
| `data-mock-form` | 항상. 이 속성이 있는 동안 폼은 서버로 전송되지 않는다 | `CommunityScreenRenderingTests.communityCreateForm_stillRendersMockNotice` |
| `글쓰기` | 항상 | `CommunityScreenRenderingTests.communityCreateForm_stillRendersMockNotice` |
| `선택하세요` | 항상 (분류 기본 선택지) | 없음 |
| `취소` | 항상 | 없음 |
| `등록` | 항상 | 없음 |

### 조각 2에서 반드시 고쳐야 할 거짓

| 지금 화면 | 무엇이 틀렸나 |
|---|---|
| 분류 선택지가 `후기`/`질문`/`자유`/`레시피` 하드코딩 | DB의 활성 카테고리는 `QNA`/`REVIEW`/`FREE` 셋뿐이다. `레시피`는 **비활성** 카테고리라 목록 필터에는 없는데 여기서는 고를 수 있다. 목록과 같은 `categories` 모델을 써야 한다 (DOMAIN.md 6.8) |
| `사진 첨부` 입력과 "최대 5장" 안내 | 이미지 업로드는 1차 범위 밖이다. 저장할 곳이 없다 |
| `local` 프로필에서 비로그인도 열린다 | 목업 미리보기 예외다. 저장 경로가 생기면 **비로그인이 폼을 열어 채우고 마지막에야 거절당하는** 흐름이 된다. `SecurityConfig`의 `publicPreview` 목록에서 `/community/new`를 뺄지 그때 결정한다 |
| 등록 버튼이 `customer-mockup.js`에 잡혀 있다 | 실제 `POST /community`로 보내면서 `data-mock-form`과 목업 안내를 함께 걷어내야 한다. 하나만 지우면 "저장된 것처럼 보이는데 안 되는" 화면이 남는다 |
| 제목 `maxlength="100"`만 있고 본문 길이 제한이 없다 | 본문은 1~5000자다 (DOMAIN.md 7). 화면 제한이 없으면 서버에서만 걸려 사용자가 긴 글을 다 쓰고 나서 거절당한다 |

---

## 수정 — `GET /community/{postId}/edit`

- 상태: 계획 (조각 2)
- 템플릿: 미정
- 핸들러: 미정
- 접근: 로그인 + **작성자 본인만.** 남의 글이면 상세와 같은 404다 (존재를 흘리지 않는다)

아직 만들지 않았다. 조각 2에서 만들 때 아래를 정하고 이 절을 채운다.

| 정할 것 | 선택지와 고려사항 |
|---|---|
| 템플릿을 새로 둘지 | `form.html`을 작성·수정 공용으로 쓰면 분기가 늘고, `edit.html`을 따로 두면 폼 마크업이 두 벌이 된다. 프로젝트에 선례가 있는지 먼저 확인한다 |
| 차단된 글의 수정 | **막는다.** `BLOCKED → DELETED`가 금지이듯(DOMAIN.md 4.2) 차단 상태를 작성자가 우회할 수 없어야 한다. 상세에서 수정 버튼 자체를 안 보여주는 것으로는 부족하고 Service에서 막아야 한다 |
| 카테고리 변경 허용 | 허용한다 (DOMAIN.md 6.3 — 제목·본문·카테고리 전부). 비활성 카테고리로는 옮길 수 없어야 한다 |

수정 후에는 상세에 `(수정됨)`이 켜진다. 이 표시는 이력 테이블 없이 `updated_at > created_at`으로만 판단하므로, **수정과 무관한 UPDATE가 하나라도 끼면 거짓이 된다** (조회수에서 이미 한 번 겪었다).

---

## 관리자 목록 — `GET /admin/community`

- 상태: 목업 (조각 5에서 연결)
- 템플릿: `admin/community/list.html`
- 핸들러: `CommunityAdminController.list`
- 접근: `hasRole("ADMIN")` (DOMAIN.md 5)

```
┌──────────┬──────────────────────────────────────────┐
│ [사이드바]│ 커뮤니티 관리                             │
│          │ [목업 안내]                               │
│          │ [작성자 검색][제목 검색][분류▾][상태▾][검색]│
│          │ ┌──────────────────────────────────────┐ │
│          │ │번호│분류│제목│작성자│작성일│상태│관리 │ │
│          │ │ 15 │후기│…  │단골  │07.21│정상│…    │ │
│          │ └──────────────────────────────────────┘ │
│          │ 제재된 게시글은 고객 화면에서 열람이 …     │
└──────────┴──────────────────────────────────────────┘
```

### 화면 문자열

| 문자열 | 언제 보이나 | 고정한 테스트 |
|---|---|---|
| `커뮤니티 관리` | 항상 | `CommunityScreenRenderingTests.communityAdminList_rendersForAdmin` |
| `작성자 검색` | 항상 (검색 입력) | 없음 |
| `제목 검색` | 항상 (검색 입력) | 없음 |
| `상세보기` | 글 한 줄마다 | 없음 |
| `제재된 게시글은 고객 화면에서 열람이 차단됩니다.` | 항상 (안내) | `CommunityScreenRenderingTests.communityAdminList_rendersForAdmin` |
| `mock-notice` | 항상. 아직 목업임을 알린다 | `CommunityScreenRenderingTests.communityAdminList_rendersForAdmin` |

---

## 관리자 상세 — `GET /admin/community/{postId}`

- 상태: 목업 (조각 5에서 연결)
- 템플릿: `admin/community/detail.html`
- 핸들러: `CommunityAdminController.detail`
- 접근: `hasRole("ADMIN")`

왼쪽에 게시글 정보·본문·댓글, 오른쪽에 신고 내역과 모더레이션 패널이 있는 2단 구성이다. **차단된 글의 본문을 관리자가 볼 수 있는 유일한 화면**이다 — 고객 경로(`/community/{id}`)에서는 관리자도 404를 받는다 (DOMAIN.md 4.3).

### 화면 문자열

| 문자열 | 언제 보이나 | 고정한 테스트 |
|---|---|---|
| `게시글 정보` | 항상 | `CommunityScreenRenderingTests.communityAdminDetail_rendersForAdmin` |
| `모더레이션` | 항상 (오른쪽 패널) | `CommunityScreenRenderingTests.communityAdminDetail_rendersForAdmin` |
| `신고 내역` | 항상 | `CommunityScreenRenderingTests.communityAdminDetail_rendersForAdmin` |
| `제재 사유` | 항상 (차단 시 필수 입력) | 없음 |
| `목록으로` | 항상 | 없음 |
| `mock-notice` | 항상. 아직 목업임을 알린다 | `CommunityScreenRenderingTests.communityAdminDetail_rendersForAdmin` |

### 조각 5에서 정하거나 고쳐야 할 것

관리자 목업 두 화면은 **도메인 규칙보다 먼저 그려졌다.** 그래서 규칙에 없는 기능이 버튼으로 존재한다. 조각 5는 이 목록을 정리하는 일부터 시작한다.

| 지금 화면 | 무엇이 문제인가 |
|---|---|
| `게시글 삭제` / `게시글 영구 삭제` 버튼 | **DOMAIN.md에 관리자 삭제 권한이 없다.** 관리자 조치는 차단뿐이고(6.7), `BLOCKED → DELETED`는 금지다(4.2). 권한을 새로 정하든 버튼을 없애든 결정이 필요하다. "영구 삭제"라는 말은 soft delete와도 어긋난다 |
| 댓글마다 `삭제` 버튼 | 관리자의 댓글 삭제도 규칙에 없다. 댓글 삭제는 작성자 본인 기준이다 (6.4) |
| 상태 어휘가 `정상`/`제재` | 도메인 용어는 `PUBLISHED`/`BLOCKED`/`DELETED`이고 문서에서는 "차단"이라 부른다. 화면·문서·코드가 서로 다른 말을 쓰고 있다. 하나로 맞춘다 |
| `IP` 표시 | `posts`에 IP 컬럼이 없다. 넣으려면 스키마 변경이고, 개인정보라 보관 근거가 필요하다 |
| `첨부 이미지` 자리 | `post_images`는 1차에서 쓰지 않는다 (DOMAIN.md 2) |
| 작성자/제목 검색, 분류·상태 필터 | 관리자 목록의 필터·정렬은 **DOMAIN.md 9의 보류 항목**이다. 고객 목록에는 검색이 없다 |
| 분류 선택지 `후기/질문/자유/레시피` | 글쓰기 화면과 같은 문제. 활성 카테고리는 셋뿐이다 |

---

## 만들지 않는 화면

`DOMAIN.md` 2절이 범위 밖으로 정한 것들이다. 요청이 들어오면 여기를 먼저 본다.

| 화면 | 왜 안 만드나 |
|---|---|
| **인기글 / 정렬 옵션** | 범위 밖. 게다가 **조회수를 기준으로 삼을 수 없다** — DOMAIN.md 6.2가 "조회수는 참고용 표시일 뿐 정렬·순위에 쓰이지 않는다"를 근거로 중복 방지를 아예 빼서, 새로고침만으로 순위를 올릴 수 있다. 좋아요 기준이면 `post_likes`의 UNIQUE 덕에 그 문제는 없지만 조각 4 이후에나 데이터가 쌓인다. 넣으려면 (1) 범위 변경, (2) 기준을 좋아요로 고정, (3) 조각 4 이후 — 셋이 세트다 |
| 검색 | 범위 밖. 제목·본문 LIKE 검색은 인덱스를 못 타서 글이 늘면 목록 전체가 느려진다 |
| 무한 스크롤 | 범위 밖 (2차). 쪽 번호 페이징으로 간다 |
| 이미지 첨부 | 범위 밖. `post_images` 테이블은 있지만 쓰지 않는다 |
| 카테고리 관리 화면 | 범위 밖. 카테고리는 migration으로 주입한다 (DOMAIN.md 6.8) |
| 대댓글 | 2차. `parent_comment_id`를 코드에 등장시키지 않는다 (6.4) |
