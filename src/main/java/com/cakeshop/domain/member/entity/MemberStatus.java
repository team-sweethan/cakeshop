package com.cakeshop.domain.member.entity;

/** 회원 계정 상태의 DB 저장값이다. */
public enum MemberStatus {
    ACTIVE,
    SUSPENDED,
    WITHDRAWN;

    public boolean canTransitionTo(MemberStatus next) {
        if (next == null) {
            return false;
        }
        return switch (this) {
            case ACTIVE -> next == SUSPENDED || next == WITHDRAWN;
            case SUSPENDED -> next == ACTIVE || next == WITHDRAWN;
            case WITHDRAWN -> false;
        };
    }
}
