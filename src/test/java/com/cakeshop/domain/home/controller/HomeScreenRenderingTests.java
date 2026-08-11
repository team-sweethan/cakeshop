package com.cakeshop.domain.home.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import com.cakeshop.domain.community.dto.view.PopularPostView;
import com.cakeshop.domain.community.dto.view.PopularSectionView;
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
 * <p>인기글 영역이 <b>없을 때 통째로 빠지는지</b>가 이 클래스의 요점이다. 날짜와 제목만 남고 안이
 * 빈 칸은 사용자에게 고장으로 보이지만 서버에는 오류가 없어 로그에도 안 남는다
 * (docs/community/specs/community-popular.md H27이 목록 화면에서 같은 자리를 문다).
 *
 * <p>영역의 존재는 낱말이 아니라 {@code id}로 본다. Thymeleaf는 HTML 주석을 응답에 그대로
 * 내보내므로 "인기글"이라는 낱말은 영역이 빠진 응답에도 남는다.
 */
@SpringBootTest
@MariaDbIntegrationTest
class HomeScreenRenderingTests {

    private static final String POPULAR_SECTION = "id=\"popular-posts\"";

    private static final LocalDate RANKING_DATE = LocalDate.of(2026, 3, 9);

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private CommunityHomeQueryService communityHomeQueryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void home_confirmedRanking_showsPopularPostsWithRankingDate() throws Exception {
        when(communityHomeQueryService.getPopularSection()).thenReturn(
                new PopularSectionView(
                        RANKING_DATE,
                        List.of(new PopularPostView(1, 11L, "질문", "메인에 실린 인기글"))));

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(POPULAR_SECTION)))
                .andExpect(content().string(containsString("메인에 실린 인기글")))
                .andExpect(content().string(containsString("2026.03.09")))
                .andExpect(content().string(containsString("/community/11")));
    }

    @Test
    void home_noConfirmedRanking_dropsPopularSectionEntirely() throws Exception {
        when(communityHomeQueryService.getPopularSection())
                .thenReturn(PopularSectionView.empty());

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(POPULAR_SECTION))));
    }
}
