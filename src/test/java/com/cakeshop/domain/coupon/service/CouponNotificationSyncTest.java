package com.cakeshop.domain.coupon.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.cakeshop.domain.coupon.dto.view.CouponNotificationView;
import com.cakeshop.domain.notification.entity.DeliveryScope;
import com.cakeshop.domain.notification.entity.NotificationType;
import com.cakeshop.domain.notification.service.NotificationCouponQueryService;
import com.cakeshop.domain.notification.service.NotificationService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
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
 * 담당자 : 이정후
 * 작성일 : 2026-08-19
 * 기능 : 쿠폰 발급 알림 동기화 스케줄러 단위 테스트
 * 설명 : CouponNotificationSync가 신규 발급 쿠폰을 감지하고 알림을 정상 발송하는지 검증한다.
 * ******************************
 */
@ExtendWith(MockitoExtension.class)
class CouponNotificationSyncTest {

    @Mock
    private CouponNotificationQueryService couponNotificationQueryService;

    @Mock
    private NotificationCouponQueryService notificationCouponQueryService;

    @Mock
    private NotificationService notificationService;

    @Spy
    private Clock clock = Clock.fixed(Instant.parse("2026-08-19T09:00:00Z"), ZoneId.of("Asia/Seoul"));

    @InjectMocks
    private CouponNotificationSync couponNotificationSync;

    @Test
    @DisplayName("신규 발급된 쿠폰이 있고 아직 알림이 전송되지 않은 경우 COUPON 알림이 정상 발송된다")
    void syncCouponNotifications_newCoupon_sendsNotification() {
        LocalDateTime now = LocalDateTime.now(clock);
        CouponNotificationView coupon = new CouponNotificationView(10L, 1L, 2L, "웰컴 10% 할인 쿠폰", now);
        given(couponNotificationQueryService.findRecentlyIssuedMemberCoupons(any(LocalDateTime.class)))
                .willReturn(List.of(coupon));
        given(notificationCouponQueryService.isCouponNotificationSentOrInactive(2L, 10L))
                .willReturn(false);

        couponNotificationSync.syncCouponNotifications();

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
    @DisplayName("이미 알림이 전송되었거나 비활성 회원인 경우 알림 발송을 스킵한다")
    void syncCouponNotifications_alreadySent_skipsNotification() {
        LocalDateTime now = LocalDateTime.now(clock);
        CouponNotificationView coupon = new CouponNotificationView(11L, 1L, 3L, "생일 축하 쿠폰", now);
        given(couponNotificationQueryService.findRecentlyIssuedMemberCoupons(any(LocalDateTime.class)))
                .willReturn(List.of(coupon));
        given(notificationCouponQueryService.isCouponNotificationSentOrInactive(3L, 11L))
                .willReturn(true);

        couponNotificationSync.syncCouponNotifications();

        verify(notificationService, never()).makeNotification(any());
    }
}
