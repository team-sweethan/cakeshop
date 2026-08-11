package com.cakeshop.domain.payment.infra;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TossPaymentAvailabilityTests {

    @Test
    void isEnabled_bothKeysPresent_returnsTrue() {
        TossPaymentAvailability availability = new TossPaymentAvailability(
                "test-client-key",
                "test-secret-key"
        );

        assertThat(availability.isEnabled()).isTrue();
        assertThat(availability.clientKey()).isEqualTo("test-client-key");
    }

    @Test
    void isEnabled_secretKeyMissing_returnsFalse() {
        TossPaymentAvailability availability = new TossPaymentAvailability("test-client-key", "");

        assertThat(availability.isEnabled()).isFalse();
    }
}
