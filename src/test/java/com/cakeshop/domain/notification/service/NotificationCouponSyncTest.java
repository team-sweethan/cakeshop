package com.cakeshop.domain.notification.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.cakeshop.domain.coupon.dto.view.CouponNotificationView;
import com.cakeshop.domain.coupon.service.CouponNotificationQueryService;
import com.cakeshop.domain.member.service.MemberNotificationQueryService;
import com.cakeshop.domain.notification.entity.DeliveryScope;
import com.cakeshop.domain.notification.entity.Notification;
import com.cakeshop.domain.notification.entity.NotificationType;
import com.cakeshop.domain.notification.mapper.NotificationMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * ******************************
 * 작성자 : 김민정
 * 담당자 : 김민정
 * 작성일 : 2026-08-19
 * 기능 : 쿠폰 발급 및 만료 임박(3일 전) 알림 동기화 스케줄러 단위 테스트
 * 설명 : NotificationCouponSync가 신규 발급, 만료 임박 및 실패한 SMS 재시도/종결을 정상 수행하는지 검증한다.
 * ******************************
 */
@ExtendWith(MockitoExtension.class)
class NotificationCouponSyncTest {

    @Mock
    private CouponNotificationQueryService couponNotificationQueryService;

    @Mock
    private NotificationCouponQueryService notificationCouponQueryService;

    @Mock
    private MemberNotificationQueryService memberNotificationQueryService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private NotificationDeliveryService notificationDeliveryService;

    @Mock
    private NotificationMapper notificationMapper;

    @Spy
    private Clock clock = Clock.fixed(Instant.parse("2026-08-19T09:00:00Z"), ZoneId.of("Asia/Seoul"));

    @InjectMocks
    private NotificationCouponSync notificationCouponSync;

    @Test
    @DisplayName("신규 발급된 유효 쿠폰이 있는 경우 COUPON 발급 알림이 정상 발송된다")
    void syncCouponIssuance_newlyIssuedCoupon_sendsIssuanceNotification() {
        LocalDateTime now = LocalDateTime.now(clock);
        CouponNotificationView coupon = new CouponNotificationView(10L, 1L, 2L, "웰컴 10% 할인 쿠폰", now, now, now.plusDays(30));
        given(couponNotificationQueryService.findRecentlyIssuedMemberCoupons(any(), any(), any(), anyInt()))
                .willReturn(List.of(coupon));
        given(couponNotificationQueryService.findRecentlyStartedMemberCoupons(any(), any(), any(), anyInt()))
                .willReturn(List.of());
        given(memberNotificationQueryService.isMemberActive(2L)).willReturn(true);
        given(couponNotificationQueryService.isMemberCouponAvailableAndUnexpired(10L, coupon.expiresAt())).willReturn(true);
        given(notificationCouponQueryService.isCouponIssuedNotificationCompletedOrInactive(2L, 10L))
                .willReturn(false);

        notificationCouponSync.syncCouponIssuance();

        verify(notificationService).makeNotification(argThat(req ->
                req.getReceiverId() == 2L &&
                req.getUserCouponId() == 10L &&
                req.getType() == NotificationType.COUPON &&
                req.getDeliveryScope() == DeliveryScope.WEB_AND_SMS &&
                "COUPON:2:10".equals(req.getEventKey()) &&
                req.getArgs() != null &&
                "웰컴 10% 할인 쿠폰".equals(req.getArgs()[0])
        ));
    }

    @Test
    @DisplayName("사용 시작일이 도래한 미래 시작 쿠폰이 있는 경우 COUPON 발급 알림이 정상 발송된다")
    void syncCouponIssuance_startedCoupon_sendsIssuanceNotification() {
        LocalDateTime now = LocalDateTime.now(clock);
        CouponNotificationView coupon = new CouponNotificationView(11L, 2L, 3L, "오픈 기념 쿠폰", now.minusDays(5), now, now.plusDays(25));
        given(couponNotificationQueryService.findRecentlyIssuedMemberCoupons(any(), any(), any(), anyInt()))
                .willReturn(List.of());
        given(couponNotificationQueryService.findRecentlyStartedMemberCoupons(any(), any(), any(), anyInt()))
                .willReturn(List.of(coupon));
        given(memberNotificationQueryService.isMemberActive(3L)).willReturn(true);
        given(couponNotificationQueryService.isMemberCouponAvailableAndUnexpired(11L, coupon.expiresAt())).willReturn(true);
        given(notificationCouponQueryService.isCouponIssuedNotificationCompletedOrInactive(3L, 11L))
                .willReturn(false);

        notificationCouponSync.syncCouponIssuance();

        verify(notificationService).makeNotification(argThat(req ->
                req.getReceiverId() == 3L &&
                req.getUserCouponId() == 11L &&
                req.getType() == NotificationType.COUPON &&
                req.getDeliveryScope() == DeliveryScope.WEB_AND_SMS &&
                "COUPON:3:11".equals(req.getEventKey()) &&
                req.getArgs() != null &&
                "오픈 기념 쿠폰".equals(req.getArgs()[0])
        ));
    }

