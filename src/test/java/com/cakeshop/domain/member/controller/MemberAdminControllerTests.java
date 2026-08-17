package com.cakeshop.domain.member.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
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
import com.cakeshop.domain.member.service.MemberSessionService;
import com.cakeshop.domain.member.dto.view.MemberAuthenticationView;
import com.cakeshop.domain.order.dto.view.OrderMemberOrderView;
import com.cakeshop.domain.order.service.OrderMemberQueryService;
import com.cakeshop.global.security.MemberDetails;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.core.MethodParameter;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

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
                        new MemberAdminController(
                                memberAdminService,
                                mock(MemberSessionService.class),
                                mock(OrderMemberQueryService.class)))
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
                        new MemberAdminController(
                                memberAdminService,
                                mock(MemberSessionService.class),
                                mock(OrderMemberQueryService.class)))
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
        OrderMemberQueryService orderMemberQueryService =
                mock(OrderMemberQueryService.class);
        PageResult<OrderMemberOrderView> orderPageResult =
                new PageResult<>(
                        List.of(),
                        new PageRequest(2, 10),
                        12);

        when(memberAdminService.getMemberDetail(1L))
                .thenReturn(member);
        when(orderMemberQueryService.getAdminMemberOrders(anyLong(), any()))
                .thenReturn(orderPageResult);

        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new MemberAdminController(
                                memberAdminService,
                                mock(MemberSessionService.class),
                                orderMemberQueryService))
                .build();

        mockMvc.perform(get("/admin/members/1")
                        .param("orderPage", "2"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/member/detail"))
                .andExpect(model().attribute("member", member))
                .andExpect(model().attribute("orderPageResult", orderPageResult));

        verify(memberAdminService).getMemberDetail(1L);
        ArgumentCaptor<PageRequest> orderPageCaptor =
                ArgumentCaptor.forClass(PageRequest.class);
        verify(orderMemberQueryService)
                .getAdminMemberOrders(anyLong(), orderPageCaptor.capture());
        assertThat(orderPageCaptor.getValue().getPage()).isEqualTo(2);
        assertThat(orderPageCaptor.getValue().getSize()).isEqualTo(10);
    }

    @Test
    void suspendMember_detailSource_expiresSessionsAndRedirectsToDetail()
            throws Exception {
        MemberAdminService memberAdminService =
                mock(MemberAdminService.class);
        MemberSessionService memberSessionService =
                mock(MemberSessionService.class);
        when(memberAdminService.suspendMember(
                1L,
                "정지 사유",
                99L))
                .thenReturn("member@example.com");
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new MemberAdminController(
                                memberAdminService,
                                memberSessionService,
                                mock(OrderMemberQueryService.class)))
                .setCustomArgumentResolvers(
                        new AdminDetailsArgumentResolver())
                .build();

        mockMvc.perform(post("/admin/members/1/suspend")
                        .param("reason", "정지 사유")
                        .param("source", "detail"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/members/1"))
                .andExpect(flash().attribute(
                        "successMessage",
                        "회원 이용을 정지했습니다."));

        verify(memberAdminService).suspendMember(1L, "정지 사유", 99L);
        verify(memberSessionService)
                .expireSessionsByEmail("member@example.com");
    }

    @Test
    void suspendMember_blankReason_redirectsWithError()
            throws Exception {
        MemberAdminService memberAdminService =
                mock(MemberAdminService.class);
        MemberSessionService memberSessionService =
                mock(MemberSessionService.class);
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new MemberAdminController(
                                memberAdminService,
                                memberSessionService,
                                mock(OrderMemberQueryService.class)))
                .setCustomArgumentResolvers(
                        new AdminDetailsArgumentResolver())
                .build();

        mockMvc.perform(post("/admin/members/1/suspend")
                        .param("reason", "   "))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/members"))
                .andExpect(flash().attribute(
                        "errorMessage",
                        "이용정지 사유를 확인해 주세요."));

        verify(memberAdminService, never())
                .suspendMember(any(), any(), any());
        verify(memberSessionService, never())
                .expireSessionsByEmail(any());
    }

    @Test
    void activateMember_suspendedMember_redirects() throws Exception {
        MemberAdminService memberAdminService =
                mock(MemberAdminService.class);
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new MemberAdminController(
                                memberAdminService,
                                mock(MemberSessionService.class),
                                mock(OrderMemberQueryService.class)))
                .setCustomArgumentResolvers(
                        new AdminDetailsArgumentResolver())
                .build();

        mockMvc.perform(post("/admin/members/1/activate"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/members"))
                .andExpect(flash().attribute(
                        "errorMessage",
                        "이용정지 해제 사유를 확인해 주세요."));

        mockMvc.perform(post("/admin/members/1/activate")
                        .param("reason", "해제 사유"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/members"))
                .andExpect(flash().attribute(
                        "successMessage",
                        "회원 이용정지를 해제했습니다."));

        verify(memberAdminService).activateMember(1L, "해제 사유", 99L);
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
                null,
                List.of());
    }

    private static UsernamePasswordAuthenticationToken adminPrincipal() {
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

    private static class AdminDetailsArgumentResolver
            implements HandlerMethodArgumentResolver {

        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.getParameterType() == MemberDetails.class;
        }

        @Override
        public Object resolveArgument(
                MethodParameter parameter,
                ModelAndViewContainer mavContainer,
                NativeWebRequest webRequest,
                org.springframework.web.bind.support.WebDataBinderFactory binderFactory) {
            return adminPrincipal().getPrincipal();
        }
    }
}
