package com.cakeshop.domain.order.controller.customer;

import jakarta.servlet.http.HttpSession;
import java.util.UUID;

/** 세션의 새 주문 진행 의도를 한 요청만 예약하고, 성공 시에만 소비한다. */
final class PendingPaymentNewOrderIntentSession {

    private static final String SESSION_KEY =
            PendingPaymentNewOrderIntentSession.class.getName() + ".newOrderIntent";

    private PendingPaymentNewOrderIntentSession() {
    }

    static void issue(HttpSession session, long memberId) {
        synchronized (session) {
            session.setAttribute(SESSION_KEY, new Intent(UUID.randomUUID(), memberId, false));
        }
    }

    static Reservation reserve(HttpSession session, long memberId) {
        synchronized (session) {
            Object value = session.getAttribute(SESSION_KEY);
            if (!(value instanceof Intent intent)
                    || intent.memberId() != memberId
                    || intent.reserved()) {
                return null;
            }
            session.setAttribute(SESSION_KEY, intent.reserve());
            return new Reservation(intent.intentId(), memberId);
        }
    }

    static void complete(HttpSession session, Reservation reservation) {
        synchronized (session) {
            Object value = session.getAttribute(SESSION_KEY);
            if (value instanceof Intent intent && intent.matches(reservation)) {
                session.removeAttribute(SESSION_KEY);
            }
        }
    }

    static void release(HttpSession session, Reservation reservation) {
        synchronized (session) {
            Object value = session.getAttribute(SESSION_KEY);
            if (value instanceof Intent intent && intent.matches(reservation)) {
                session.setAttribute(SESSION_KEY, intent.release());
            }
        }
    }

    record Reservation(UUID intentId, long memberId) {
    }

    private record Intent(UUID intentId, long memberId, boolean reserved) {

        private Intent reserve() {
            return new Intent(intentId, memberId, true);
        }

        private Intent release() {
            return new Intent(intentId, memberId, false);
        }

        private boolean matches(Reservation reservation) {
            return intentId.equals(reservation.intentId()) && memberId == reservation.memberId();
        }
    }
}
