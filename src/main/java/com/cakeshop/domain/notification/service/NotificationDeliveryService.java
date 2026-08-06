package com.cakeshop.domain.notification.service;

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
