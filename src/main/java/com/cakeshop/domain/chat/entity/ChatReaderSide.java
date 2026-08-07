package com.cakeshop.domain.chat.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ChatReaderSide {
    CUSTOMER("고객"),
    ADMIN("관리자");

    private final String label;
}