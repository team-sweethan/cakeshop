package com.cakeshop.domain.community.dto.view;

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 현규
 * 작성일 : 2026-08-18
 * 기능 : 커뮤니티 화면 데이터 전달
 * 설명 : PostImageView 화면에 전달할 첨부 이미지를 정의한다.
 * ******************************
 */
public record PostImageView(
        Long id,
        String imageUrl,
        int sortOrder
) {
}
