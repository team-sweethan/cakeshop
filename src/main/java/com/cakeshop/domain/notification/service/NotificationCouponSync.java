package com.cakeshop.domain.notification.service;

import com.cakeshop.domain.coupon.dto.view.CouponNotificationView;
import com.cakeshop.domain.coupon.service.CouponNotificationQueryService;
import com.cakeshop.domain.member.service.MemberNotificationQueryService;
import com.cakeshop.domain.notification.dto.form.NotificationRequest;
import com.cakeshop.domain.notification.entity.DeliveryScope;
import com.cakeshop.domain.notification.entity.Notification;
import com.cakeshop.domain.notification.entity.NotificationType;
import com.cakeshop.domain.notification.mapper.NotificationMapper;
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
 * 기능 : 쿠폰 발급(4초 주기), 만료 임박(1분 주기), 실패 SMS 재시도(30초 주기) 감지 & 알림 동기화 스케줄러
 * 설명 : 타 도메인 코드를 직접 수정하지 않고, 쿠폰 공개 Service로부터 데이터를 조회하여
 *        사용 시작일이 도래한 신규 발급 쿠폰(COUPON) 및 만료 3일 전 쿠폰(COUPON_EXPIRING_SOON)에 대해 실시간 웹 토스트 및 문자 알림을 발송한다.
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
    private final NotificationDeliveryService notificationDeliveryService;
    private final NotificationMapper notificationMapper;
    private final Clock clock;

    private final AtomicBoolean isIssuanceRunning = new AtomicBoolean(false);
    private final AtomicBoolean isExpirationRunning = new AtomicBoolean(false);
    private final AtomicBoolean isRetryRunning = new AtomicBoolean(false);

    // 발급 동기화용 커서
    private LocalDateTime lastIssuanceSyncTime;
    private Long lastIssuanceProcessedMemberCouponId;

    // 시작일 도래 동기화용 커서
    private LocalDateTime lastStartsSyncTime;
    private Long lastStartsProcessedMemberCouponId;

    // 만료 동기화용 실행 간 커서
    private LocalDateTime persistentCursorExpiresAt;
    private Long persistentCursorMemberCouponId;

    /**
     * 1. 사용 시작일이 도래한 유효 발급 쿠폰 실시간 알림 발송 (4초 주기 - 실시간 토스트 보장)
     */
    @Async
    @Scheduled(fixedDelay = 4000)
    public void syncCouponIssuance() {
        if (!isIssuanceRunning.compareAndSet(false, true)) {
            log.trace("이전 쿠폰 발급 알림 동기화 배치가 실행 중이므로 이번 스케줄 조회를 직렬화(스킵)합니다.");
            return;
        }

        try {
            LocalDateTime now = LocalDateTime.now(clock);
            if (lastIssuanceSyncTime == null) {
                LocalDateTime latestNotificationTime = notificationMapper.findLatestCouponNotificationCreatedAt();
                if (latestNotificationTime != null) {
                    lastIssuanceSyncTime = latestNotificationTime.minusSeconds(10);
                } else {
                    lastIssuanceSyncTime = now;
                }
            }
            if (lastStartsSyncTime == null) {
                lastStartsSyncTime = lastIssuanceSyncTime;
            }

            // 1-1. 신규 발급 쿠폰 처리 (idx_member_coupons_issued_at_id 인덱스 기반)
            syncNewlyIssuedCoupons(now);

            // 1-2. 사용 시작일 도래 쿠폰 처리 (idx_coupons_status_starts_expires 인덱스 기반)
            syncStartedCoupons(now);

        } catch (Exception e) {
            log.error("쿠폰 발급 알림 동기화 배치 실행 중 예외 발생 (errorType={}):", e.getClass().getSimpleName());
        } finally {
            isIssuanceRunning.set(false);
        }
    }

    private void syncNewlyIssuedCoupons(LocalDateTime now) {
        int loopCount = 0;
        final int MAX_LOOPS_PER_RUN = 10;
        final int PAGE_SIZE = 100;

        // 지연 커밋(Out-of-order) 트랜잭션을 포괄하기 위한 10초 안전 윈도우
        LocalDateTime querySince = lastIssuanceSyncTime.minusSeconds(10);
        LocalDateTime currentCursorTime = lastIssuanceSyncTime;
        Long currentCursorId = lastIssuanceProcessedMemberCouponId;

        while (loopCount < MAX_LOOPS_PER_RUN) {
            loopCount++;

            List<CouponNotificationView> issuedCoupons = couponNotificationQueryService.findRecentlyIssuedMemberCoupons(
                    querySince, currentCursorTime, currentCursorId, PAGE_SIZE
            );

            if (issuedCoupons == null || issuedCoupons.isEmpty()) {
                break;
            }

            boolean hadErrorInBatch = false;

            for (CouponNotificationView coupon : issuedCoupons) {
                if (coupon.memberCouponId() == null || coupon.memberId() == null) {
                    continue;
                }

                Long memberId = coupon.memberId();
                Long memberCouponId = coupon.memberCouponId();
                LocalDateTime expiresAt = coupon.expiresAt();
                String couponName = (coupon.couponName() != null && !coupon.couponName().isBlank()) ? coupon.couponName() : "할인";

                try {
                    if (!memberNotificationQueryService.isMemberActive(memberId)) {
                        currentCursorTime = coupon.issuedAt();
                        currentCursorId = memberCouponId;
                        continue;
                    }

                    if (!couponNotificationQueryService.isMemberCouponAvailableAndUnexpired(memberCouponId, expiresAt)) {
                        currentCursorTime = coupon.issuedAt();
                        currentCursorId = memberCouponId;
                        continue;
                    }

                    if (!notificationCouponQueryService.isCouponIssuedNotificationCompletedOrInactive(memberId, memberCouponId)) {
                        notificationService.makeNotification(NotificationRequest.builder()
                                .receiverId(memberId)
                                .userCouponId(memberCouponId)
                                .type(NotificationType.COUPON)
                                .eventKey(NotificationType.COUPON.name() + ":" + memberId + ":" + memberCouponId)
                                .deliveryScope(DeliveryScope.WEB_AND_SMS)
                                .args(new Object[]{couponName})
                                .build());
                    }

                    currentCursorTime = coupon.issuedAt();
                    currentCursorId = memberCouponId;

                } catch (Exception e) {
                    log.error("신규 쿠폰 발급 알림 발송 중 오류 발생 (errorType={}):", e.getClass().getSimpleName());
                    hadErrorInBatch = true;
                    break;
                }
            }

            if (hadErrorInBatch || issuedCoupons.size() < PAGE_SIZE) {
                break;
            }
        }

        this.lastIssuanceSyncTime = currentCursorTime;
        this.lastIssuanceProcessedMemberCouponId = currentCursorId;
    }

    private void syncStartedCoupons(LocalDateTime now) {
        int loopCount = 0;
        final int MAX_LOOPS_PER_RUN = 10;
        final int PAGE_SIZE = 100;

        LocalDateTime querySince = lastStartsSyncTime.minusSeconds(10);
        LocalDateTime currentCursorTime = lastStartsSyncTime;
        Long currentCursorId = lastStartsProcessedMemberCouponId;

        while (loopCount < MAX_LOOPS_PER_RUN) {
            loopCount++;

            List<CouponNotificationView> startedCoupons = couponNotificationQueryService.findRecentlyStartedMemberCoupons(
                    querySince, currentCursorTime, currentCursorId, PAGE_SIZE
            );

            if (startedCoupons == null || startedCoupons.isEmpty()) {
                break;
            }

            boolean hadErrorInBatch = false;

            for (CouponNotificationView coupon : startedCoupons) {
                if (coupon.memberCouponId() == null || coupon.memberId() == null) {
                    continue;
                }

                Long memberId = coupon.memberId();
                Long memberCouponId = coupon.memberCouponId();
                LocalDateTime expiresAt = coupon.expiresAt();
                String couponName = (coupon.couponName() != null && !coupon.couponName().isBlank()) ? coupon.couponName() : "할인";

                try {
                    if (!memberNotificationQueryService.isMemberActive(memberId)) {
                        currentCursorTime = coupon.startsAt();
                        currentCursorId = memberCouponId;
                        continue;
                    }

                    if (!couponNotificationQueryService.isMemberCouponAvailableAndUnexpired(memberCouponId, expiresAt)) {
                        currentCursorTime = coupon.startsAt();
                        currentCursorId = memberCouponId;
                        continue;
                    }

                    if (!notificationCouponQueryService.isCouponIssuedNotificationCompletedOrInactive(memberId, memberCouponId)) {
                        notificationService.makeNotification(NotificationRequest.builder()
                                .receiverId(memberId)
                                .userCouponId(memberCouponId)
                                .type(NotificationType.COUPON)
                                .eventKey(NotificationType.COUPON.name() + ":" + memberId + ":" + memberCouponId)
                                .deliveryScope(DeliveryScope.WEB_AND_SMS)
                                .args(new Object[]{couponName})
                                .build());
                    }

                    currentCursorTime = coupon.startsAt();
                    currentCursorId = memberCouponId;

                } catch (Exception e) {
                    log.error("쿠폰 시작 도래 알림 발송 중 오류 발생 (errorType={}):", e.getClass().getSimpleName());
                    hadErrorInBatch = true;
                    break;
                }
            }

            if (hadErrorInBatch || startedCoupons.size() < PAGE_SIZE) {
                break;
            }
        }

        this.lastStartsSyncTime = currentCursorTime;
        this.lastStartsProcessedMemberCouponId = currentCursorId;
    }

    /**
     * 2. 실패한 외부 SMS 발송 건 자동 재시도 (30초 주기 - 인덱스 및 커서 페이징 활용)
     */
    @Async
    @Scheduled(fixedDelay = 30000)
    public void retryFailedCouponSms() {
        if (!isRetryRunning.compareAndSet(false, true)) {
            return;
        }

        try {
            Long lastRetryId = null;
            final int BATCH_SIZE = 50;

            for (int loop = 0; loop < 5; loop++) {
                List<Notification> retryableList = notificationMapper.findRetryableCouponNotifications(lastRetryId, BATCH_SIZE);
                if (retryableList == null || retryableList.isEmpty()) {
                    break;
                }

                for (Notification notification : retryableList) {
                    lastRetryId = notification.getId();
                    if (notification.getId() == null || notification.getReceiverId() == null) continue;

                    // 회원 비활성 시 종결 처리 (큐 차단 방지)
                    if (!memberNotificationQueryService.isMemberActive(notification.getReceiverId())) {
                        notificationDeliveryService.recordSkippedAttempt(notification.getId(), "MEMBER_INACTIVE");
                        continue;
                    }

                    Long userCouponId = notification.getUserCouponId();
                    if (userCouponId != null) {
                        LocalDateTime targetExpiresAt = null;
                        // 만료 임박 알림인 경우 eventKey에서 당시 만료 시각 추출하여 일치 여부 검증 (만료일 연장 시 구 알림 재전송 차단)
                        if (notification.getNotificationType() == NotificationType.COUPON_EXPIRING_SOON
                                && notification.getEventKey() != null) {
                            String[] parts = notification.getEventKey().split(":");
                            if (parts.length >= 4) {
                                try {
                                    targetExpiresAt = LocalDateTime.parse(parts[3], EXPIRE_KEY_FORMAT);
                                } catch (Exception ignored) {
                                }
                            }
                        }

                        // 쿠폰이 사용/예약/비활성화되었거나 만료일이 연장되어 불일치하는 경우 종결 처리
                        if (!couponNotificationQueryService.isMemberCouponAvailableAndUnexpired(userCouponId, targetExpiresAt)) {
                            notificationDeliveryService.recordSkippedAttempt(notification.getId(), "COUPON_UNAVAILABLE_OR_EXTENDED");
                            continue;
                        }
                    }

                    notificationService.retrySmsForNotification(
                            notification.getId(),
                            notification.getReceiverId(),
                            notification.getTitle(),
                            notification.getContent()
                    );
                }

                if (retryableList.size() < BATCH_SIZE) {
                    break;
                }
            }
        } catch (Exception e) {
            log.error("실패한 쿠폰 알림 SMS 재시도 중 예외 발생 (errorType={}):", e.getClass().getSimpleName());
        } finally {
            isRetryRunning.set(false);
        }
    }

    /**
     * 3. 만료 3일 이내 유효 쿠폰 만료 임박 알림 발송 (1분 주기 - DB 부하 제로 최적화)
     */
    @Async
    @Scheduled(fixedDelay = 60000)
    public void syncCouponExpiration() {
        if (!isExpirationRunning.compareAndSet(false, true)) {
            log.trace("이전 쿠폰 만료 알림 동기화 배치가 실행 중이므로 이번 스케줄 조회를 직렬화(스킵)합니다.");
            return;
        }

        try {
            int loopCount = 0;
            final int MAX_LOOPS_PER_RUN = 10;
            final int PAGE_SIZE = 100;

            LocalDateTime currentCursorTime = this.persistentCursorExpiresAt;
            Long currentCursorId = this.persistentCursorMemberCouponId;
            boolean reachedEnd = false;

            while (loopCount < MAX_LOOPS_PER_RUN) {
                loopCount++;

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
                        if (!memberNotificationQueryService.isMemberActive(memberId)) {
                            currentCursorTime = expiresAt;
                            currentCursorId = memberCouponId;
                            continue;
                        }

                        if (!couponNotificationQueryService.isMemberCouponAvailableAndUnexpired(memberCouponId, expiresAt)) {
                            currentCursorTime = expiresAt;
                            currentCursorId = memberCouponId;
                            continue;
                        }

                        if (!notificationCouponQueryService.isCouponExpiringNotificationCompletedOrInactive(memberId, memberCouponId, expiresAt)) {
                            String expireKey = EXPIRE_KEY_FORMAT.format(expiresAt);
                            String eventKey = NotificationType.COUPON_EXPIRING_SOON.name() + ":" + memberId + ":" + memberCouponId + ":" + expireKey;

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
                this.persistentCursorExpiresAt = null;
                this.persistentCursorMemberCouponId = null;
            } else {
                this.persistentCursorExpiresAt = currentCursorTime;
                this.persistentCursorMemberCouponId = currentCursorId;
            }

        } catch (Exception e) {
            log.error("쿠폰 만료 알림 동기화 배치 실행 중 예외 발생 (errorType={}):", e.getClass().getSimpleName());
        } finally {
            isExpirationRunning.set(false);
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
