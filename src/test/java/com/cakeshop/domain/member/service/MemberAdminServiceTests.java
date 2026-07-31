package com.cakeshop.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.cakeshop.domain.member.dto.form.MemberAdminListType;
import com.cakeshop.domain.member.dto.form.MemberAdminSearchCondition;
import com.cakeshop.domain.member.dto.view.MemberAdminDetailRow;
import com.cakeshop.domain.member.dto.view.MemberAdminDetailView;
import com.cakeshop.domain.member.dto.view.MemberAdminListRow;
import com.cakeshop.domain.member.dto.view.MemberAdminListView;
import com.cakeshop.domain.member.entity.MemberStatus;
import com.cakeshop.domain.member.error.MemberErrorCode;
import com.cakeshop.domain.member.mapper.MemberMapper;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;
import com.cakeshop.global.error.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MemberAdminServiceTests {

    @Mock
    private MemberMapper memberMapper;

    @InjectMocks
    private MemberAdminService memberAdminService;

    @Test
    void getMembers_blankKeywordAndNoResults_returnsEmptyPage() {
        MemberAdminSearchCondition condition = new MemberAdminSearchCondition();

        condition.setKeyword("   ");
        when(memberMapper.countAdminMembers(condition)).thenReturn(0L);

        PageResult<MemberAdminListView> result =
                memberAdminService.getMembers(condition, null);

        assertThat(condition.getKeyword()).isNull();
        assertThat(result.getContent()).isEmpty();
        assertThat(result.getPage()).isEqualTo(1);
        assertThat(result.getSize()).isEqualTo(PageRequest.DEFAULT_SIZE);
        verify(memberMapper, never())
                .findAdminMembers(any(), anyInt(), anyInt());
    }

    @Test
    void getMembers_existingMember_masksPersonalInformation() {
        MemberAdminSearchCondition condition = new MemberAdminSearchCondition();
        PageRequest pageRequest = new PageRequest(2, 10);
        MemberAdminListRow row = new MemberAdminListRow(
                1L,
                "홍길동",
                "sumin@example.com",
                "010-1234-5678",
                LocalDate.of(2000, 1, 15),
                MemberStatus.ACTIVE,
                LocalDateTime.of(2026, 7, 30, 10, 0),
                null);

        when(memberMapper.countAdminMembers(condition)).thenReturn(11L);
        when(memberMapper.findAdminMembers(condition, 10, 10))
                .thenReturn(List.of(row));

        PageResult<MemberAdminListView> result =
                memberAdminService.getMembers(condition, pageRequest);

        assertThat(result.getContent())
                .singleElement()
                .satisfies(member -> {
                    assertThat(member.maskedEmail())
                            .isEqualTo("su***@example.com");
                    assertThat(member.maskedPhone())
                            .isEqualTo("010-****-5678");
                    assertThat(member.maskedBirthDate())
                            .isEqualTo("2000.**.**");
                });
        assertThat(result.getPage()).isEqualTo(2);
        assertThat(result.getTotalPages()).isEqualTo(2);
    }

    @Test
    void getMembers_nonMemberTab_clearsStatusFilter() {
        MemberAdminSearchCondition condition = new MemberAdminSearchCondition();

        condition.setListType(MemberAdminListType.WITHDRAWN);
        condition.setStatus(MemberStatus.ACTIVE);
        when(memberMapper.countAdminMembers(condition)).thenReturn(0L);

        memberAdminService.getMembers(condition, new PageRequest(1, 10));

        assertThat(condition.getStatus()).isNull();
    }

    @Test
    void getMembers_shortEmail_masksAtLeastOneCharacter() {
        MemberAdminSearchCondition condition = new MemberAdminSearchCondition();
        MemberAdminListRow oneCharacterEmail = new MemberAdminListRow(
                1L,
                "한글자",
                "a@example.com",
                null,
                null,
                MemberStatus.ACTIVE,
                LocalDateTime.now(),
                null);
        MemberAdminListRow twoCharacterEmail = new MemberAdminListRow(
                2L,
                "두글자",
                "ab@example.com",
                null,
                null,
                MemberStatus.ACTIVE,
                LocalDateTime.now(),
                null);

        when(memberMapper.countAdminMembers(condition)).thenReturn(2L);
        when(memberMapper.findAdminMembers(condition, 10, 0))
                .thenReturn(List.of(oneCharacterEmail, twoCharacterEmail));

        PageResult<MemberAdminListView> result =
                memberAdminService.getMembers(
                        condition,
                        new PageRequest(1, 10));

        assertThat(result.getContent())
                .extracting(MemberAdminListView::maskedEmail)
                .containsExactly(
                        "***@example.com",
                        "a***@example.com");
    }

    @Test
    void getMembers_withdrawnStatusOnMemberTab_clearsStatusFilter() {
        MemberAdminSearchCondition condition = new MemberAdminSearchCondition();

        condition.setStatus(MemberStatus.WITHDRAWN);
        when(memberMapper.countAdminMembers(condition)).thenReturn(0L);

        memberAdminService.getMembers(condition, new PageRequest(1, 10));

        assertThat(condition.getStatus()).isNull();
    }

    @Test
    void getMemberDetail_existingMember_returnsDetail() {
        MemberAdminDetailRow row = new MemberAdminDetailRow(
                1L,
                "관리자 조회 회원",
                "member",
                "member@example.com",
                "010-1234-5678",
                LocalDate.of(2000, 1, 1),
                "USER",
                MemberStatus.ACTIVE,
                LocalDateTime.of(2026, 7, 31, 10, 0),
                LocalDateTime.of(2026, 7, 31, 10, 0),
                null,
                null,
                null);
        when(memberMapper.findAdminMemberDetail(1L))
                .thenReturn(Optional.of(row));

        MemberAdminDetailView result =
                memberAdminService.getMemberDetail(1L);

        assertThat(result.maskedEmail()).isEqualTo("me***@example.com");
        assertThat(result.maskedPhone()).isEqualTo("010-****-5678");
        assertThat(result.maskedBirthDate()).isEqualTo("2000.**.**");
    }

    @Test
    void getMemberDetail_nonexistentMember_throwsNotFound() {
        when(memberMapper.findAdminMemberDetail(999L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> memberAdminService.getMemberDetail(999L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(MemberErrorCode.NOT_FOUND));
    }
}
