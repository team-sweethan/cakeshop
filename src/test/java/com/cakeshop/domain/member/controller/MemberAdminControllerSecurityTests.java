package com.cakeshop.domain.member.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.cakeshop.domain.member.dto.view.MemberAdminDetailView;
import com.cakeshop.domain.member.dto.view.MemberAdminListView;
import com.cakeshop.domain.member.entity.MemberStatus;
import com.cakeshop.domain.member.service.MemberAdminService;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;
import com.cakeshop.global.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MemberAdminController.class)
@Import(SecurityConfig.class)
class MemberAdminControllerSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MemberAdminService memberAdminService;

    @Test
    @WithAnonymousUser
    void members_anonymousUser_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/admin/members"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void members_customerRole_returnsForbidden() throws Exception {
        mockMvc.perform(get("/admin/members"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void members_adminRole_rendersMemberList() throws Exception {
        PageResult<MemberAdminListView> pageResult =
                new PageResult<>(
                        List.of(),
                        new PageRequest(1, 10),
                        0);

        when(memberAdminService.getMembers(any(), any()))
                .thenReturn(pageResult);

        mockMvc.perform(get("/admin/members"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/member/list"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void members_statusFilter_searchFormKeepsCurrentStatus() throws Exception {
        PageResult<MemberAdminListView> pageResult =
                new PageResult<>(
                        List.of(),
                        new PageRequest(1, 10),
                        0);

        when(memberAdminService.getMembers(any(), any()))
                .thenReturn(pageResult);

        mockMvc.perform(get("/admin/members")
                        .param("status", "SUSPENDED"))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        containsString("name=\"status\"")))
                .andExpect(content().string(
                        containsString("value=\"SUSPENDED\"")));
    }

    @Test
    @WithMockUser(roles = "USER")
    void memberDetail_customerRole_returnsForbidden() throws Exception {
        mockMvc.perform(get("/admin/members/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void memberDetail_adminRole_rendersDetail() throws Exception {
        LocalDateTime registeredAt =
                LocalDateTime.of(2026, 7, 31, 10, 0);
        MemberAdminDetailView member = new MemberAdminDetailView(
                1L,
                "관리자 조회 회원",
                "member",
                "member@example.com",
                "010-1234-5678",
                LocalDate.of(2000, 1, 1),
                "USER",
                MemberStatus.ACTIVE,
                registeredAt,
                registeredAt,
                null,
                null,
                null);

        when(memberAdminService.getMemberDetail(1L))
                .thenReturn(member);

        mockMvc.perform(get("/admin/members/1"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/member/detail"))
                .andExpect(content().string(
                        containsString("member@example.com")));
    }
}
