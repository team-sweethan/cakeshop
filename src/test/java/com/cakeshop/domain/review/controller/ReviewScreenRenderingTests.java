package com.cakeshop.domain.review.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.cakeshop.global.config.MariaDbIntegrationTest;

/** 후기 작성 화면 둘을 실제 Thymeleaf로 렌더링한다. 표현식이 깨져도 컴파일은 통과한다. */
@SpringBootTest
@MariaDbIntegrationTest
@Transactional
// encoding 을 명시하지 않으면 JVM 기본 문자셋으로 읽어, 시드의 한글이 PC 에 따라 깨진다.
@Sql(scripts = "classpath:db/seed/seed-local.sql",
     config = @SqlConfig(encoding = "UTF-8"),
     executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class ReviewScreenRenderingTests {

    private static final String MEMBER_EMAIL = "user@cakeshop.local";
    private static final LocalDateTime PICKED_UP_AT = LocalDateTime.of(2026, 3, 1, 10, 0);

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
    @WithUserDetails(value = MEMBER_EMAIL, userDetailsServiceBeanName = "memberDetailsService")
    void writableList_withoutPickedUpOrder_showsEmptyNotice() throws Exception {
        // 빈 목록은 정상 상태라 오류로 드러나지 않는다. 평소 화면에 없는 분기를 여기서 고정한다.
        mockMvc.perform(get("/mypage/reviews/writable"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("작성할 후기가 없습니다.")));
    }

    @Test
    @WithUserDetails(value = MEMBER_EMAIL, userDetailsServiceBeanName = "memberDetailsService")
    void writableList_withPickedUpOrder_showsItem() throws Exception {
        long orderItemId = createPickedUpOrderItem();

        mockMvc.perform(get("/mypage/reviews/writable"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(productName())))
                .andExpect(content().string(containsString("orderItemId=" + orderItemId)));
    }

    @Test
    @WithUserDetails(value = MEMBER_EMAIL, userDetailsServiceBeanName = "memberDetailsService")
    void form_withWritableOrderItem_rendersFourRatings() throws Exception {
        long orderItemId = createPickedUpOrderItem();

        mockMvc.perform(get("/reviews/new").param("orderItemId", String.valueOf(orderItemId)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(productName())))
                // 평점 4종이 각자 다른 필드를 물어야 한다 — th:field 오타는 렌더링에서만 드러난다.
                .andExpect(content().string(containsString("name=\"overallRating\"")))
                .andExpect(content().string(containsString("name=\"tasteRating\"")))
                .andExpect(content().string(containsString("name=\"designRating\"")))
                .andExpect(content().string(containsString("name=\"serviceRating\"")));
    }

    private long createPickedUpOrderItem() {
        long memberId = jdbcTemplate.queryForObject(
                "SELECT id FROM members WHERE email = ?", Long.class, MEMBER_EMAIL);
        Map<String, Object> product = product();

        jdbcTemplate.update("""
                INSERT INTO orders (
                    order_number, member_id, order_type, orderer_name, orderer_phone,
                    pickup_name, pickup_phone, original_amount, final_amount,
                    status, pickup_at, picked_up_at
                )
                VALUES (?, ?, 'GENERAL', '테스트회원', '010-0000-0002', '테스트회원', '010-0000-0002',
                        10000, 10000, 'PICKED_UP', ?, ?)
                """,
                "ORD-REVIEW-" + System.nanoTime(), memberId, PICKED_UP_AT, PICKED_UP_AT);

        long orderId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        jdbcTemplate.update("""
                INSERT INTO order_items (
                    order_id, product_id, product_name, product_type,
                    quantity, base_price, total_amount
                )
                VALUES (?, ?, ?, ?, 1, 10000, 10000)
                """,
                orderId, product.get("id"), product.get("name"), product.get("product_type"));

        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private Map<String, Object> product() {
        return jdbcTemplate.queryForMap(
                "SELECT id, name, product_type FROM products ORDER BY id LIMIT 1");
    }

    private String productName() {
        return (String) product().get("name");
    }
}
