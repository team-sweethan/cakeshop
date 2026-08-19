package com.cakeshop.domain.notification.service;

import java.time.LocalDateTime;
import com.cakeshop.domain.notification.entity.NotificationDelivery;
import com.cakeshop.domain.notification.mapper.NotificationMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 발송 이력 독립 트랜잭션 전용 서비스 (REQUIRES_NEW)
 */
@Service
@RequiredArgsConstructor
public class NotificationDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(NotificationDeliveryService.class);
    private final NotificationMapper notificationMapper;

    /**
     * 외부 SMS 전송 전, 시도 횟수를 원자적으로 예약(PENDING 상태로 사전 저장)한다.
     * 이미 성공(SENT)했거나 최대 시도 횟수에 도달한 경우 null을 반환한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long reserveDeliveryAttempt(Long notificationId, String recipient, int maxAttempts) {
        if (notificationId == null) return null;
        try {
            // 동일 알림에 대한 발송 시도 검사 및 삽입을 비관적 락(FOR UPDATE)으로 완벽하게 직렬화
            notificationMapper.findNotificationByIdForUpdate(notificationId);

            if (notificationMapper.hasSentDelivery(notificationId)) {
                return null;
            }
            if (notificationMapper.countDeliveryAttempts(notificationId) >= maxAttempts) {
                return null;
            }

            LocalDateTime now = LocalDateTime.now();
            NotificationDelivery delivery = NotificationDelivery.builder()
                    .notificationId(notificationId)
                    .recipient(recipient)
                    .templateCode("DEFAULT_SMS")
                    .status("PENDING")
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            notificationMapper.saveDelivery(delivery);
            return delivery.getId();
        } catch (Exception e) {
            log.error("SMS 발송 시도 사전 예약(PENDING) 실패 (notificationId: {})", notificationId, e);
            return null;
        }
    }

    /**
     * 외부 SMS 전송 결과를 업데이트한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateDeliveryResult(Long deliveryId, String status, String providerMessageId, String failureReason, LocalDateTime sentAt) {
        if (deliveryId == null) return;
        try {
            notificationMapper.updateDeliveryResult(deliveryId, status, providerMessageId, failureReason, sentAt, LocalDateTime.now());
        } catch (Exception e) {
            log.error("SMS 발송 결과 업데이트 실패 (deliveryId: {})", deliveryId, e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordDelivery(NotificationDelivery delivery) {
        try {
            notificationMapper.saveDelivery(delivery);
        } catch (Exception e) {
            log.error("notification_deliveries DB 저장 실패 (notificationId: {})", delivery != null ? delivery.getNotificationId() : null);
            throw e;
        }
    }
}
