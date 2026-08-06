package com.cakeshop.domain.community.dto.view;

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 현규
 * 작성일 : 2026-08-05
 * 기능 : 커뮤니티 화면 데이터 전달
 * 설명 : PostCategoryView 화면에 전달할 데이터를 정의한다.
 * ******************************
 */
public record PostCategoryView(
        Long id,
        String code,
        String name
) {
}
