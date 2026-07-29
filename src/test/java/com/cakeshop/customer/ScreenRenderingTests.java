package com.cakeshop.customer;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
            "/screens", "/login", "/signup", "/products", "/products/1", "/cart"
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
    @WithUserDetails(
        value = "user@cakeshop.local",
        userDetailsServiceBeanName = "memberDetailsService"
    )
    void memberScreensRenderWithSeededUser() throws Exception {
        String[] paths = {
            "/orders/pickup", "/orders/custom/options", "/orders/custom/request",
            "/orders/checkout", "/orders/1/payment", "/orders/complete", "/mypage",
            "/orders/1", "/notifications", "/reviews/new", "/mypage/coupons",
            "/mypage/profile"
        };

        assertScreensRender(paths);
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
