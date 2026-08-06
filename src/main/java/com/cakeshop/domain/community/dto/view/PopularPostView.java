package com.cakeshop.domain.community.dto.view;

/**
 * 목록 화면 상단 인기글 영역의 한 줄(docs/community/DOMAIN.md 6.9).
 *
 * <b>선정 당시의 조회수·좋아요·댓글 수를 담지 않는다.</b> 스냅샷에는 그 값들이 있지만
 * (순위가 왜 그랬는지를 사후에 설명하기 위해서다) 화면으로는 내리지 않는다 — 같은
 * 글이 바로 아래 목록에도 나오고, 목록은 <b>현재</b> 수치를 보여 준다. 한 화면에 같은
 * 글의 숫자가 둘이면 사용자에게는 어느 쪽도 못 믿을 값이 된다.
 *
 * 컴포넌트 순서는 CommunityMapper.xml의 SELECT 컬럼 순서와 맞춰 둔다.
 */
public record PopularPostView(
        int ranking,
        Long postId,
        String categoryName,
        String title
) {
}
