# 글쓰기 — `GET /community/new`

> 이 파일의 형식과 검사 규칙은 `docs/community/SCREENS.md`에 있다.

- 상태: 구현됨 (조각 2)
- 템플릿: `customer/community/form.html`
- 핸들러: `CommunityController.createForm`, 저장은 `CommunityController.create`(`POST /community`)
- 접근: **로그인 필요.** 비로그인은 로그인 화면으로 보내진다 (DOMAIN.md 5). `local` 프로필의 목업 미리보기 예외에서도 **뺐다** — 저장 경로가 생긴 화면을 비로그인에게 열어 두면 폼을 다 채우고 등록에서야 튕긴다

수정 화면과 **같은 템플릿**을 쓴다. 무엇이 갈리는지는 [screens/edit.md](edit.md)에 있다.

```
┌─────────────────────────────────────────────────────┐
│ [공통 헤더 fragment]                                 │
├─────────────────────────────────────────────────────┤
│  글쓰기                                              │
│                                                     │
│  분류 *      [ 선택하세요        ▾ ]                 │
│  제목 *      [                    ]                 │
│  내용 *      [                    ]                 │
│                                                     │
│  [ 취소 ]                              [ 등록 ]      │
├─────────────────────────────────────────────────────┤
│ [공통 푸터 fragment]                                 │
└─────────────────────────────────────────────────────┘
```

분류 선택지는 화면에 적어 두지 않고 **DB의 활성 카테고리**(`categories` 모델)를 쓴다. 목록 필터와 같은 값이다 (DOMAIN.md 6.8). 하드코딩하면 비활성 카테고리가 선택지에 남는데, 그건 화면만 봐서는 알 수 없다.

입력 길이 제한은 서버 검증과 같은 값이다 — 제목 100자, 본문 5000자 (DOMAIN.md 7). 화면 제한이 없으면 긴 글을 다 쓰고 나서 거절당한다.

검증에 실패하면 **리다이렉트하지 않고 입력을 되돌려 준다.** 리다이렉트로 처리하면 쓰던 글이 사라진다.

## 화면 문자열

| 문자열 | 언제 보이나 | 고정한 테스트 |
|---|---|---|
| `글쓰기` | 작성 화면에서 (수정 화면은 `글 수정`) | `CommunityScreenRenderingTests.communityCreateForm_rendersActiveCategoriesAndPostsToServer` |
| `선택하세요` | 항상 (분류 기본 선택지) | `CommunityScreenRenderingTests.communityCreateForm_rendersActiveCategoriesAndPostsToServer` |
| `등록` | 작성 화면에서 (수정 화면은 `수정`) | `CommunityScreenRenderingTests.communityCreateForm_rendersActiveCategoriesAndPostsToServer` |
| `취소` | 항상 | 없음 |

## 이 화면에 없는 것

| 없는 것 | 왜 |
|---|---|
| 사진 첨부 | 이미지 업로드는 1차 범위 밖이다. 저장할 곳이 없는 입력이 화면에 남으면 사용자는 첨부가 되는 줄 안다 (`SCREENS.md` 만들지 않는 화면) |
| `mock-notice`·`data-mock-form` | 조각 2에서 걷어냈다. `data-mock-form`이 붙은 폼은 스크립트가 가로채 서버로 보내지 않는다 — 저장 경로를 붙여 놓고 이것만 남기면 **저장된 것처럼 보이는데 안 되는** 화면이 된다 |
