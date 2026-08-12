package com.cakeshop.domain.chat.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ChatRoomStatus {
    OPEN("상담 진행 중"),
    CLOSED("상담 종료");
    private final String label;
}