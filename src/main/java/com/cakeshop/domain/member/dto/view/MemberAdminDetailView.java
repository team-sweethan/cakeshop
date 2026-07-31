package com.cakeshop.domain.member.dto.view;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.cakeshop.domain.member.entity.MemberStatus;

/**
 * 관리자 회원 상세 화면에 표시할 정보를 담는다.
 */
public record MemberAdminDetailView(
        Long id,
        String name,
        String nickname,
        String email,
        String phone,
        LocalDate birthDate,
        String role,
        MemberStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime suspendedAt,
        String suspendedReason,
        LocalDateTime withdrawnAt
) {
}
