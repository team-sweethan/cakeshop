package com.cakeshop.domain.community.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.LocalDateTime;
import java.util.List;

import com.cakeshop.domain.community.dto.form.NoticeForm;
import com.cakeshop.domain.community.dto.view.AdminNoticeDetailView;
import com.cakeshop.domain.community.dto.view.AdminNoticeListView;
import com.cakeshop.domain.community.dto.view.NoticeDisplayStatus;
import com.cakeshop.domain.community.service.CommunityNoticeAdminService;
import com.cakeshop.domain.member.dto.view.MemberAuthenticationView;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;
import com.cakeshop.global.security.MemberDetails;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 관리자 공지 화면의 폼 검증과 이동 경로를 검증한다. */
class CommunityNoticeAdminControllerTests {

    private static final long NOTICE_ID = 42L;
    private static final long ADMIN_ID = 1L;

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 3, 1, 10, 0);

    private CommunityNoticeAdminService communityNoticeAdminService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        communityNoticeAdminService = mock(CommunityNoticeAdminService.class);

        when(communityNoticeAdminService.getNotices(any(PageRequest.class)))
                .thenReturn(new PageResult<AdminNoticeListView>(
                        List.of(), new PageRequest(1, PageRequest.DEFAULT_SIZE), 0));
        when(communityNoticeAdminService.getEditableNotice(anyLong()))
                .thenReturn(publishedNotice());

        mockMvc = MockMvcBuilders
                .standaloneSetup(new CommunityNoticeAdminController(communityNoticeAdminService))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        authenticateAsAdmin();
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void list_rendersNoticeListView() throws Exception {
        mockMvc.perform(get("/admin/community/notices"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/community/notice/list"))
                .andExpect(model().attributeExists("pageResult", "pageNavigation"));
    }

    @Test
    void create_validForm_savesAndRedirectsToList() throws Exception {
        mockMvc.perform(post("/admin/community/notices/new")
                        .param("title", "공지 제목")
                        .param("content", "공지 본문")
                        .param("startsAt", "2026-03-02T09:00")
                        .param("endsAt", "2026-03-09T09:00"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/community/notices"));

        ArgumentCaptor<NoticeForm> captor = ArgumentCaptor.forClass(NoticeForm.class);
        verify(communityNoticeAdminService).createNotice(captor.capture(), anyLong());

        assertThat(captor.getValue().getStartsAt())
                .isEqualTo(LocalDateTime.of(2026, 3, 2, 9, 0));
        assertThat(captor.getValue().getEndsAt())
                .isEqualTo(LocalDateTime.of(2026, 3, 9, 9, 0));
    }

    @Test
    void create_emptyPeriod_isAccepted() throws Exception {
        mockMvc.perform(post("/admin/community/notices/new")
                        .param("title", "공지 제목")
                        .param("content", "공지 본문")
                        .param("startsAt", "")
                        .param("endsAt", ""))
                .andExpect(status().is3xxRedirection());

        ArgumentCaptor<NoticeForm> captor = ArgumentCaptor.forClass(NoticeForm.class);
        verify(communityNoticeAdminService).createNotice(captor.capture(), anyLong());

        assertThat(captor.getValue().getStartsAt()).isNull();
        assertThat(captor.getValue().getEndsAt()).isNull();
    }

    @Test
    void create_invertedPeriod_staysOnFormAndDoesNotSave() throws Exception {
        mockMvc.perform(post("/admin/community/notices/new")
                        .param("title", "공지 제목")
                        .param("content", "공지 본문")
                        .param("startsAt", "2026-03-09T09:00")
                        .param("endsAt", "2026-03-02T09:00"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/community/notice/form"))
                .andExpect(model().attributeHasFieldErrors("noticeForm", "validPeriod"));

        verify(communityNoticeAdminService, never()).createNotice(any(NoticeForm.class), anyLong());
    }

    @Test
    void create_blankTitle_staysOnFormAndDoesNotSave() throws Exception {
        mockMvc.perform(post("/admin/community/notices/new")
                        .param("title", "   ")
                        .param("content", "공지 본문"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/community/notice/form"))
                .andExpect(model().attributeHasFieldErrors("noticeForm", "title"));

        verify(communityNoticeAdminService, never()).createNotice(any(NoticeForm.class), anyLong());
    }

    @Test
    void editForm_fillsFormWithStoredValues() throws Exception {
        mockMvc.perform(get("/admin/community/notices/" + NOTICE_ID + "/edit"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/community/notice/form"))
                .andExpect(model().attribute("notice", publishedNotice()))
                .andExpect(model().attributeExists("noticeForm"));

        verify(communityNoticeAdminService).getEditableNotice(NOTICE_ID);
    }

    @Test
    void update_validForm_savesAndRedirectsToList() throws Exception {
        mockMvc.perform(post("/admin/community/notices/" + NOTICE_ID + "/edit")
                        .param("title", "고친 제목")
                        .param("content", "고친 본문"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/community/notices"));

        verify(communityNoticeAdminService).updateNotice(anyLong(), any(NoticeForm.class));
    }

    @Test
    void delete_redirectsToList() throws Exception {
        mockMvc.perform(post("/admin/community/notices/" + NOTICE_ID + "/delete"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/community/notices"));

        verify(communityNoticeAdminService).deleteNotice(NOTICE_ID);
    }

    private void authenticateAsAdmin() {
        MemberDetails principal = new MemberDetails(new MemberAuthenticationView(
                ADMIN_ID, "admin@cakeshop.local", "dummy", "ADMIN", true));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities()));
    }

    private AdminNoticeDetailView publishedNotice() {
        return new AdminNoticeDetailView(
                NOTICE_ID, "저장된 제목", "저장된 본문", NoticeDisplayStatus.VISIBLE,
                null, null, CREATED_AT, CREATED_AT);
    }
}
