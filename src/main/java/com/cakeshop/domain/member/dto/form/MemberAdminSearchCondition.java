package com.cakeshop.domain.member.dto.form;

import com.cakeshop.domain.member.entity.MemberStatus;
import lombok.Getter;
import lombok.Setter;

/**
 * 관리자 회원 목록의 탭, 검색어, 상태 조건을 담는다.
 */
@Getter
@Setter
public class MemberAdminSearchCondition {

    private String keyword;
    private MemberStatus status;
    private MemberAdminListType listType = MemberAdminListType.MEMBERS;

    public String normalizedKeyword() {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }

        return keyword.trim();
    }

    public boolean isMembers() {
        return listType == MemberAdminListType.MEMBERS;
    }

    public boolean isWithdrawn() {
        return listType == MemberAdminListType.WITHDRAWN;
    }

    public boolean isAdmins() {
        return listType == MemberAdminListType.ADMINS;
    }
}
