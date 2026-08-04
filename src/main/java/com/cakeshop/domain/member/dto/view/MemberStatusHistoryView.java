package com.cakeshop.domain.member.dto.view;

import java.time.LocalDateTime;

import com.cakeshop.domain.member.entity.MemberStatus;
import com.cakeshop.domain.member.entity.MemberStatusAction;

public record MemberStatusHistoryView(
        Long id,
        MemberStatusAction action,
        MemberStatus beforeStatus,
        MemberStatus afterStatus,
        String reason,
        String processedByName,
        LocalDateTime processedAt
) {
    public String actionLabel() {
        return action == MemberStatusAction.SUSPEND
                ? "이용정지"
                : "정지 해제";
    }

    public String processorLabel() {
        return processedByName == null || processedByName.isBlank()
                ? "기존 데이터"
                : processedByName;
    }

    public String beforeStatusLabel() {
        return statusLabel(beforeStatus);
    }

    public String afterStatusLabel() {
        return statusLabel(afterStatus);
    }

    private String statusLabel(MemberStatus status) {
        return status == MemberStatus.ACTIVE
                ? "정상"
                : "이용정지";
    }
}
