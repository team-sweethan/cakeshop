package com.cakeshop.domain.community.dto.view;

/**
 * 한 게시글의 댓글 수를 두 가지로 센 결과다. 한 번의 쿼리로 둘 다 받는다.
 *
 * 두 값이 갈리는 이유는 삭제된 댓글의 처리 때문이다(docs/community/DOMAIN.md 4.4).
 * 삭제된 댓글은 목록에 자리 표시로 남으므로 "더 보기"로 펼칠 것이 남았는지 판단할 때는
 * 세어야 하고, 화면의 "댓글 N"에는 들어가면 안 된다.
 *
 * 하나로 합치면 둘 중 하나는 반드시 틀린다. 자리 표시를 세면 개수가 부풀고, 안 세면
 * 삭제된 댓글만 남은 구간에서 "더 보기"가 사라져 그 아래 댓글에 닿을 수 없게 된다.
 */
public record CommentCountView(
        long rowCount,        // 자리 표시를 포함한 전체 행 수. "더 보기" 판단에 쓴다
        long publishedCount   // 노출 중인 댓글 수. 화면의 "댓글 N"이다
) {
}
