package com.cakeshop.domain.notification.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.cakeshop.domain.coupon.dto.view.CouponNotificationView;
import com.cakeshop.domain.coupon.service.CouponNotificationQueryService;
import com.cakeshop.domain.member.service.MemberNotificationQueryService;
import com.cakeshop.domain.notification.entity.DeliveryScope;
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
 * 기능 : 쿠폰 발급 알림 동기화 스케줄러 단위 테스트
 * 설명 : NotificationCouponSync가 신규 발급 쿠폰을 감지하고 알림을 정상 발송하며 예외 시 커서를 보호하는지 검증한다.
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
    @DisplayName("신규 발급된 쿠폰이 있고 활성 회원인 경우 COUPON 알림이 정상 발송된다")
    void syncCouponNotifications_newCoupon_sendsNotification() {
        LocalDateTime now = LocalDateTime.now(clock);
        CouponNotificationView coupon = new CouponNotificationView(10L, 1L, 2L, "웰컴 10% 할인 쿠폰", now);
        given(couponNotificationQueryService.findRecentlyIssuedMemberCoupons(any(), any(), any(), anyInt()))
                .willReturn(List.of(coupon));
        given(memberNotificationQueryService.isMemberActive(2L)).willReturn(true);
        given(notificationCouponQueryService.isCouponNotificationSentOrInactive(2L, 10L))
                .willReturn(false);

        notificationCouponSync.syncCouponNotifications();

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
    @DisplayName("이미 알림이 전송 완료된 경우 알림 발송을 스킵한다")
    void syncCouponNotifications_alreadySent_skipsNotification() {
        LocalDateTime now = LocalDateTime.now(clock);
        CouponNotificationView coupon = new CouponNotificationView(11L, 1L, 3L, "생일 축하 쿠폰", now);
        given(couponNotificationQueryService.findRecentlyIssuedMemberCoupons(any(), any(), any(), anyInt()))
                .willReturn(List.of(coupon));
        given(memberNotificationQueryService.isMemberActive(3L)).willReturn(true);
        given(notificationCouponQueryService.isCouponNotificationSentOrInactive(3L, 11L))
                .willReturn(true);

        notificationCouponSync.syncCouponNotifications();

        verify(notificationService, never()).makeNotification(any());
    }

    @Test
    @DisplayName("특정 행에서 발송 오류 발생 시 루프를 중단하여 커서가 실패 행을 건너뛰지 않도록 방어한다")
    void syncCouponNotifications_errorOnRow_breaksLoop() {
        LocalDateTime now = LocalDateTime.now(clock);
        CouponNotificationView coupon1 = new CouponNotificationView(12L, 1L, 4L, "쿠폰1", now.minusMinutes(2));
        CouponNotificationView coupon2 = new CouponNotificationView(13L, 1L, 5L, "쿠폰2", now.minusMinutes(1));
        given(couponNotificationQueryService.findRecentlyIssuedMemberCoupons(any(), any(), any(), anyInt()))
                .willReturn(List.of(coupon1, coupon2));
        given(memberNotificationQueryService.isMemberActive(4L)).willReturn(true);
        given(notificationCouponQueryService.isCouponNotificationSentOrInactive(4L, 12L)).willReturn(false);

        doThrow(new RuntimeException("DB Connection Error"))
                .when(notificationService).makeNotification(any());

        notificationCouponSync.syncCouponNotifications();

        // 첫 번째 행에서 실패했으므로 두 번째 행은 처리 시도하지 않고 즉시 루프 중단
        verify(notificationService, times(1)).makeNotification(any());
        verify(memberNotificationQueryService, never()).isMemberActive(5L);
    }
}
