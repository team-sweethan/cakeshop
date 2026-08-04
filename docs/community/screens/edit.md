# 수정 — `GET /community/{postId}/edit`

> 이 파일의 형식과 검사 규칙은 `docs/community/SCREENS.md`에 있다.

- 상태: 구현됨 (조각 2)
- 템플릿: `customer/community/form.html`
- 핸들러: `CommunityController.editForm`, 저장은 `CommunityController.edit`(`POST /community/{postId}/edit`)
- 접근: 로그인 + **작성자 본인만.** 남의 글이면 상세와 같은 404다 (존재를 흘리지 않는다). 차단된 글은 작성자에게도 403 — 존재는 이미 아는 상대이고, 404를 주면 왜 막혔는지 알 수 없다

**작성 화면과 같은 템플릿을 쓴다.** 수정 항목이 작성 항목과 같아서(DOMAIN.md 6.3) 마크업을 두 벌로 두면 한쪽만 고치는 일이 생긴다. 선례도 공용이다(`ProductAdminController`, `CouponAdminController`). 갈리는 것은 `editingPostId` 모델 하나뿐이다.

| `editingPostId` | 제목 | 버튼 | 전송 주소 | 취소 |
|---|---|---|---|---|
| 없음 | `글쓰기` | `등록` | `POST /community` | 목록 |
| 있음 | `글 수정` | `수정` | `POST /community/{postId}/edit` | 그 글의 상세 |

검증에 실패해도 `editingPostId`가 남아야 한다. 빠지면 재전송이 **작성으로 나가 글이 하나 더 생긴다.**

## 화면 문자열

| 문자열 | 언제 보이나 | 고정한 테스트 |
|---|---|---|
| `글 수정` | `editingPostId`가 있을 때 (제목과 버튼) | `CommunityScreenRenderingTests.communityEditForm_author_rendersExistingValues` |

## 결정한 것

| 정한 것 | 결정 |
|---|---|
| 템플릿을 새로 둘지 | **작성 화면과 공용.** 위 참고 |
| 차단된 글의 수정 | **막는다.** 상세에서 버튼을 숨기는 것으로는 부족하다 — 작성자는 차단된 글의 본문과 사유를 보므로 주소를 알고, 화면 없이 요청만 보낼 수 있다. `CommunityService`가 403으로 거절한다 (DOMAIN.md 4.2) |
| 카테고리 변경 허용 | **허용.** 단 활성 카테고리로만 옮길 수 있다 (DOMAIN.md 6.3, 6.8) |

수정 후에는 상세에 `(수정됨)`이 켜진다. 이 표시는 이력 테이블 없이 `updated_at > created_at`으로만 판단하므로, **수정과 무관한 UPDATE가 하나라도 끼면 거짓이 된다** (조회수에서 이미 한 번 겪었다). 그래서 수정 UPDATE는 `updated_at`을 건드리지 않고 컬럼의 자동 갱신에 맡기고, 조회수 UPDATE는 반대로 명시해서 보존한다.
