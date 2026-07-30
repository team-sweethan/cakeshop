package com.cakeshop.domain.member.dto.view;

import java.time.LocalDateTime;

import com.cakeshop.domain.member.entity.MemberStatus;

/**
 * 관리자 회원 목록 한 행에 표시할 정보를 담는다.
 */
public record MemberAdminListView(
        Long id,
        String name,
        String maskedEmail,
        String maskedPhone,
        String maskedBirthDate,
        MemberStatus status,
        LocalDateTime createdAt,
        LocalDateTime withdrawnAt
) {
}
