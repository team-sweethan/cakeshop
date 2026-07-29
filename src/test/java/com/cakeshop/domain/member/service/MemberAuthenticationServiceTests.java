package com.cakeshop.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.cakeshop.domain.member.dto.view.MemberAuthenticationView;
import com.cakeshop.domain.member.entity.Member;
import com.cakeshop.domain.member.entity.MemberStatus;
import com.cakeshop.domain.member.mapper.MemberMapper;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MemberAuthenticationServiceTests {

    @Mock
    private MemberMapper memberMapper;

    @InjectMocks
    private MemberAuthenticationService memberAuthenticationService;

    @Test
    void findForAuthentication_existingMember_returnsAuthenticationView() {
        Member member = Member.builder()
                .id(1L)
                .email("member@cakeshop.local")
                .password("encoded-password")
                .role("USER")
                .status(MemberStatus.ACTIVE)
                .build();
        when(memberMapper.findByEmail(member.getEmail())).thenReturn(Optional.of(member));

        Optional<MemberAuthenticationView> result =
                memberAuthenticationService.findForAuthentication(member.getEmail());

        assertThat(result).contains(new MemberAuthenticationView(
                member.getId(),
                member.getEmail(),
                member.getPassword(),
                member.getRole(),
                true));
    }

    @ParameterizedTest
    @EnumSource(value = MemberStatus.class, names = {"SUSPENDED", "WITHDRAWN"})
    void findForAuthentication_nonActiveMember_returnsLoginBlockedView(MemberStatus status) {
        Member member = Member.builder()
                .id(1L)
                .email("blocked@cakeshop.local")
                .password("encoded-password")
                .role("USER")
                .status(status)
                .build();
        when(memberMapper.findByEmail(member.getEmail()))
                .thenReturn(Optional.of(member));

        Optional<MemberAuthenticationView> result =
                memberAuthenticationService.findForAuthentication(member.getEmail());

        assertThat(result).get()
                .extracting(MemberAuthenticationView::loginAllowed)
                .isEqualTo(false);
    }

    @Test
    void findForAuthentication_statusMissing_returnsLoginBlockedView() {
        Member member = Member.builder()
                .id(1L)
                .email("invalid-status@cakeshop.local")
                .password("encoded-password")
                .role("USER")
                .status(null)
                .build();
        when(memberMapper.findByEmail(member.getEmail()))
                .thenReturn(Optional.of(member));

        Optional<MemberAuthenticationView> result =
                memberAuthenticationService.findForAuthentication(member.getEmail());

        assertThat(result).get()
                .extracting(MemberAuthenticationView::loginAllowed)
                .isEqualTo(false);
    }
}
