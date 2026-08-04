package com.cakeshop.domain.member.dto.view;

import java.time.LocalDateTime;
import java.util.List;

import com.cakeshop.domain.member.entity.MemberStatus;

/**
 * 관리자 회원 상세 화면에 표시할 정보를 담는다.
 */
public record MemberAdminDetailView(
        Long id,
        String name,
        String nickname,
        String maskedEmail,
        String maskedPhone,
        String maskedBirthDate,
        String role,
        MemberStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime suspendedAt,
        String suspendedReason,
        LocalDateTime withdrawnAt,
        List<MemberStatusHistoryView> statusHistories
) {
}