    @Test
    @DisplayName("실패한 외부 SMS 발송 알림이 있고 쿠폰이 여전히 유효한 경우 retryFailedCouponSms에서 정상 재시도된다")
    void retryFailedCouponSms_availableCoupon_retriesSuccessfully() {
        Notification failedNotification = Notification.builder()
                .id(100L)
                .receiverId(5L)
                .userCouponId(20L)
                .title("쿠폰 발급")
                .content("웰컴 쿠폰이 발급되었습니다.")
                .notificationType(NotificationType.COUPON)
                .eventKey("COUPON:5:20")
                .deliveryScope(DeliveryScope.WEB_AND_SMS)
                .build();

        given(notificationMapper.findRetryableCouponNotifications(any(), anyInt()))
                .willReturn(List.of(failedNotification))
                .willReturn(List.of());
        given(memberNotificationQueryService.isMemberActive(5L)).willReturn(true);
        given(couponNotificationQueryService.isMemberCouponAvailableAndUnexpired(20L, null)).willReturn(true);

        notificationCouponSync.retryFailedCouponSms();

        verify(notificationService).retrySmsForNotification(100L, 5L, "쿠폰 발급", "웰컴 쿠폰이 발급되었습니다.");
    }

    @Test
    @DisplayName("만료일이 연장되어 알림 당시의 만료일시와 불일치하는 경우 재시도를 건너뛰고 SKIPPED 종결 처리한다")
    void retryFailedCouponSms_extendedExpiration_skipsAndFinalizes() {
        Notification failedNotification = Notification.builder()
                .id(102L)
                .receiverId(7L)
                .userCouponId(22L)
                .title("쿠폰 만료 임박")
                .content("웰컴 쿠폰의 사용 기한이 2일 남았습니다.")
                .notificationType(NotificationType.COUPON_EXPIRING_SOON)
                .eventKey("COUPON_EXPIRING_SOON:7:22:202608210900")
                .deliveryScope(DeliveryScope.WEB_AND_SMS)
                .build();

        LocalDateTime parsedOldExpiresAt = LocalDateTime.parse("2026-08-21T09:00:00");
        given(notificationMapper.findRetryableCouponNotifications(any(), anyInt()))
                .willReturn(List.of(failedNotification))
                .willReturn(List.of());
        given(memberNotificationQueryService.isMemberActive(7L)).willReturn(true);
        // 당시 만료일(2026-08-21 09:00)과 현재 DB 만료일이 달라 불일치(false) 반환
        given(couponNotificationQueryService.isMemberCouponAvailableAndUnexpired(eq(22L), eq(parsedOldExpiresAt)))
                .willReturn(false);

        notificationCouponSync.retryFailedCouponSms();

        verify(notificationService, never()).retrySmsForNotification(any(), any(), any(), any());
        verify(notificationDeliveryService).recordSkippedAttempt(102L, "COUPON_UNAVAILABLE_OR_EXTENDED");
    }

    @Test
    @DisplayName("만료 3일 이내인 유효 쿠폰이 있고 활성 회원인 경우 COUPON_EXPIRING_SOON 만료 알림이 정상 발송된다")
    void syncCouponExpiration_expiringCoupon_sendsExpirationNotification() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime expiresAt = now.plusHours(50); // 50시간 = 2일
        CouponNotificationView coupon = new CouponNotificationView(10L, 1L, 2L, "웰컴 10% 할인 쿠폰", now.minusDays(20), now.minusDays(20), expiresAt);
        given(couponNotificationQueryService.findExpiringMemberCoupons(anyInt(), any(), any(), anyInt()))
                .willReturn(List.of(coupon));
        given(memberNotificationQueryService.isMemberActive(2L)).willReturn(true);
        given(couponNotificationQueryService.isMemberCouponAvailableAndUnexpired(10L, expiresAt)).willReturn(true);
        given(notificationCouponQueryService.isCouponExpiringNotificationCompletedOrInactive(2L, 10L, expiresAt))
                .willReturn(false);

        notificationCouponSync.syncCouponExpiration();

        verify(notificationService).makeNotification(argThat(req ->
                req.getReceiverId() == 2L &&
                req.getUserCouponId() == 10L &&
                req.getType() == NotificationType.COUPON_EXPIRING_SOON &&
                req.getDeliveryScope() == DeliveryScope.WEB_AND_SMS &&
                req.getEventKey().startsWith("COUPON_EXPIRING_SOON:2:10:") &&
                req.getArgs() != null &&
                "웰컴 10% 할인 쿠폰".equals(req.getArgs()[0]) &&
                "2일".equals(req.getArgs()[1])
        ));
    }
}
