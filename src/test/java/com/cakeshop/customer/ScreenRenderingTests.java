package com.cakeshop.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

// 시드는 Flyway 관리 대상이 아니라서 locations 로는 못 불러온다. 스크립트로 직접 넣는다.
@SpringBootTest(properties = "app.mockup.public-preview=true")
@MariaDbIntegrationTest
// encoding 을 명시하지 않으면 JVM 기본 문자셋으로 읽어, 시드의 한글이 PC 에 따라 깨진다.
@Sql(scripts = "classpath:db/seed/seed-local.sql",
     config = @SqlConfig(encoding = "UTF-8"),
     executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class ScreenRenderingTests {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
    void productScreens_errorMessage_renderCommonAlertFragment() throws Exception {
        for (String path : new String[] {"/products", "/products/1"}) {
            mockMvc.perform(get(path)
                    .flashAttr("errorMessage", "장바구니 오류"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("alert alert--error")))
                .andExpect(content().string(containsString("장바구니 오류")));
        }
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
    void cartCount_unauthenticatedRequest_isNotSavedForLoginRedirect() throws Exception {
        MvcResult countResult = mockMvc.perform(get("/cart/count"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login"))
            .andReturn();

        MockHttpSession countSession = (MockHttpSession) countResult.getRequest().getSession(false);
        assertThat(countSession == null
                ? null
                : countSession.getAttribute("SPRING_SECURITY_SAVED_REQUEST"))
            .isNull();

        MvcResult pageResult = mockMvc.perform(get("/cart"))
            .andExpect(status().is3xxRedirection())
            .andReturn();
        MockHttpSession pageSession = (MockHttpSession) pageResult.getRequest().getSession(false);
        assertThat(pageSession).isNotNull();
        assertThat(pageSession.getAttribute("SPRING_SECURITY_SAVED_REQUEST")).isNotNull();
    }

    @Test
    void productDetail_unauthenticatedMember_showsLoginCartLinkOnly() throws Exception {
        mockMvc.perform(get("/products/1"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("로그인 후 장바구니 담기")))
            .andExpect(content().string(not(containsString("data-server-cart-form"))));
    }

    @Test
    void productDetail_imagesMissing_rendersPlaceholderWithoutImageTag() throws Exception {
        mockMvc.perform(get("/products/1"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("상품 이미지 준비 중")))
            .andExpect(content().string(containsString("등록된 상품 이미지가 없습니다.")))
            .andExpect(content().string(not(containsString("data-product-main-image"))));
    }

    @Test
    @Transactional
    void productDetail_imagesExist_rendersMainImageAndRemainingThumbnails() throws Exception {
        Long productId = jdbcTemplate.queryForObject(
            "SELECT id FROM products WHERE name = '딸기 생크림 케이크'",
            Long.class
        );
        jdbcTemplate.update(
            """
            INSERT INTO product_images (
                product_id,
                image_url,
                sort_order
            )
            VALUES
                (?, '/uploads/product/main.jpg', 0),
                (?, '/uploads/product/second.jpg', 1),
                (?, '/uploads/product/third.jpg', 2)
            """,
            productId,
            productId,
            productId
        );

        mockMvc.perform(get("/products/{productId}", productId))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("data-product-main-image")))
            .andExpect(content().string(containsString("/uploads/product/main.jpg")))
            .andExpect(content().string(containsString("/uploads/product/second.jpg")))
            .andExpect(content().string(containsString("/uploads/product/third.jpg")))
            .andExpect(content().string(not(containsString("등록된 상품 이미지가 없습니다."))));
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
        value = "user@cakeshop.local",
        userDetailsServiceBeanName = "memberDetailsService"
    )
    void customProductDetail_showsCustomOptionFlowWithoutServerCartForm() throws Exception {
        mockMvc.perform(get("/products/6"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("/orders/custom/options")))
            .andExpect(content().string(not(containsString("data-server-cart-form"))));
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

    @Test
    @WithUserDetails(
        value = "admin@cakeshop.local",
        userDetailsServiceBeanName = "memberDetailsService"
    )
    void productEdit_imagesMissing_rendersUploadFormAndPlaceholder()
            throws Exception {
        mockMvc.perform(get("/admin/products/1/edit"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString(
                "data-product-image-upload"
            )))
            .andExpect(content().string(containsString(
                "등록된 상품 이미지가 없습니다."
            )))
            .andExpect(content().string(containsString(
                "form=\"productInfoForm\""
            )))
            .andExpect(content().string(not(matchesPattern(
                "(?s).*<button(?=[^>]*data-image-upload-button)"
                    + "(?=[^>]*disabled)[^>]*>.*"
            ))));
    }

    @Test
    @Transactional
    @WithUserDetails(
        value = "admin@cakeshop.local",
        userDetailsServiceBeanName = "memberDetailsService"
    )
    void productEdit_fiveImagesExist_rendersRepresentativeAndDisablesUpload()
            throws Exception {
        Long productId = jdbcTemplate.queryForObject(
            "SELECT id FROM products WHERE name = '딸기 생크림 케이크'",
            Long.class
        );
        jdbcTemplate.update(
            """
            INSERT INTO product_images (
                product_id,
                image_url,
                sort_order
            )
            VALUES
                (?, '/uploads/product/first.jpg', 0),
                (?, '/uploads/product/second.jpg', 1),
                (?, '/uploads/product/third.jpg', 2),
                (?, '/uploads/product/fourth.jpg', 3),
                (?, '/uploads/product/fifth.jpg', 4)
            """,
            productId,
            productId,
            productId,
            productId,
            productId
        );

        mockMvc.perform(get(
                "/admin/products/{productId}/edit",
                productId
            ))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString(
                "/uploads/product/first.jpg"
            )))
            .andExpect(content().string(containsString(
                "대표 이미지"
            )))
            .andExpect(content().string(containsString(
                "상품 이미지를 최대 5장까지 등록했습니다."
            )))
            .andExpect(content().string(matchesPattern(
                "(?s).*<button(?=[^>]*data-image-upload-button)"
                    + "(?=[^>]*disabled)[^>]*>.*"
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
