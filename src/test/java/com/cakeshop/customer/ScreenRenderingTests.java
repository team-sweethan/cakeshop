package com.cakeshop.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cakeshop.global.config.MariaDbIntegrationTest;
import com.cakeshop.domain.order.dto.form.GeneralOrderForm;
import com.cakeshop.domain.order.service.OrderCheckoutService;
import com.cakeshop.domain.order.service.OrderServiceImpl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.time.LocalDateTime;

// 시드는 Flyway 관리 대상이 아니라서 locations 로는 못 불러온다. 스크립트로 직접 넣는다.
@SpringBootTest(properties = {
    "app.mockup.public-preview=true",
    "app.payment.toss.client-key=test-client-key"
})
@MariaDbIntegrationTest
@Sql(scripts = "classpath:db/seed/seed-local.sql",
     executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class ScreenRenderingTests {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private OrderServiceImpl orderService;

    @Autowired
    private OrderCheckoutService orderCheckoutService;

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
            "/products", "/products/1", "/cart"
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
    @WithUserDetails(
        value = "user@cakeshop.local",
        userDetailsServiceBeanName = "memberDetailsService"
    )
    void memberScreensRenderWithSeededUser() throws Exception {
        String[] paths = {
            "/orders/custom/options", "/orders/custom/request",
            "/orders/checkout?productId=1&quantity=1&optionIds=1",
            "/mypage",
            "/orders", "/notifications", "/reviews/new", "/mypage/coupons",
            "/mypage/profile"
        };

        assertScreensRender(paths);
    }

    @Test
    @WithUserDetails(
        value = "user@cakeshop.local",
        userDetailsServiceBeanName = "memberDetailsService"
    )
    void orderCheckout_rendersActualProductAndGeneralOrderAction()
            throws Exception {
        mockMvc.perform(get(
                "/orders/checkout?productId=1&quantity=1&optionIds=1"
            ))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("딸기 생크림 케이크")))
            .andExpect(content().string(containsString("케이크 크기")))
            .andExpect(content().string(containsString("action=\"/orders/general\"")))
            .andExpect(content().string(containsString("data-pickup-date")))
            .andExpect(content().string(containsString("data-pickup-time-panel")))
            .andExpect(content().string(containsString("type=\"radio\"")))
            .andExpect(content().string(containsString("data-same-as-orderer")))
            .andExpect(content().string(containsString("value=\"테스트회원\"")))
            .andExpect(content().string(containsString("value=\"010-0000-0002\"")))
            .andExpect(content().string(not(containsString("data-success-url"))))
            .andExpect(content().string(not(containsString("/orders/1/payment"))));
    }

    @Test
    @Transactional
    @WithUserDetails(
        value = "user@cakeshop.local",
        userDetailsServiceBeanName = "memberDetailsService"
    )
    void paymentScreen_createdOrder_rendersActualTossRequestData()
            throws Exception {
        long orderId = createGeneralOrder();

        mockMvc.perform(get("/orders/{orderId}/payment", orderId))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("딸기 생크림 케이크")))
            .andExpect(content().string(containsString(
                "https://js.tosspayments.com/v2/standard"
            )))
            .andExpect(content().string(containsString("data-payment-amount=\"35000\"")))
            .andExpect(content().string(not(containsString("data-mock-form"))));
    }

    @Test
    @Transactional
    @WithUserDetails(
        value = "user@cakeshop.local",
        userDetailsServiceBeanName = "memberDetailsService"
    )
    void orderDetail_createdOrder_rendersStableHistoryCardLayout()
            throws Exception {
        long orderId = createGeneralOrder();

        mockMvc.perform(get("/orders/{orderId}", orderId))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("order-history-card")))
            .andExpect(content().string(containsString(
                "order-history-card__number"
            )))
            .andExpect(content().string(containsString("order-history-detail")))
            .andExpect(content().string(not(containsString(
                "style=\"grid-column:span 2\""
            ))));
    }

    @Test
    @Transactional
    @WithUserDetails(
        value = "user@cakeshop.local",
        userDetailsServiceBeanName = "memberDetailsService"
    )
    void completionScreen_donePayment_rendersActualOrderAndMethod()
            throws Exception {
        long orderId = createGeneralOrder();
        jdbcTemplate.update(
            "UPDATE payments SET status = 'DONE', payment_key = ?, method = ?, provider_status = 'DONE' "
                + "WHERE order_id = ?",
            "test-payment-key-" + orderId,
            "카드",
            orderId
        );
        jdbcTemplate.update(
            "UPDATE orders SET status = 'READY_FOR_PICKUP' WHERE id = ?",
            orderId
        );

        mockMvc.perform(get("/orders/complete").param("orderId", String.valueOf(orderId)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("딸기 생크림 케이크")))
            .andExpect(content().string(containsString("35,000원")))
            .andExpect(content().string(containsString("카드")))
            .andExpect(content().string(containsString("매장 1층 픽업 데스크")));
    }

    @Test
    @Transactional
    @WithUserDetails(
        value = "user@cakeshop.local",
        userDetailsServiceBeanName = "memberDetailsService"
    )
    void paymentCallbacks_createdOrder_renderCsrfBridgeAndSafeFailure()
            throws Exception {
        long orderId = createGeneralOrder();
        String tossOrderId = jdbcTemplate.queryForObject(
            "SELECT toss_order_id FROM payments WHERE order_id = ?",
            String.class,
            orderId
        );

        mockMvc.perform(get("/orders/{orderId}/payment/success", orderId)
                .param("paymentKey", "test-payment-key")
                .param("orderId", tossOrderId)
                .param("amount", "35000"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("payment-confirm-form")))
            .andExpect(content().string(containsString("name=\"_csrf\"")))
            .andExpect(content().string(containsString(
                "/orders/" + orderId + "/payment/confirm"
            )));

        mockMvc.perform(get("/orders/{orderId}/payment/fail", orderId)
                .param("code", "PAY_PROCESS_CANCELED")
                .param("message", "provider raw message must not be shown"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("결제가 취소되었습니다")))
            .andExpect(content().string(not(containsString(
                "provider raw message must not be shown"
            ))));
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
    @Transactional
    @WithUserDetails(
        value = "admin@cakeshop.local",
        userDetailsServiceBeanName = "memberDetailsService"
    )
    void fulfillmentAdminScreen_readyOrder_marksPickupComplete()
            throws Exception {
        long orderId = createGeneralOrder();
        jdbcTemplate.update(
            """
            UPDATE payments
            SET status = 'DONE',
                provider_status = 'DONE',
                payment_key = ?,
                approved_at = CURRENT_TIMESTAMP(6)
            WHERE order_id = ?
            """,
            "SCREEN-FULFILLMENT-" + orderId,
            orderId
        );
        jdbcTemplate.update(
            """
            UPDATE orders
            SET status = 'READY_FOR_PICKUP',
                ready_at = CURRENT_TIMESTAMP(6)
            WHERE id = ?
            """,
            orderId
        );
        LocalDateTime pickupAt = jdbcTemplate.queryForObject(
            "SELECT pickup_at FROM orders WHERE id = ?",
            LocalDateTime.class,
            orderId
        );

        mockMvc.perform(get("/admin/fulfillment")
                .param("pickupDate", pickupAt.toLocalDate().toString()))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("딸기 생크림 케이크")))
            .andExpect(content().string(containsString("케이크 크기: 1호")))
            .andExpect(content().string(containsString("<details")))
            .andExpect(content().string(containsString(
                "data-order-id=\"" + orderId + "\""
            )))
            .andExpect(content().string(containsString("테스트회원")))
            .andExpect(content().string(containsString("주문 ID #" + orderId)))
            .andExpect(content().string(containsString("픽업 완료 처리")))
            .andExpect(content().string(containsString(
                "/admin/fulfillment/" + orderId + "/pickup"
            )))
            .andExpect(content().string(containsString("name=\"_csrf\"")));

        mockMvc.perform(post("/admin/fulfillment/{orderId}/pickup", orderId)
                .with(csrf())
                .param("pickupDate", pickupAt.toLocalDate().toString()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl(
                "/admin/fulfillment?pickupDate=" + pickupAt.toLocalDate()
            ));

        assertThat(jdbcTemplate.queryForObject(
            "SELECT status FROM orders WHERE id = ?",
            String.class,
            orderId
        )).isEqualTo("PICKED_UP");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT picked_up_by FROM orders WHERE id = ?",
            Long.class,
            orderId
        )).isEqualTo(jdbcTemplate.queryForObject(
            "SELECT id FROM members WHERE email = ?",
            Long.class,
            "admin@cakeshop.local"
        ));
    }

    @Test
    @Transactional
    @WithUserDetails(
        value = "admin@cakeshop.local",
        userDetailsServiceBeanName = "memberDetailsService"
    )
    void paymentAdminScreen_donePayment_rendersActualPaymentHistory()
            throws Exception {
        long orderId = createGeneralOrder();
        jdbcTemplate.update(
            """
            UPDATE payments
            SET status = 'DONE',
                provider_status = 'DONE',
                payment_key = ?,
                method = 'CARD',
                approved_at = CURRENT_TIMESTAMP(6)
            WHERE order_id = ?
            """,
            "SCREEN-PAYMENT-" + orderId,
            orderId
        );

        String orderNumber = jdbcTemplate.queryForObject(
            "SELECT order_number FROM orders WHERE id = ?",
            String.class,
            orderId
        );

        mockMvc.perform(get("/admin/payments").param("status", "DONE"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("결제 내역")))
            .andExpect(content().string(containsString(orderNumber)))
            .andExpect(content().string(containsString("35,000원")))
            .andExpect(content().string(containsString("결제 완료")))
            .andExpect(content().string(containsString("data-payment-id")))
            .andExpect(content().string(containsString(
                "/admin/orders/" + orderId
            )))
            .andExpect(content().string(not(containsString("PAY-001"))))
            .andExpect(content().string(not(containsString("환불 처리하시겠습니까"))));
    }

    @Test
    @Transactional
    @WithUserDetails(
        value = "admin@cakeshop.local",
        userDetailsServiceBeanName = "memberDetailsService"
    )
    void adminOrderDetail_generalPaidOrder_rendersCancellationForm()
            throws Exception {
        long orderId = createGeneralOrder();
        jdbcTemplate.update(
            """
            UPDATE payments
            SET status = 'DONE',
                provider_status = 'DONE',
                payment_key = ?,
                method = 'CARD',
                approved_at = CURRENT_TIMESTAMP(6)
            WHERE order_id = ?
            """,
            "SCREEN-ADMIN-CANCEL-" + orderId,
            orderId
        );
        jdbcTemplate.update(
            """
            UPDATE orders
            SET status = 'READY_FOR_PICKUP',
                ready_at = CURRENT_TIMESTAMP(6)
            WHERE id = ?
            """,
            orderId
        );

        mockMvc.perform(get("/admin/orders/{orderId}", orderId))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("관리자 취소 사유")))
            .andExpect(content().string(containsString("주문·결제 취소")))
            .andExpect(content().string(containsString("제작·픽업에서 처리")))
            .andExpect(content().string(containsString(
                "/admin/fulfillment?pickupDate="
            )))
            .andExpect(content().string(not(containsString(
                "/admin/orders/" + orderId + "/pickup"
            ))))
            .andExpect(content().string(containsString(
                "/admin/orders/" + orderId + "/cancel"
            )))
            .andExpect(content().string(containsString("name=\"_csrf\"")));
    }

    private void assertScreensRender(String[] paths) throws Exception {
        for (String path : paths) {
            mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"));
        }
    }

    private long createGeneralOrder() {
        long memberId = jdbcTemplate.queryForObject(
            "SELECT id FROM members WHERE email = ?",
            Long.class,
            "user@cakeshop.local"
        );
        var checkout = orderCheckoutService.getGeneralCheckout(
            1L,
            1,
            List.of(1L)
        );

        GeneralOrderForm form = new GeneralOrderForm();
        form.setRequestKey(java.util.UUID.randomUUID().toString());
        form.setProductId(1L);
        form.setQuantity(1);
        form.setOptionIds(List.of(1L));
        form.setOrdererName("테스트회원");
        form.setOrdererPhone("010-0000-0002");
        form.setPickupName("테스트회원");
        form.setPickupPhone("010-0000-0002");
        form.setPickupAt(
            checkout.pickupDates().getFirst().times().getFirst().value()
        );

        return orderService.createGeneralOrder(memberId, form);
    }
}
