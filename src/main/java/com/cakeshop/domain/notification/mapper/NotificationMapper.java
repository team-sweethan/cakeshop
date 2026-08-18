package com.cakeshop.domain.notification.mapper;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.cakeshop.domain.notification.dto.view.NotificationResponse;
import com.cakeshop.domain.notification.entity.Notification;
import com.cakeshop.domain.notification.entity.NotificationDelivery;

@Mapper
public interface NotificationMapper {
    // TODO: 조회·저장 메서드 — LIMIT #{size} OFFSET #{offset} 페이징 규칙 준수
    
    // 알림 DB에 저장
    void save(Notification notification);

    // 특정 회원 알림 목록 조회 (페이징 적용)
    List<NotificationResponse> findUserNotificationList(@Param("receiverId") Long receiverId,
                                                        @Param("size") int size,
                                                        @Param("offset") int offset);

    // 특정 회원 알림 1건 읽음 처리
    void markAsRead(@Param("id") Long id, @Param("receiverId") Long receiverId);

    // 특정 회원 알림 전체 읽음 처리
    void markAllAsRead(Long receiverId);

    // 특정 회원의 안 읽은 알림 개수 조회
    int countUnreadNotifications(Long receiverId);

    // 중복 event_key 존재 여부
    boolean existsByReceiverIdAndEventKey(@Param("receiverId") Long receiverId, @Param("eventKey") String eventKey);

    // 중복 event_key 기반 기존 알림 객체 조회
    Notification findNotificationByReceiverAndEventKey(@Param("receiverId") Long receiverId, @Param("eventKey") String eventKey);

    // 중복 event_key 기반 기존 알림 비관적 잠금 조회 (FOR UPDATE - 동시성 중복 발송 방어)
    Notification findNotificationByReceiverAndEventKeyForUpdate(@Param("receiverId") Long receiverId, @Param("eventKey") String eventKey);

    // 중복 event_key 기반 최근 시각 갱신 및 unread 처리
    void updateLastEventAtAndUnread(@Param("receiverId") Long receiverId,
                                    @Param("eventKey") String eventKey,
                                    @Param("title") String title,
                                    @Param("content") String content,
                                    @Param("lastEventAt") LocalDateTime lastEventAt);

    // 중복 event_key 기반 기존 알림 ID 조회
    Long findIdByReceiverIdAndEventKey(@Param("receiverId") Long receiverId, @Param("eventKey") String eventKey);

    // 이미 SENT 성공 발송 이력이 있는지 확인
    boolean hasSentDelivery(@Param("notificationId") Long notificationId);

    // 수신 회원 전화번호 조회 (주문서 작성 번호 우선, 알림톡 발송용)
    String findReceiverPhone(@Param("receiverId") Long receiverId, @Param("orderId") Long orderId); 

    // 문자로도 알림 보내기
    void saveDelivery(NotificationDelivery delivery);
}
