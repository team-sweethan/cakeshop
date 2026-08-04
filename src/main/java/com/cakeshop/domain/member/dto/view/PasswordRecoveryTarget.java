package com.cakeshop.domain.member.dto.view;

public record PasswordRecoveryTarget(Long memberId, String email) {
}
