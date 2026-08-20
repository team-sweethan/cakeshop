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
// 게시글에 붙은 첨부 이미지 한 장. 상세 화면의 이미지 영역과 작성·수정 폼의 기존 첨부 목록이 쓴다.
// sortOrder 는 화면에 그릴 순서라서, 정렬은 SQL 이 이 값으로 해 두고 화면은 받은 순서대로 그린다.
public record PostImageView(
        Long id,
        String imageUrl,
        int sortOrder
) {
}
