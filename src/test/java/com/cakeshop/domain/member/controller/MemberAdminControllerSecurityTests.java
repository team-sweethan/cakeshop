package com.cakeshop.domain.member.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.LocalDateTime;
import java.util.List;

import com.cakeshop.domain.member.dto.view.MemberAdminDetailView;
import com.cakeshop.domain.member.dto.view.MemberAuthenticationView;
import com.cakeshop.domain.member.dto.view.MemberAdminListView;
import com.cakeshop.domain.member.entity.MemberStatus;
import com.cakeshop.domain.member.service.MemberAdminService;
import com.cakeshop.domain.member.service.MemberSessionService;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;
import com.cakeshop.global.security.SecurityConfig;
import com.cakeshop.global.security.MemberDetails;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

@WebMvcTest(MemberAdminController.class)
@Import(SecurityConfig.class)
class MemberAdminControllerSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MemberAdminService memberAdminService;

    @MockitoBean
    private MemberSessionService memberSessionService;

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
                "me***@example.com",
                "010-****-5678",
                "2000.**.**",
                "USER",
                MemberStatus.ACTIVE,
                registeredAt,
                registeredAt,
                null,
                null,
                null,
                List.of());

        when(memberAdminService.getMemberDetail(1L))
                .thenReturn(member);

        mockMvc.perform(get("/admin/members/1"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/member/detail"))
                .andExpect(content().string(
                        containsString("me***@example.com")))
                .andExpect(content().string(
                        not(containsString("member@example.com"))))
                .andExpect(content().string(
                        containsString(
                                "/admin/members/1/suspend")))
                .andExpect(content().string(
                        not(containsString(
                                "/admin/members/1/activate"))));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void memberDetail_suspendedMember_rendersOnlyActivateAction()
            throws Exception {
        LocalDateTime registeredAt =
                LocalDateTime.of(2026, 7, 31, 10, 0);
        MemberAdminDetailView member = new MemberAdminDetailView(
                2L,
                "정지 회원",
                "suspended",
                "su***@example.com",
                "010-****-5678",
                "2000.**.**",
                "USER",
                MemberStatus.SUSPENDED,
                registeredAt,
                registeredAt,
                registeredAt,
                "정지 사유",
                null,
                List.of());

        when(memberAdminService.getMemberDetail(2L))
                .thenReturn(member);

        mockMvc.perform(get("/admin/members/2"))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        containsString(
                                "/admin/members/2/activate")))
                .andExpect(content().string(
                        not(containsString(
                                "/admin/members/2/suspend"))));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void members_memberTab_rendersStatusActions() throws Exception {
        LocalDateTime registeredAt =
                LocalDateTime.of(2026, 7, 31, 10, 0);
        MemberAdminListView activeMember = new MemberAdminListView(
                1L,
                "정상 회원",
                "ac***@example.com",
                "010-****-1111",
                "2000.**.**",
                MemberStatus.ACTIVE,
                registeredAt,
                null);
        MemberAdminListView suspendedMember = new MemberAdminListView(
                2L,
                "정지 회원",
                "su***@example.com",
                "010-****-2222",
                "2000.**.**",
                MemberStatus.SUSPENDED,
                registeredAt,
                null);
        PageResult<MemberAdminListView> pageResult =
                new PageResult<>(
                        List.of(activeMember, suspendedMember),
                        new PageRequest(1, 10),
                        2);

        when(memberAdminService.getMembers(any(), any()))
                .thenReturn(pageResult);

        mockMvc.perform(get("/admin/members"))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        containsString(
                                "/admin/members/1/suspend")))
                .andExpect(content().string(
                        containsString(
                                "/admin/members/2/activate")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void suspendMember_withoutCsrf_returnsForbidden() throws Exception {
        mockMvc.perform(post("/admin/members/1/suspend")
                        .param("reason", "정지 사유"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    void suspendMember_customerRole_returnsForbidden() throws Exception {
        mockMvc.perform(post("/admin/members/1/suspend")
                        .with(csrf())
                        .param("reason", "정지 사유"))
                .andExpect(status().isForbidden());
    }

    @Test
    void suspendMember_adminRole_usesAuthenticatedAdminId() throws Exception {
        when(memberAdminService.suspendMember(1L, "정지 사유", 99L))
                .thenReturn("member@example.com");

        mockMvc.perform(post("/admin/members/1/suspend")
                        .with(csrf())
                        .with(authentication(adminAuthentication()))
                        .param("reason", "정지 사유"))
                .andExpect(status().is3xxRedirection());

        verify(memberAdminService)
                .suspendMember(1L, "정지 사유", 99L);
    }

    @Test
    @WithMockUser(roles = "USER")
    void activateMember_customerRole_returnsForbidden() throws Exception {
        mockMvc.perform(post("/admin/members/1/activate")
                        .with(csrf())
                        .param("reason", "해제 사유"))
                .andExpect(status().isForbidden());
    }

    private UsernamePasswordAuthenticationToken adminAuthentication() {
        MemberDetails details = new MemberDetails(
                new MemberAuthenticationView(
                        99L,
                        "admin@example.com",
                        "password",
                        "ADMIN",
                        true));

        return new UsernamePasswordAuthenticationToken(
                details,
                details.getPassword(),
                details.getAuthorities());
    }
}
