package com.cakeshop.domain.community.dto.view;

/**
 * 목록 필터와 글쓰기 화면의 카테고리 선택지를 담는다.
 *
 * <p>비활성 카테고리는 선택지에 넣지 않는다. 다만 이미 그 카테고리로 작성된 글은
 * 그대로 보인다(docs/community/DOMAIN.md 6.8).
 *
 * @param id 카테고리 식별자
 * @param code 카테고리 코드
 * @param name 화면에 표시할 카테고리 이름
 */
public record PostCategoryView(
        Long id,
        String code,
        String name
) {
}
