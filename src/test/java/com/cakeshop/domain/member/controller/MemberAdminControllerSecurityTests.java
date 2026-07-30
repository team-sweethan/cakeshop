package com.cakeshop.domain.member.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.List;

import com.cakeshop.domain.member.dto.view.MemberAdminListView;
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
}
