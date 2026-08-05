package com.cakeshop.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.cakeshop.domain.member.dto.view.MemberSummaryView;
import com.cakeshop.domain.member.entity.MemberStatus;
import com.cakeshop.domain.member.error.MemberErrorCode;
import com.cakeshop.domain.member.mapper.MemberMapper;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;
import com.cakeshop.global.error.BusinessException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MemberQueryServiceTests {

    @Mock
    private MemberMapper memberMapper;

    @InjectMocks
    private MemberQueryService memberQueryService;

    @ParameterizedTest
    @EnumSource(MemberStatus.class)
    void findByMemberId_existingMember_returnsSummaryRegardlessOfStatus(MemberStatus status) {
        MemberSummaryView summary = new MemberSummaryView(1L, "회원", "USER", status);
        when(memberMapper.findSummaryByMemberId(1L)).thenReturn(Optional.of(summary));

        MemberSummaryView result = memberQueryService.findByMemberId(1L);

        assertThat(result).isEqualTo(summary);
    }

    @Test
    void findByMemberId_missingMember_throwsMemberNotFound() {
        when(memberMapper.findSummaryByMemberId(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> memberQueryService.findByMemberId(999L))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(MemberErrorCode.NOT_FOUND);
    }

    @Test
    void findAll_existingMembers_returnsPagedSummaries() {
        List<MemberSummaryView> summaries = List.of(
                new MemberSummaryView(1L, "회원", "USER", MemberStatus.ACTIVE),
                new MemberSummaryView(2L, "관리자", "ADMIN", MemberStatus.ACTIVE));
        PageRequest pageRequest = new PageRequest(2, 2);
        when(memberMapper.countAllMembers()).thenReturn(4L);
        when(memberMapper.findAllSummaries(2, 2)).thenReturn(summaries);

        PageResult<MemberSummaryView> result = memberQueryService.findAll(pageRequest);

        assertThat(result.getContent()).isEqualTo(summaries);
        assertThat(result.getPage()).isEqualTo(2);
        assertThat(result.getSize()).isEqualTo(2);
        assertThat(result.getTotalElements()).isEqualTo(4);
        assertThat(result.getTotalPages()).isEqualTo(2);
    }

    @Test
    void findAll_noMembers_returnsEmptyPageWithoutListQuery() {
        when(memberMapper.countAllMembers()).thenReturn(0L);

        PageResult<MemberSummaryView> result = memberQueryService.findAll(null);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getPage()).isEqualTo(1);
        assertThat(result.getSize()).isEqualTo(PageRequest.DEFAULT_SIZE);
        assertThat(result.getTotalElements()).isZero();
    }
}
