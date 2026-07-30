package com.cakeshop.domain.member.dto.form;

import java.util.Arrays;

/**
 * 관리자 회원 목록의 탭 구분을 나타낸다.
 */
public enum MemberAdminListType {

    MEMBERS("members"),
    WITHDRAWN("withdrawn"),
    ADMINS("admins");

    private final String queryValue;

    MemberAdminListType(String queryValue) {
        this.queryValue = queryValue;
    }

    public String getQueryValue() {
        return queryValue;
    }

    public static MemberAdminListType from(String value) {
        if (value == null || value.isBlank()) {
            return MEMBERS;
        }

        return Arrays.stream(values())
                .filter(type -> type.queryValue.equalsIgnoreCase(value))
                .findFirst()
                .orElse(MEMBERS);
    }
}
