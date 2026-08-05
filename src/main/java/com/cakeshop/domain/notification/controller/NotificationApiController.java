package com.cakeshop.domain.notification.controller;

import com.cakeshop.domain.notification.dto.view.NotificationResponse;
import com.cakeshop.domain.notification.service.NotificationService;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.security.MemberDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.cakeshop.domain.notification.dto.form.NotificationRequest;
import com.cakeshop.domain.notification.entity.NotificationType;
import com.cakeshop.domain.notification.entity.DeliveryScope;


import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationApiController {

    private final NotificationService notificationService;

    // 내 알림 목록 최신순 페이징 조회 API
    @GetMapping
    public ResponseEntity<List<NotificationResponse>> getNotifications(
        @AuthenticationPrincipal MemberDetails memberDetails, @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) 
    {
        if (memberDetails == null) {
            return ResponseEntity.ok(List.of());
        } // 로그인 안 했으면 빈 리스트 반환
        
        PageRequest pageRequest = new PageRequest(page, size); // 페이징 전용 계산기 클래스 사용
        List<NotificationResponse> list = notificationService.findUserNotificationList(
                memberDetails.getMemberId(), pageRequest.getSize(), pageRequest.getOffset()); // DB에서 알림 목록 조회
        return ResponseEntity.ok(list); // 화면에 알림 목록 JSON으로 반환
    }

    // 알림 1개 읽음 처리 API
    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(
            @PathVariable Long id,
            @AuthenticationPrincipal MemberDetails memberDetails) 
    {
        if (memberDetails != null) {
            notificationService.markAsRead(id, memberDetails.getMemberId());
        }
        return ResponseEntity.ok().build();
    }

    // 알림 전체 읽음 처리 API
    @PatchMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead(@AuthenticationPrincipal MemberDetails memberDetails) {
        if (memberDetails != null) {
            notificationService.markAllAsRead(memberDetails.getMemberId());
        }
        return ResponseEntity.ok().build();
    }

    // 안 읽은 알림 개수 조회 API
    @GetMapping("/unread-count")
    public ResponseEntity<Integer> getUnreadCount(@AuthenticationPrincipal MemberDetails memberDetails) {
        if (memberDetails == null) return ResponseEntity.ok(0);
        int unreadCount = notificationService.countUnreadNotifications(memberDetails.getMemberId());
        return ResponseEntity.ok(unreadCount);
    }

    private final org.springframework.core.env.Environment environment;

    // SMS 발송 테스트용 전용 API
    @GetMapping("/test-sms")
    public ResponseEntity<String> testSmsNotification(@AuthenticationPrincipal MemberDetails memberDetails) {
        if (environment != null && environment.acceptsProfiles(org.springframework.core.env.Profiles.of("prod"))) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN)
                    .body("운영 프로필(prod)에서는 테스트 API를 사용할 수 없습니다.");
        }
        if (memberDetails == null) {
            return ResponseEntity.status(401).body("로그인 후 접속해 주세요. (http://localhost:8080/login)");
        }
        try {
            // 풀 패키지 경로를 싹 지우고 단축 이름으로 깔끔하게 작성!
            notificationService.makeNotification(
                NotificationRequest.builder()
                    .receiverId(memberDetails.getMemberId())
                    .type(NotificationType.ORDER_PAID)
                    .args(new Object[]{"ORD-TEST-9999"})
                    .deliveryScope(DeliveryScope.WEB_AND_SMS)
                    .build()
            );
            return ResponseEntity.ok("테스트 알림이 성공적으로 전송되었습니다! 핸드폰을 확인해 보세요!");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("테스트 실패 상세 원인: " + e.getClass().getName() + " : " + e.getMessage());
        }
    }
}
