package com.cakeshop.domain.chat.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ChatResponseStatus {
    WAITING_ADMIN("미답변"),
    WAITING_CUSTOMER("상담중"), // 상담 안 끝났고 고객이 답변 안 했을 경우
    RESOLVED("완료");

    private final String label;
}