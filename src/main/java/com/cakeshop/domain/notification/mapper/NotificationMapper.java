package com.cakeshop.domain.notification.mapper;

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

    // 수신 회원 전화번호 조회 (알림톡/SMS 발송용)
    String findReceiverPhone(@Param("receiverId") Long receiverId);

    // 카카오 알림톡으로도 보내기
    void saveDelivery(NotificationDelivery delivery);
}
