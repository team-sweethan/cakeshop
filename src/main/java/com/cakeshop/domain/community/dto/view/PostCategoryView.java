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
// 노출 중인 게시판 카테고리 한 줄. 목록 화면의 카테고리 필터와 작성 폼의 선택 상자가 함께 쓴다.
// code 는 프로그램이 쓰는 식별 문자열, name 은 화면에 보이는 이름이다 (code = "QNA", name = "질문").
public record PostCategoryView(
        Long id,
        String code,
        String name
) {
}
