package com.cakeshop.domain.review.dto.view;

import com.cakeshop.domain.review.dto.query.ReviewImageRow;

// 후기에 붙은 사진 한 장. 상품 상세·내 후기·관리자 화면 어디서나 같은 모양으로 쓴다
// 조회 결과인 ReviewImageRow 를 화면용으로 옮겨 담기만 한다 (지금은 필드가 같지만 통로를 갈라 둔 것)
public record ReviewImageView(
        Long id,
        String imageUrl,
        int sortOrder
) {

    public static ReviewImageView from(ReviewImageRow row) {
        return new ReviewImageView(row.id(), row.imageUrl(), row.sortOrder());
    }

}
