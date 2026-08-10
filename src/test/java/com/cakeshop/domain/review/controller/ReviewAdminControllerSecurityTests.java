package com.cakeshop.domain.review.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.cakeshop.domain.review.dto.view.AdminReviewListView;
import com.cakeshop.domain.review.service.ReviewAdminService;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;
import com.cakeshop.global.security.SecurityConfig;

@WebMvcTest(ReviewAdminController.class)
@Import(SecurityConfig.class)
class ReviewAdminControllerSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReviewAdminService reviewAdminService;

    @Test
    @WithAnonymousUser
    void adminReviews_redirectsToLogin_whenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/admin/reviews"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void adminReviews_isForbidden_forCustomerRole() throws Exception {
        mockMvc.perform(get("/admin/reviews"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminReviews_isAccessible_forAdminRole() throws Exception {
        when(reviewAdminService.getReviews(any(), any(), any(), any(), any(PageRequest.class)))
                .thenReturn(new PageResult<AdminReviewListView>(
                        List.of(), new PageRequest(null, null), 0));

        mockMvc.perform(get("/admin/reviews"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/review/list"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void block_isForbidden_forCustomerRole() throws Exception {
        mockMvc.perform(post("/admin/reviews/1/block").with(csrf()))
                .andExpect(status().isForbidden());

        verify(reviewAdminService, never()).block(anyLong());
    }

    @Test
    @WithMockUser(roles = "USER")
    void unblock_isForbidden_forCustomerRole() throws Exception {
        mockMvc.perform(post("/admin/reviews/1/unblock").with(csrf()))
                .andExpect(status().isForbidden());

        verify(reviewAdminService, never()).unblock(anyLong());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void block_isForbidden_whenCsrfTokenIsMissing() throws Exception {
        mockMvc.perform(post("/admin/reviews/1/block"))
                .andExpect(status().isForbidden());

        verify(reviewAdminService, never()).block(anyLong());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void block_redirectsBackToTheDetail_forAdminRole() throws Exception {
        mockMvc.perform(post("/admin/reviews/1/block").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/reviews/1"));

        verify(reviewAdminService).block(1L);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void unblock_redirectsBackToTheDetail_forAdminRole() throws Exception {
        mockMvc.perform(post("/admin/reviews/1/unblock").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/reviews/1"));

        verify(reviewAdminService).unblock(1L);
    }
}
