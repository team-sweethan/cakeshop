package com.cakeshop.domain.community.dto.view;

// 목록 화면 상단 인기글 영역의 한 줄. 순위·링크·제목 넷만 담는다.
// 조회수·좋아요 수는 일부러 없다 — 바로 아래 목록이 현재 수치를 보여 주므로 여기 숫자를 또 내리지 않는다.
// 컴포넌트 순서는 CommunityMapper.xml 의 SELECT 컬럼 순서와 맞춰 둔다(MyBatis 가 그 순서로 생성자를 부른다).
public record PopularPostView(
        int ranking,
        Long postId,
        String categoryName,
        String title
) {
}
