package com.cakeshop.domain.member.dto.view;

/** 마이페이지와 회원정보 수정 화면에 노출할 회원 읽기 DTO다. */
public record MemberProfileView(
        String email,
        String name,
        String nickname,
        String phone
) {
}
