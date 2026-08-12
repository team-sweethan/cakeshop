package com.cakeshop.domain.community.controller;

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

import com.cakeshop.domain.community.dto.view.NoticeDetailView;
import com.cakeshop.domain.community.dto.view.NoticeView;
import com.cakeshop.domain.community.service.CommunityNoticeService;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 고객 공지 화면의 모델 계약과 파라미터 처리를 검증한다. */
class CommunityNoticeControllerTests {

    private static final long NOTICE_ID = 42L;

    private static final LocalDateTime DISPLAYED_AT = LocalDateTime.of(2026, 3, 1, 10, 0);

    private CommunityNoticeService communityNoticeService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        communityNoticeService = mock(CommunityNoticeService.class);

        when(communityNoticeService.getNotices(any(PageRequest.class)))
                .thenReturn(new PageResult<>(
                        List.of(new NoticeView(NOTICE_ID, "공지 제목", DISPLAYED_AT)),
                        new PageRequest(1, PageRequest.DEFAULT_SIZE),
                        1));
        when(communityNoticeService.getNotice(NOTICE_ID)).thenReturn(
                new NoticeDetailView(NOTICE_ID, "공지 제목", "공지 본문", DISPLAYED_AT));

        mockMvc = MockMvcBuilders
                .standaloneSetup(new CommunityNoticeController(communityNoticeService))
                .build();
    }

    @Test
    void list_bindsPageResultAndNavigation() throws Exception {
        mockMvc.perform(get("/community/notices"))
                .andExpect(status().isOk())
                .andExpect(view().name("customer/community/notice/list"))
                .andExpect(model().attributeExists("pageResult", "pageNavigation"));
    }

    @Test
    void list_usesFixedPageSize() throws Exception {
        mockMvc.perform(get("/community/notices").param("page", "3"))
                .andExpect(status().isOk());

        ArgumentCaptor<PageRequest> captor = ArgumentCaptor.forClass(PageRequest.class);
        verify(communityNoticeService).getNotices(captor.capture());

        assertThat(captor.getValue().getPage()).isEqualTo(3);
        assertThat(captor.getValue().getSize()).isEqualTo(PageRequest.DEFAULT_SIZE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc", ""})
    void list_invalidPage_fallsBackToFirstPage(String page) throws Exception {
        mockMvc.perform(get("/community/notices").param("page", page))
                .andExpect(status().isOk());

        ArgumentCaptor<PageRequest> captor = ArgumentCaptor.forClass(PageRequest.class);
        verify(communityNoticeService).getNotices(captor.capture());

        assertThat(captor.getValue().getPage()).isEqualTo(1);
    }

    @Test
    void detail_bindsNotice() throws Exception {
        mockMvc.perform(get("/community/notices/" + NOTICE_ID))
                .andExpect(status().isOk())
                .andExpect(view().name("customer/community/notice/detail"))
                .andExpect(model().attributeExists("notice"));
    }
}
