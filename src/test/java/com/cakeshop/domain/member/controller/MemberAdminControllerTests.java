package com.cakeshop.domain.member.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.LocalDateTime;
import java.util.List;

import com.cakeshop.domain.member.dto.form.MemberAdminListType;
import com.cakeshop.domain.member.dto.form.MemberAdminSearchCondition;
import com.cakeshop.domain.member.dto.view.MemberAdminDetailView;
import com.cakeshop.domain.member.dto.view.MemberAdminListView;
import com.cakeshop.domain.member.entity.MemberStatus;
import com.cakeshop.domain.member.service.MemberAdminService;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class MemberAdminControllerTests {

    @Test
    void members_searchAndPagingRequest_bindsConditions() throws Exception {
        MemberAdminService memberAdminService =
                mock(MemberAdminService.class);
        PageResult<MemberAdminListView> pageResult =
                new PageResult<>(
                        List.of(),
                        new PageRequest(2, 10),
                        12);

        when(memberAdminService.getMembers(any(), any()))
                .thenReturn(pageResult);

        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new MemberAdminController(memberAdminService))
                .build();

        mockMvc.perform(get("/admin/members")
                        .param("tab", "withdrawn")
                        .param("keyword", "홍길동")
                        .param("page", "2")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/member/list"))
                .andExpect(model().attribute("pageResult", pageResult))
                .andExpect(model().attributeExists(
                        "condition",
                        "memberStatuses"));

        ArgumentCaptor<MemberAdminSearchCondition> conditionCaptor =
                ArgumentCaptor.forClass(MemberAdminSearchCondition.class);
        ArgumentCaptor<PageRequest> pageCaptor =
                ArgumentCaptor.forClass(PageRequest.class);

        verify(memberAdminService).getMembers(
                conditionCaptor.capture(),
                pageCaptor.capture());

        assertThat(conditionCaptor.getValue().getListType())
                .isEqualTo(MemberAdminListType.WITHDRAWN);
        assertThat(conditionCaptor.getValue().getKeyword())
                .isEqualTo("홍길동");
        assertThat(pageCaptor.getValue().getPage()).isEqualTo(2);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(10);
    }

    @Test
    void members_invalidParameters_usesDefaults() throws Exception {
        MemberAdminService memberAdminService =
                mock(MemberAdminService.class);

        when(memberAdminService.getMembers(any(), any()))
                .thenAnswer(invocation -> new PageResult<>(
                        List.of(),
                        invocation.getArgument(1),
                        0));

        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new MemberAdminController(memberAdminService))
                .build();

        mockMvc.perform(get("/admin/members")
                        .param("tab", "unknown")
                        .param("status", "UNKNOWN")
                        .param("page", "invalid")
                        .param("size", "-1"))
                .andExpect(status().isOk());

        ArgumentCaptor<MemberAdminSearchCondition> conditionCaptor =
                ArgumentCaptor.forClass(MemberAdminSearchCondition.class);
        ArgumentCaptor<PageRequest> pageCaptor =
                ArgumentCaptor.forClass(PageRequest.class);

        verify(memberAdminService).getMembers(
                conditionCaptor.capture(),
                pageCaptor.capture());

        assertThat(conditionCaptor.getValue().getListType())
                .isEqualTo(MemberAdminListType.MEMBERS);
        assertThat(conditionCaptor.getValue().getStatus()).isNull();
        assertThat(pageCaptor.getValue().getPage()).isEqualTo(1);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(10);
    }

    @Test
    void memberDetail_existingMember_rendersDetail() throws Exception {
        MemberAdminService memberAdminService =
                mock(MemberAdminService.class);
        MemberAdminDetailView member = detail();

        when(memberAdminService.getMemberDetail(1L))
                .thenReturn(member);

        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new MemberAdminController(memberAdminService))
                .build();

        mockMvc.perform(get("/admin/members/1"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/member/detail"))
                .andExpect(model().attribute("member", member));

        verify(memberAdminService).getMemberDetail(1L);
    }

    private MemberAdminDetailView detail() {
        LocalDateTime registeredAt =
                LocalDateTime.of(2026, 7, 31, 10, 0);

        return new MemberAdminDetailView(
                1L,
                "관리자 조회 회원",
                "member",
                "me***@example.com",
                "010-****-5678",
                "2000.**.**",
                "USER",
                MemberStatus.ACTIVE,
                registeredAt,
                registeredAt,
                null,
                null,
                null);
    }
}
