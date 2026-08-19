package com.cakeshop.domain.order.controller.customer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

class PendingPaymentNewOrderIntentSessionTests {

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    @AfterEach
    void shutDownExecutor() {
        executor.shutdownNow();
    }

    @Test
    void reserve_allowsOnlyOneConcurrentRequestToUseAnIssuedIntent() throws Exception {
        MockHttpSession session = new MockHttpSession();
        PendingPaymentNewOrderIntentSession.issue(session, 10L);
        CountDownLatch start = new CountDownLatch(1);

        Future<PendingPaymentNewOrderIntentSession.Reservation> first = executor.submit(() -> {
            start.await();
            return PendingPaymentNewOrderIntentSession.reserve(session, 10L);
        });
        Future<PendingPaymentNewOrderIntentSession.Reservation> second = executor.submit(() -> {
            start.await();
            return PendingPaymentNewOrderIntentSession.reserve(session, 10L);
        });

        start.countDown();

        assertThat(java.util.Arrays.asList(first.get(), second.get()))
                .filteredOn(java.util.Objects::nonNull)
                .hasSize(1);
    }

    @Test
    void release_makesFailedReservationAvailableForRetry() {
        MockHttpSession session = new MockHttpSession();
        PendingPaymentNewOrderIntentSession.issue(session, 10L);
        PendingPaymentNewOrderIntentSession.Reservation reservation =
                PendingPaymentNewOrderIntentSession.reserve(session, 10L);

        PendingPaymentNewOrderIntentSession.release(session, reservation);

        assertThat(PendingPaymentNewOrderIntentSession.reserve(session, 10L)).isNotNull();
    }
}
