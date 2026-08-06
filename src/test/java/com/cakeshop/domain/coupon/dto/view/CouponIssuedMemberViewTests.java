package com.cakeshop.domain.coupon.dto.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.cakeshop.domain.coupon.entity.CustomerCouponStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class CouponIssuedMemberViewTests {

    @Test
    void constructor_masksPhoneAndKeepsOnlyBirthday() {
        CouponIssuedMemberView view = new CouponIssuedMemberView(
                1L, "홍길동", "member@example.com", "010-1234-5678", "01-15",
                CustomerCouponStatus.AVAILABLE, LocalDateTime.of(2026, 8, 6, 10, 0)
        );

        assertThat(view.email()).isEqualTo("me***@example.com");
        assertThat(view.phone()).isEqualTo("010-****-5678");
        assertThat(view.birthday()).isEqualTo("01-15");
    }
}
