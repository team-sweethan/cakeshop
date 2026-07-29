package com.cakeshop.domain.member.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MemberStatusTests {

    @Test
    void canTransitionTo_activeMember_allowsSuspensionAndWithdrawal() {
        assertThat(MemberStatus.ACTIVE.canTransitionTo(MemberStatus.SUSPENDED)).isTrue();
        assertThat(MemberStatus.ACTIVE.canTransitionTo(MemberStatus.WITHDRAWN)).isTrue();
    }

    @Test
    void canTransitionTo_suspendedMember_allowsActivationAndWithdrawal() {
        assertThat(MemberStatus.SUSPENDED.canTransitionTo(MemberStatus.ACTIVE)).isTrue();
        assertThat(MemberStatus.SUSPENDED.canTransitionTo(MemberStatus.WITHDRAWN)).isTrue();
    }

    @Test
    void canTransitionTo_withdrawnMember_rejectsEveryTransition() {
        assertThat(MemberStatus.WITHDRAWN.canTransitionTo(MemberStatus.ACTIVE)).isFalse();
        assertThat(MemberStatus.WITHDRAWN.canTransitionTo(MemberStatus.SUSPENDED)).isFalse();
        assertThat(MemberStatus.WITHDRAWN.canTransitionTo(MemberStatus.WITHDRAWN)).isFalse();
        assertThat(MemberStatus.WITHDRAWN.canTransitionTo(null)).isFalse();
    }
}
