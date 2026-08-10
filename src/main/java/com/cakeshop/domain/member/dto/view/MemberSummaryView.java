package com.cakeshop.domain.member.dto.view;

import com.cakeshop.domain.member.entity.MemberStatus;

/** 다른 도메인에 공개할 최소 회원 조회 정보다. */
public record MemberSummaryView(
        Long id,
        String name,
        String role,
        MemberStatus status
) {
}
