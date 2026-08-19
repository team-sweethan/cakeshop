package com.cakeshop.domain.home.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import com.cakeshop.domain.community.dto.view.NoticeSectionView;
import com.cakeshop.domain.community.dto.view.NoticeView;
import com.cakeshop.domain.community.service.CommunityHomeQueryService;
import com.cakeshop.global.config.MariaDbIntegrationTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * 메인 화면을 실제 Thymeleaf로 렌더링한다.
 *
 * <p>공지 영역이 <b>없을 때 통째로 빠지는지</b>가 이 클래스의 요점이다. 날짜와 제목만 남고 안이
 * 빈 칸은 사용자에게 고장으로 보이지만 서버에는 오류가 없어 로그에도 안 남는다
 * (docs/community/specs/community-notice.md H45).
 *
 * <p>영역의 존재는 낱말이 아니라 {@code id}로 본다. 템플릿의 내부 설명은 Thymeleaf parser-level
 * 주석으로 두어 응답에 저장소 경로가 노출되지 않게 한다.
 */
@SpringBootTest
@MariaDbIntegrationTest
class HomeScreenRenderingTests {

    private static final String NOTICE_SECTION = "id=\"notices\"";
    private static final String INTERNAL_NOTICE_SPEC_PATH =
            "docs/community/specs/community-notice.md";

    private static final LocalDateTime NOTICE_DATE = LocalDateTime.of(2026, 3, 9, 10, 0);

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private CommunityHomeQueryService communityHomeQueryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        when(communityHomeQueryService.getNoticeSection())
                .thenReturn(NoticeSectionView.empty());
    }

    /** 메인에 인기글 영역이 없다 — 조각 15에서 뺐다. 되살아나면 이 검사가 먼저 문다(H52). */
    @Test
    void home_hasNoPopularSection() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("id=\"popular-posts\""))));
    }

    @Test
    void home_storeImage_usesDedicatedResponsiveMediaArea() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/css/home.css\"")))
                .andExpect(content().string(containsString("class=\"store-media\"")));
    }

    @Test
    void home_visibleNotice_showsNoticeSectionWithFullListLink() throws Exception {
        when(communityHomeQueryService.getNoticeSection()).thenReturn(
                new NoticeSectionView(List.of(
                        new NoticeView(7L, "메인에 실린 공지", NOTICE_DATE))));

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(NOTICE_SECTION)))
                .andExpect(content().string(containsString("메인에 실린 공지")))
                .andExpect(content().string(containsString("2026.03.09")))
                .andExpect(content().string(containsString("/community/notices/7")))
                .andExpect(content().string(containsString("/community/notices\"")))
                .andExpect(content().string(not(containsString(INTERNAL_NOTICE_SPEC_PATH))));
    }

    @Test
    void home_noVisibleNotice_dropsNoticeSectionEntirely() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(NOTICE_SECTION))));
    }

    /** 공지는 서비스 안내와 카테고리 사이에 둔다. */
    @Test
    void home_noticeSection_comesBetweenServiceAndCategory() throws Exception {
        when(communityHomeQueryService.getNoticeSection()).thenReturn(
                new NoticeSectionView(List.of(
                        new NoticeView(7L, "메인에 실린 공지", NOTICE_DATE))));

        String html = mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        int serviceIndex = html.indexOf("id=\"service-title\"");
        int noticeIndex = html.indexOf(NOTICE_SECTION);
        int categoryIndex = html.indexOf("id=\"category-title\"");

        // indexOf는 없는 영역에 -1을 주므로, 순서를 보기 전에 세 영역이 실제로 있는지 먼저 못박는다.
        assertThat(serviceIndex).isNotNegative();
        assertThat(noticeIndex).isNotNegative();
        assertThat(categoryIndex).isNotNegative();
        assertThat(serviceIndex).isLessThan(noticeIndex);
        assertThat(noticeIndex).isLessThan(categoryIndex);
    }
}
