package com.cakeshop.domain.member.dto.view;

/**
 * ******************************
 * 작성자 : 김민정
 * 담당자 : 수민
 * 작성일 : 2026-08-11
 * 기능 : 채팅 목록용 회원 정보 연동 계약 DTO
 * 설명 : 관리자 채팅 목록에서 회원 ID 목록으로 고객 이름 및 정보를 일괄 연동 조회하기 위한 DTO.
 * ******************************
 */
public record MemberChatView(
        Long id,
        String name
) {
}
