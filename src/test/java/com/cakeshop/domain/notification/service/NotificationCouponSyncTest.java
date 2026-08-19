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
 * 설명 : NotificationCouponSync가 사용 시작일이 도래한 신규 발급 쿠폰(COUPON), 만료 3일 전 쿠폰(COUPON_EXPIRING_SOON) 및 실패한 SMS 재시도를 정상 수행하는지 검증한다.
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
    @DisplayName("실패한 외부 SMS 발송 알림이 있고 쿠폰이 여전히 유효한 경우 자동으로 retrySmsForNotification이 호출된다")
    void syncCouponIssuance_retryFailedSms_retriesSuccessfully() {
        Notification failedNotification = Notification.builder()
                .id(100L)
                .receiverId(5L)
                .userCouponId(20L)
                .title("쿠폰 발급")
                .content("웰컴 쿠폰이 발급되었습니다.")
                .notificationType(NotificationType.COUPON)
                .deliveryScope(DeliveryScope.WEB_AND_SMS)
                .build();

        given(couponNotificationQueryService.findRecentlyIssuedMemberCoupons(any(), any(), any(), anyInt()))
                .willReturn(List.of());
        given(couponNotificationQueryService.findRecentlyStartedMemberCoupons(any(), any(), any(), anyInt()))
                .willReturn(List.of());
        given(notificationMapper.findRetryableCouponNotifications(anyInt()))
                .willReturn(List.of(failedNotification));
        given(memberNotificationQueryService.isMemberActive(5L)).willReturn(true);
        given(couponNotificationQueryService.isMemberCouponAvailableAndUnexpired(20L, null)).willReturn(true);

        notificationCouponSync.syncCouponIssuance();

        verify(notificationService).retrySmsForNotification(100L, 5L, "쿠폰 발급", "웰컴 쿠폰이 발급되었습니다.");
    }

    @Test
    @DisplayName("실패한 외부 SMS 알림의 쿠폰이 재시도 시점에 이미 사용(USED)되었으면 재시도를 스킵한다")
    void syncCouponIssuance_retryFailedSms_couponUsed_skipsRetry() {
        Notification failedNotification = Notification.builder()
                .id(101L)
                .receiverId(6L)
                .userCouponId(21L)
                .title("쿠폰 발급")
                .content("웰컴 쿠폰이 발급되었습니다.")
                .notificationType(NotificationType.COUPON)
                .deliveryScope(DeliveryScope.WEB_AND_SMS)
                .build();

        given(couponNotificationQueryService.findRecentlyIssuedMemberCoupons(any(), any(), any(), anyInt()))
                .willReturn(List.of());
        given(couponNotificationQueryService.findRecentlyStartedMemberCoupons(any(), any(), any(), anyInt()))
                .willReturn(List.of());
        given(notificationMapper.findRetryableCouponNotifications(anyInt()))
                .willReturn(List.of(failedNotification));
        given(memberNotificationQueryService.isMemberActive(6L)).willReturn(true);
        given(couponNotificationQueryService.isMemberCouponAvailableAndUnexpired(21L, null)).willReturn(false);

        notificationCouponSync.syncCouponIssuance();

        verify(notificationService, never()).retrySmsForNotification(any(), any(), any(), any());
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

    @Test
    @DisplayName("발송 직전 쿠폰이 이미 사용(USED)되어 비가용 상태가 된 경우 발급 알림 발송을 스킵한다")
    void syncCouponIssuance_couponNotAvailable_skipsNotification() {
        LocalDateTime now = LocalDateTime.now(clock);
        CouponNotificationView coupon = new CouponNotificationView(10L, 1L, 2L, "웰컴 10% 할인 쿠폰", now, now, now.plusDays(30));
        given(couponNotificationQueryService.findRecentlyIssuedMemberCoupons(any(), any(), any(), anyInt()))
                .willReturn(List.of(coupon));
        given(couponNotificationQueryService.findRecentlyStartedMemberCoupons(any(), any(), any(), anyInt()))
                .willReturn(List.of());
        given(memberNotificationQueryService.isMemberActive(2L)).willReturn(true);
        given(couponNotificationQueryService.isMemberCouponAvailableAndUnexpired(10L, coupon.expiresAt())).willReturn(false);

        notificationCouponSync.syncCouponIssuance();

        verify(notificationService, never()).makeNotification(any());
    }
}
