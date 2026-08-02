package com.cakeshop.customer;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cakeshop.global.config.MariaDbIntegrationTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

// 시드는 Flyway 관리 대상이 아니라서 locations 로는 못 불러온다. 스크립트로 직접 넣는다.
@SpringBootTest(properties = "app.mockup.public-preview=true")
@MariaDbIntegrationTest
@Sql(scripts = "classpath:db/seed/seed-local.sql",
     executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class ScreenRenderingTests {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
            .apply(springSecurity())
            .build();
    }

    @Test
    void publicScreensRenderWithoutAuthentication() throws Exception {
        String[] paths = {
            "/screens", "/login", "/signup", "/find-email",
            "/products", "/products/1"
        };

        assertScreensRender(paths);
    }

    @Test
    void login_successMessage_rendersCommonPopupFragment() throws Exception {
        mockMvc.perform(get("/login")
                .flashAttr("successMessage", "회원가입이 완료되었습니다!"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("data-common-alert-popup")))
            .andExpect(content().string(containsString("window.alert(")));
    }

    @Test
    void login_recoveredEmail_prefillsEmailInput() throws Exception {
        mockMvc.perform(get("/login")
                .flashAttr("recoveredEmail", "member@example.com"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("value=\"member@example.com\"")));
    }

    @Test
    void myPage_unauthenticatedMember_redirectsToLoginEvenInPublicPreview() throws Exception {
        mockMvc.perform(get("/mypage"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login"));
    }

    @Test
    void cart_unauthenticatedMember_redirectsToLoginEvenInPublicPreview() throws Exception {
        mockMvc.perform(get("/cart"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login"));
    }

    @Test
    void productDetail_unauthenticatedMember_showsLoginCartLinkOnly() throws Exception {
        mockMvc.perform(get("/products/1"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("로그인 후 장바구니 담기")))
            .andExpect(content().string(not(containsString("data-server-cart-form"))));
    }

    @Test
    @WithUserDetails(
        value = "user@cakeshop.local",
        userDetailsServiceBeanName = "memberDetailsService"
    )
    void memberScreensRenderWithSeededUser() throws Exception {
        String[] paths = {
            "/cart",
            "/orders/pickup", "/orders/custom/options", "/orders/custom/request",
            "/orders/checkout", "/orders/1/payment", "/orders/complete", "/mypage",
            "/orders/1", "/notifications", "/reviews/new", "/mypage/coupons",
            "/mypage/profile"
        };

        assertScreensRender(paths);

        mockMvc.perform(get("/products/1"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("data-server-cart-form")))
            .andExpect(content().string(not(containsString("로그인 후 장바구니 담기"))));
    }

    @Test
    @WithUserDetails(
        value = "admin@cakeshop.local",
        userDetailsServiceBeanName = "memberDetailsService"
    )
    void productOptionAdminScreenRendersWithSeededAdmin()
            throws Exception {
        assertScreensRender(new String[] {"/admin/products"});

        mockMvc.perform(get("/admin/products/1/options"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("text/html"))
            .andExpect(content().string(containsString("옵션:")))
            .andExpect(content().string(containsString("1호")))
            .andExpect(content().string(containsString("2호")))
            .andExpect(content().string(containsString("위로 이동")))
            .andExpect(content().string(containsString("아래로 이동")))
            .andExpect(content().string(not(
                containsString("name=\"sortOrder\"")
            )));
    }

    private void assertScreensRender(String[] paths) throws Exception {
        for (String path : paths) {
            mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"));
        }
    }
}
