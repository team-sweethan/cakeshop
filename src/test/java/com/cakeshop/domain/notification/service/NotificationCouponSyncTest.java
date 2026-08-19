package com.cakeshop.domain.notification.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.cakeshop.domain.coupon.dto.view.CouponNotificationView;
import com.cakeshop.domain.coupon.service.CouponNotificationQueryService;
import com.cakeshop.domain.member.service.MemberNotificationQueryService;
import com.cakeshop.domain.notification.entity.DeliveryScope;
import com.cakeshop.domain.notification.entity.NotificationType;
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
 * 기능 : 쿠폰 만료 임박(3일 전) 알림 동기화 스케줄러 단위 테스트
 * 설명 : NotificationCouponSync가 만료 3일 이내 유효 쿠폰을 감지하고 알림을 정상 발송하는지 검증한다.
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

    @Spy
    private Clock clock = Clock.fixed(Instant.parse("2026-08-19T09:00:00Z"), ZoneId.of("Asia/Seoul"));

    @InjectMocks
    private NotificationCouponSync notificationCouponSync;

    @Test
    @DisplayName("만료 3일 이내인 유효 쿠폰이 있고 활성 회원인 경우 COUPON_EXPIRING_SOON 알림이 정상 발송된다")
    void syncCouponExpiringNotifications_expiringCoupon_sendsNotification() {
        LocalDateTime expiresAt = LocalDateTime.now(clock).plusDays(2);
        CouponNotificationView coupon = new CouponNotificationView(10L, 1L, 2L, "웰컴 10% 할인 쿠폰", expiresAt);
        given(couponNotificationQueryService.findExpiringMemberCoupons(anyInt(), any(), any(), anyInt()))
                .willReturn(List.of(coupon));
        given(memberNotificationQueryService.isMemberActive(2L)).willReturn(true);
        given(notificationCouponQueryService.isCouponExpiringNotificationSentOrInactive(2L, 10L, expiresAt))
                .willReturn(false);

        notificationCouponSync.syncCouponExpiringNotifications();

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

    @Test
    @DisplayName("이미 만료 임박 알림이 전송된 경우 알림 발송을 스킵한다")
    void syncCouponExpiringNotifications_alreadySent_skipsNotification() {
        LocalDateTime expiresAt = LocalDateTime.now(clock).plusDays(2);
        CouponNotificationView coupon = new CouponNotificationView(11L, 1L, 3L, "생일 축하 쿠폰", expiresAt);
        given(couponNotificationQueryService.findExpiringMemberCoupons(anyInt(), any(), any(), anyInt()))
                .willReturn(List.of(coupon));
        given(memberNotificationQueryService.isMemberActive(3L)).willReturn(true);
        given(notificationCouponQueryService.isCouponExpiringNotificationSentOrInactive(3L, 11L, expiresAt))
                .willReturn(true);

        notificationCouponSync.syncCouponExpiringNotifications();

        verify(notificationService, never()).makeNotification(any());
    }
}
