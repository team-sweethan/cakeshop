package com.cakeshop.domain.member.dto.view;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.cakeshop.domain.member.entity.MemberStatus;

/**
 * 관리자 회원 목록 SQL 조회 결과를 담는다.
 */
public record MemberAdminListRow(
        Long id,
        String name,
        String email,
        String phone,
        LocalDate birthDate,
        MemberStatus status,
        LocalDateTime createdAt,
        LocalDateTime withdrawnAt
) {
}
