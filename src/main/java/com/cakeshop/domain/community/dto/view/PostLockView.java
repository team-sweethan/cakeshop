package com.cakeshop.domain.community.dto.view;

import com.cakeshop.domain.community.entity.PostStatus;

/**
 * 좋아요 경로가 게시글 행을 잠그면서 함께 읽는 최소 정보.
 *
 * 화면에 나가는 값이 아니라 <b>권한 판단에만</b> 쓴다. 그래서 PostDetailView를 쓰지 않는다 —
 * 그쪽은 카테고리·회원과 조인하는데, MariaDB의 FOR UPDATE는 조인한 테이블의 행까지 잠근다.
 * 좋아요 한 번에 카테고리 행이 잠기면 같은 분류의 모든 글이 서로 줄을 서게 된다.
 *
 * 두 필드인 이유는 4.3의 응답이 갈리기 때문이다. 상태만으로는 차단된 글에 403을 줄지
 * 404를 줄지 정할 수 없고, 그것은 요청자가 작성자인지에 달렸다.
 */
public record PostLockView(
        Long memberId,      // 작성자. 차단된 글의 403/404를 가른다
        PostStatus status
) {
}
