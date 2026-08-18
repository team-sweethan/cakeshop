# 조각 2 — 작성·수정·삭제

> **끝난 조각의 기록이다. 지금 구속하지 않는다.**
> 이 조각이 만든 규칙의 정본은 `../specs/community-post.md`다.
> 조각 순서와 진행 상태, 위험과 결정 로그는 `../PLAN.md`.

- 소유권 검증은 인증 사용자 기준으로 Service에서
- 삭제 = `PUBLISHED → DELETED` 전이
- 입력 검증 (DOMAIN.md 7)

**검증**: 남의 글 수정·삭제 시도 거부, `BLOCKED` 글 수정·삭제 시도 거부, 검증 실패 케이스, 공백만 입력 거부.

**완료 (2026-08-03)**. `CommunityMapper` +4 statement(`insertPost`·`updatePost`·`deletePost`·`existsActiveCategory`), `Post` 엔티티(쓰기 경로 필드만), `dto/form/PostForm`, `CommunityService` 3개 메서드 + `getEditablePost`, `CommunityController` 5개 핸들러, `form.html` 전면 교체(작성·수정 공용), `detail.html` 수정·삭제 버튼. migration은 없다 — `posts`에 필요한 컬럼이 V0에 전부 있다.

검증은 `CommunityMapperTests`(+11), `CommunityServiceTests`(+15), `CommunityControllerTests`(+7), `CommunityScreenRenderingTests`(+5)로 고정했다. 하네스 표에 H2a·H2b·H2d를 올렸다.

목업이던 `form.html`의 거짓 다섯 개를 걷어냈다(`screens/new.md`의 "이 화면에 없는 것"). 분류 선택지는 `categories` 모델로, `data-mock-form`과 목업 안내는 삭제, 사진 첨부 입력 삭제, 본문 `maxlength=5000` 추가. `SecurityConfig`의 `publicPreview` 목록에서 `/community/new`도 뺐다 — 저장 경로가 생긴 화면을 비로그인에게 열어 두면 폼을 다 채우고 등록에서야 튕긴다.

`screens/edit.md`의 보류 3건은 `../PLAN.md`의 결정 로그에 남겼다.
