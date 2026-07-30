package com.cakeshop.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

import com.cakeshop.domain.member.dto.form.ProfileUpdateForm;
import com.cakeshop.domain.member.dto.form.SignupForm;
import com.cakeshop.domain.member.dto.view.EmailRecoveryResult;
import com.cakeshop.domain.member.dto.view.MemberProfileView;
import com.cakeshop.domain.member.dto.view.RecoveredEmailView;
import com.cakeshop.domain.member.entity.Member;
import com.cakeshop.domain.member.entity.MemberStatus;
import com.cakeshop.domain.member.error.MemberErrorCode;
import com.cakeshop.domain.member.mapper.MemberMapper;
import com.cakeshop.global.error.BusinessException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class MemberServiceTests {

    @Mock
    private MemberMapper memberMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private MemberService memberService;

    @Test
    void join_duplicateEmail_throwsMemberBusinessException() {
        SignupForm form = new SignupForm();
        form.setEmail("member@cakeshop.local");
        when(memberMapper.findByEmail(form.getEmail()))
                .thenReturn(Optional.of(Member.builder().id(1L).build()));

        assertThatThrownBy(() -> memberService.join(form))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(MemberErrorCode.DUPLICATE_EMAIL);

        verify(memberMapper, never()).join(org.mockito.ArgumentMatchers.any(Member.class));
    }

    @Test
    void join_validForm_mapsAndPersistsBirthDate() {
        SignupForm form = new SignupForm();
        form.setEmail("member@cakeshop.local");
        form.setPassword("Password1!");
        form.setName("홍길동");
        form.setNickname("케이크러버");
        form.setPhone("010-1234-5678");
        form.setBirthDate(LocalDate.of(2000, 1, 15));
        when(memberMapper.findByEmail(form.getEmail())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(form.getPassword())).thenReturn("encoded-password");

        memberService.join(form);

        ArgumentCaptor<Member> captor = ArgumentCaptor.forClass(Member.class);
        verify(memberMapper).join(captor.capture());
        assertThat(captor.getValue().getBirthDate()).isEqualTo(form.getBirthDate());
    }

    @Test
    void updateMemberInfo_passwordBlank_updatesBasicInfoWithoutPassword() {
        Member member = Member.builder()
                .id(1L)
                .email("member@cakeshop.local")
                .password("encoded-password")
                .name("기존 이름")
                .phone("010-0000-0000")
                .birthDate(LocalDate.of(2000, 1, 15))
                .build();
        ProfileUpdateForm form = new ProfileUpdateForm();
        form.setName("새 이름");
        form.setNickname("새닉네임");
        form.setPhone("010-1234-5678");
        when(memberMapper.findByEmail(member.getEmail())).thenReturn(Optional.of(member));
        when(memberMapper.update(any(Member.class))).thenReturn(1);

        memberService.updateMemberInfo(member.getEmail(), form);

        verify(passwordEncoder, never()).matches(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
        verify(passwordEncoder, never()).encode(org.mockito.ArgumentMatchers.anyString());
        verify(memberMapper).update(member);
        org.assertj.core.api.Assertions.assertThat(member.getName()).isEqualTo("새 이름");
        org.assertj.core.api.Assertions.assertThat(member.getNickname()).isEqualTo("새닉네임");
        org.assertj.core.api.Assertions.assertThat(member.getPhone()).isEqualTo("010-1234-5678");
        org.assertj.core.api.Assertions.assertThat(member.getBirthDate())
                .isEqualTo(LocalDate.of(2000, 1, 15));
        org.assertj.core.api.Assertions.assertThat(member.getPassword()).isNull();
    }

    @Test
    void updateMemberInfo_currentPasswordMismatch_throwsMemberBusinessException() {
        Member member = Member.builder()
                .id(1L)
                .email("member@cakeshop.local")
                .password("encoded-current-password")
                .build();
        ProfileUpdateForm form = passwordChangeForm();
        when(memberMapper.findByEmail(member.getEmail()))
                .thenReturn(Optional.of(member));
        when(passwordEncoder.matches(
                form.getCurrentPassword(),
                member.getPassword()))
                .thenReturn(false);

        assertThatThrownBy(() ->
                memberService.updateMemberInfo(member.getEmail(), form))
                .isInstanceOf(BusinessException.class)
                .extracting(exception ->
                        ((BusinessException) exception).getErrorCode())
                .isEqualTo(MemberErrorCode.INVALID_CURRENT_PASSWORD);

        verify(passwordEncoder, never()).encode(
                org.mockito.ArgumentMatchers.anyString());
        verify(memberMapper, never()).update(
                org.mockito.ArgumentMatchers.any(Member.class));
    }

    @Test
    void updateMemberInfo_validPasswordChange_encodesAndUpdatesPassword() {
        Member member = Member.builder()
                .id(1L)
                .email("member@cakeshop.local")
                .password("encoded-current-password")
                .build();
        ProfileUpdateForm form = passwordChangeForm();
        when(memberMapper.findByEmail(member.getEmail()))
                .thenReturn(Optional.of(member));
        when(passwordEncoder.matches(
                form.getCurrentPassword(),
                member.getPassword()))
                .thenReturn(true);
        when(passwordEncoder.encode(form.getNewPassword()))
                .thenReturn("encoded-new-password");
        when(memberMapper.update(member)).thenReturn(1);

        memberService.updateMemberInfo(member.getEmail(), form);

        assertThat(member.getPassword()).isEqualTo("encoded-new-password");
        assertThat(member.getName()).isEqualTo(form.getName());
        assertThat(member.getNickname()).isEqualTo(form.getNickname());
        assertThat(member.getPhone()).isEqualTo(form.getPhone());
        verify(memberMapper).update(member);
    }

    @Test
    void updateMemberInfo_updateAffectsNoRows_throwsMemberBusinessException() {
        Member member = Member.builder()
                .id(1L)
                .email("member@cakeshop.local")
                .password("encoded-password")
                .build();
        ProfileUpdateForm form = new ProfileUpdateForm();
        form.setName("홍길동");
        form.setNickname("케이크러버");
        form.setPhone("010-1234-5678");
        when(memberMapper.findByEmail(member.getEmail()))
                .thenReturn(Optional.of(member));
        when(memberMapper.update(member)).thenReturn(0);

        assertThatThrownBy(() ->
                memberService.updateMemberInfo(member.getEmail(), form))
                .isInstanceOf(BusinessException.class)
                .extracting(exception ->
                        ((BusinessException) exception).getErrorCode())
                .isEqualTo(MemberErrorCode.UPDATE_FAILED);

        verify(memberMapper).update(member);
    }

    @Test
    void getMemberProfile_existingMember_returnsProfileWithNickname() {
        Member member = Member.builder()
                .email("member@cakeshop.local")
                .name("홍길동")
                .nickname("케이크러버")
                .phone("010-1234-5678")
                .birthDate(LocalDate.of(2000, 1, 15))
                .build();
        when(memberMapper.findByEmail(member.getEmail()))
                .thenReturn(Optional.of(member));

        MemberProfileView profile =
                memberService.getMemberProfile(member.getEmail());

        assertThat(profile).isEqualTo(new MemberProfileView(
                member.getEmail(),
                member.getName(),
                member.getNickname(),
                member.getPhone(),
                member.getBirthDate()));
    }

    @Test
    void findEmails_matchingMembers_normalizesInputAndMasksAllEmails() {
        LocalDate birthDate = LocalDate.of(2000, 1, 15);
        when(memberMapper.findEmailsByMemberInfo(
                "홍길동",
                birthDate,
                "01012345678"))
                .thenReturn(List.of(
                        "member@example.com",
                        "ab@example.com",
                        "x@example.com"));

        EmailRecoveryResult result = memberService.findEmails(
                " 홍길동 ",
                birthDate,
                "010-1234-5678");

        assertThat(result.emails()).containsExactly(
                "member@example.com",
                "ab@example.com",
                "x@example.com");
        assertThat(result.views()).containsExactly(
                new RecoveredEmailView(0, "me****@example.com"),
                new RecoveredEmailView(1, "a*@example.com"),
                new RecoveredEmailView(2, "*@example.com"));
    }

    @Test
    void findEmails_noMatchingMember_returnsEmptyList() {
        LocalDate birthDate = LocalDate.of(2000, 1, 15);
        when(memberMapper.findEmailsByMemberInfo(
                "홍길동",
                birthDate,
                "01012345678"))
                .thenReturn(List.of());

        assertThat(memberService.findEmails(
                "홍길동",
                birthDate,
                "01012345678").emails()).isEmpty();
    }

    @Test
    void withdraw_activeMember_withdrawsByPersistentId() {
        Member member = Member.builder()
                .id(7L)
                .email("member@cakeshop.local")
                .status(MemberStatus.ACTIVE)
                .build();
        when(memberMapper.findByEmail(member.getEmail())).thenReturn(Optional.of(member));
        when(memberMapper.withdrawById(member.getId(), MemberStatus.WITHDRAWN)).thenReturn(1);

        memberService.withdraw(member.getEmail());

        verify(memberMapper).withdrawById(member.getId(), MemberStatus.WITHDRAWN);
    }

    @Test
    void withdraw_withdrawnMember_throwsInvalidStatusTransition() {
        Member member = Member.builder()
                .id(7L)
                .email("member@cakeshop.local")
                .status(MemberStatus.WITHDRAWN)
                .build();
        when(memberMapper.findByEmail(member.getEmail())).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> memberService.withdraw(member.getEmail()))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(MemberErrorCode.INVALID_STATUS_TRANSITION);

        verify(memberMapper, never()).withdrawById(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(MemberStatus.class));
    }

    private ProfileUpdateForm passwordChangeForm() {
        ProfileUpdateForm form = new ProfileUpdateForm();
        form.setName("홍길동");
        form.setNickname("케이크러버");
        form.setPhone("010-1234-5678");
        form.setCurrentPassword("Current1!");
        form.setNewPassword("NewPassword1!");
        form.setNewPasswordConfirm("NewPassword1!");
        return form;
    }
}
