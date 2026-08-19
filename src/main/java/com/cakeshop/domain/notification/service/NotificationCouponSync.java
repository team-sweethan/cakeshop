package com.cakeshop.domain.notification.service;

import com.cakeshop.domain.coupon.dto.view.CouponNotificationView;
import com.cakeshop.domain.coupon.service.CouponNotificationQueryService;
import com.cakeshop.domain.member.service.MemberNotificationQueryService;
import com.cakeshop.domain.notification.dto.form.NotificationRequest;
import com.cakeshop.domain.notification.entity.DeliveryScope;
import com.cakeshop.domain.notification.entity.NotificationType;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * ******************************
 * 작성자 : 김민정
 * 담당자 : 김민정
 * 작성일 : 2026-08-19
 * 기능 : 쿠폰 만료 임박(3일 전) 감지 및 알림 동기화 스케줄러 (알림 도메인 오케스트레이션)
 * 설명 : 타 도메인 코드를 직접 수정하지 않고, 쿠폰 공개 Service로부터 데이터를 조회하여
 *        현재 사용 가능하고 만료 3일 이내에 도달한 회원 쿠폰에 대해 실시간 웹 토스트 및 문자 알림을 발송한다.
 * ******************************
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationCouponSync {

    private static final DateTimeFormatter EXPIRE_KEY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    private final CouponNotificationQueryService couponNotificationQueryService;
    private final NotificationCouponQueryService notificationCouponQueryService;
    private final MemberNotificationQueryService memberNotificationQueryService;
    private final NotificationService notificationService;
    private final Clock clock;

    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    // 1,000건 초과 대량 대상 발생 시에도 다음 실행에서 이어서 처리하기 위한 실행 간 커서
    private LocalDateTime persistentCursorExpiresAt;
    private Long persistentCursorMemberCouponId;

    @Async
    @Scheduled(fixedDelay = 5000)
    public void syncCouponExpiringNotifications() {
        if (!isRunning.compareAndSet(false, true)) {
            log.trace("이전 쿠폰 만료 알림 동기화 배치가 실행 중이므로 이번 스케줄 조회를 직렬화(스킵)합니다.");
            return;
        }

        try {
            int loopCount = 0;
            final int MAX_LOOPS_PER_RUN = 10; // 1회 스케줄당 최대 1,000건까지 순회
            final int PAGE_SIZE = 100;

            LocalDateTime currentCursorTime = this.persistentCursorExpiresAt;
            Long currentCursorId = this.persistentCursorMemberCouponId;
            boolean reachedEnd = false;

            while (loopCount < MAX_LOOPS_PER_RUN) {
                loopCount++;

                // 만료 3일 이내인 유효 쿠폰 목록 복합 커서 조회
                List<CouponNotificationView> expiringCoupons = couponNotificationQueryService.findExpiringMemberCoupons(
                        3, currentCursorTime, currentCursorId, PAGE_SIZE
                );

                if (expiringCoupons == null || expiringCoupons.isEmpty()) {
                    reachedEnd = true;
                    break;
                }

                boolean hadErrorInBatch = false;

                for (CouponNotificationView coupon : expiringCoupons) {
                    if (coupon.memberCouponId() == null || coupon.memberId() == null || coupon.expiresAt() == null) {
                        continue;
                    }

                    Long memberId = coupon.memberId();
                    Long memberCouponId = coupon.memberCouponId();
                    LocalDateTime expiresAt = coupon.expiresAt();
                    String couponName = (coupon.couponName() != null && !coupon.couponName().isBlank()) ? coupon.couponName() : "할인";

                    try {
                        // 발송 직전 회원 활성 상태 재검증
                        if (!memberNotificationQueryService.isMemberActive(memberId)) {
                            currentCursorTime = expiresAt;
                            currentCursorId = memberCouponId;
                            continue;
                        }

                        // 이미 전송 완료(SENT/DELIVERED 또는 2회 상한)되었으면 스킵하고, 신규 또는 SMS 실패 재시도 대상은 발송
                        if (!notificationCouponQueryService.isCouponExpiringNotificationCompletedOrInactive(memberId, memberCouponId, expiresAt)) {
                            String expireKey = EXPIRE_KEY_FORMAT.format(expiresAt);
                            String eventKey = NotificationType.COUPON_EXPIRING_SOON.name() + ":" + memberId + ":" + memberCouponId + ":" + expireKey;

                            // 실제 남은 기간(Duration)에 맞게 정밀 포맷팅
                            String remainingText = calculateRemainingText(expiresAt);

                            notificationService.makeNotification(NotificationRequest.builder()
                                    .receiverId(memberId)
                                    .userCouponId(memberCouponId)
                                    .type(NotificationType.COUPON_EXPIRING_SOON)
                                    .eventKey(eventKey)
                                    .deliveryScope(DeliveryScope.WEB_AND_SMS)
                                    .args(new Object[]{couponName, remainingText})
                                    .build());
                        }

                        currentCursorTime = expiresAt;
                        currentCursorId = memberCouponId;

                    } catch (Exception e) {
                        log.error("쿠폰 만료 임박 알림 발송 중 오류 발생 (errorType={}):", e.getClass().getSimpleName());
                        hadErrorInBatch = true;
                        break;
                    }
                }

                if (hadErrorInBatch) {
                    break;
                }

                if (expiringCoupons.size() < PAGE_SIZE) {
                    reachedEnd = true;
                    break;
                }
            }

            if (reachedEnd) {
                // 모든 대상을 한 바퀴 다 순회했으면 다음 실행을 위해 커서를 처음으로 리셋
                this.persistentCursorExpiresAt = null;
                this.persistentCursorMemberCouponId = null;
            } else {
                // 1,000건 상한으로 끊긴 경우 다음 실행에서 이어서 처리하도록 커서 저장
                this.persistentCursorExpiresAt = currentCursorTime;
                this.persistentCursorMemberCouponId = currentCursorId;
            }

        } catch (Exception e) {
            log.error("쿠폰 만료 알림 동기화 배치 실행 중 예외 발생 (errorType={}):", e.getClass().getSimpleName());
        } finally {
            isRunning.set(false);
        }
    }

    private String calculateRemainingText(LocalDateTime expiresAt) {
        LocalDateTime now = LocalDateTime.now(clock);
        Duration duration = Duration.between(now, expiresAt);
        long totalHours = duration.toHours();

        if (totalHours >= 48) {
            long days = totalHours / 24;
            return days + "일";
        } else if (totalHours >= 24) {
            return "1일";
        } else if (totalHours >= 1) {
            return totalHours + "시간";
        } else if (duration.toMinutes() >= 1) {
            return duration.toMinutes() + "분";
        } else {
            return "곧";
        }
    }
}
