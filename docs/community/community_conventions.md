# Community 코드 구조 실험 규칙

이 문서는 프로젝트 공통 정본인 [`docs/conventions.md`](../conventions.md)의 원칙을 바꾸지 않고,
`community` 도메인에서 Entity·Form·View 경계를 먼저 적용해 보기 위한 로컬 규칙이다. 다른 도메인의
선례나 전역 규칙으로 간주하지 않으며, 충돌할 때는 `docs/conventions.md`가 우선한다.

## 적용 범위

- `src/main/java/com/cakeshop/domain/community/**`
- `src/main/resources/mapper/community/**`
- `src/main/resources/templates/{customer,admin}/community/**`
- `src/test/java/com/cakeshop/domain/community/**`

업무 상태·권한·입력 규칙은 `DOMAIN.md`와 연결된 `specs/*.md`가 계속 소유한다. 이 문서는 객체 역할과
패키지 경계만 다룬다.

## 객체 역할

### Entity

`entity`에는 커뮤니티 테이블의 저장 행을 표현하는 MyBatis 객체와 그 값을 소유한 상태 enum을 둔다.
Entity를 HTTP 요청, 화면 출력, 부분 수정 명령으로 재사용하지 않는다. 신규 행의 DB 생성 키는 저장 전
`null`일 수 있지만, 수정에 사용하지 않는 필수 컬럼을 일부러 `null`로 채운 Entity는 만들지 않는다.

### Form DTO

`dto/form`에는 Spring MVC가 바인딩하는 변경 가능한 class만 둔다. 문자열 길이와 필수값 같은 입력 형식은
Bean Validation으로 검증하고, 카테고리 존재 여부·소유권·현재 상태는 Service가 검증한다.

### Query DTO

`dto/query`에는 Mapper의 조회 조건과 조회 결과를 둔다. 화면에 직접 전달하지 않고 Service가 업무 검증이나
View 조립에 사용한다. 불변 `record`를 기본으로 하며 이름은 역할에 따라 `*Row`, `*Filter`, `*Aggregate`를
사용한다. 잠금 조회 결과도 화면 View가 아니라 `*Row`로 표현한다.

### Command DTO

`dto/command`에는 Service가 검증을 끝낸 뒤 Mapper 쓰기에 전달하는 값을 둔다. HTTP 바인딩과 Bean
Validation에 사용하지 않으며 불변 `record`를 기본으로 한다. Entity를 부분 UPDATE 매개변수로 재사용해야
할 때만 만들고, 예상만으로 빈 Command 타입을 추가하지 않는다.

### View DTO

`dto/view`에는 Controller가 화면 Model에 제공하는 값과 화면 선택 목록만 둔다. 불변 `record`를 기본으로
하고 Mapper 내부 조회 행을 포함하지 않는다. 작성자 표시명, 수정 여부와 같은 파생값은 View가 만들며,
상태·정렬처럼 값을 소유한 enum은 화면 라벨과 요청 파라미터를 함께 제공할 수 있다.

## 현재 적용 매핑

| 역할 | 타입 |
|---|---|
| Form | `BlockForm`, `CommentForm`, `PostForm`, `ReportForm`, `NoticeForm` |
| Command | `PostUpdateCommand`, `NoticeUpdateCommand` |
| Query | `PostListRow`, `PostDetailRow`, `CommentRow`, `CommentCountRow`, `ReplyCountRow`, `ReportRow`, `PostLockRow`, `NoticeListRow`, `NoticeDetailRow`, `NoticeLockRow`, 관리자 `*Row`(공지 포함) |
| View | `PostListView`, `PostDetailView`, `PostImageView`, `PostCategoryView`, `CommentView`, `CommentThreadView`, `CommentSectionView`, `ReportView`, `NoticeView`, `NoticeSectionView`, `NoticeDetailView`, 관리자 `*View`(공지 포함), 인기글 View |

`NoticeDisplayStatus`는 저장된 값이 아니라 **저장 상태와 노출 기간에서 파생된 판정**이라 `entity`가
아니라 `dto/view`에 둔다. 판정 규칙은 enum이 갖고 Service가 `Clock`으로 만든 시각을 넘겨 부른다 —
화면이 계산하면 같은 규칙이 템플릿마다 한 벌씩 생긴다.

`PostSort`와 `AdminPostSort`는 요청 허용값이면서 화면 선택 목록이고 SQL 분기값이기도 하다. 사용자 입력을
그대로 SQL에 연결하지 않는 제한된 enum이라는 성격을 유지하며 `dto/view`에서 라벨과 파라미터를 제공한다.

## 전역화 판단 기준

community 적용을 마친 뒤 다음을 확인하고 `docs/conventions.md` 반영 여부를 별도로 결정한다.

- View 패키지에서 화면과 무관한 Mapper 값이 실제로 사라졌는가
- Entity에 부분 UPDATE를 위한 불완전 상태가 사라졌는가
- Mapper와 Service 시그니처가 역할을 더 분명하게 드러내는가
- Query·Command 패키지가 탐색을 돕는 충분한 파일 수를 갖는가
- MyBatis 매핑과 테스트 유지 비용이 얻은 명확성보다 커지지 않는가

검증 전에는 다른 도메인에 같은 패키지를 일괄 적용하지 않는다.
