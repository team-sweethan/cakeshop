package com.cakeshop.domain.notification.controller;

import com.cakeshop.domain.notification.dto.view.NotificationResponse;
import com.cakeshop.domain.notification.service.NotificationService;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.security.MemberDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationApiController {

    private final NotificationService notificationService;

    // 안 읽은 알림 개수 조회 API
    @GetMapping("/unread-count")
    public ResponseEntity<Integer> getUnreadCount(@AuthenticationPrincipal MemberDetails memberDetails) {
        if (memberDetails == null) return ResponseEntity.ok(0);
        int unreadCount = notificationService.countUnreadNotifications(memberDetails.getMemberId());
        return ResponseEntity.ok(unreadCount);
    }

    // 내 알림 목록 최신순 페이징 조회 API
    @GetMapping
    public ResponseEntity<List<NotificationResponse>> getNotifications(
            @AuthenticationPrincipal MemberDetails memberDetails,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (memberDetails == null) return ResponseEntity.ok(List.of());
        PageRequest pageRequest = new PageRequest(page, size);
        List<NotificationResponse> list = notificationService.findUserNotificationList(
                memberDetails.getMemberId(), pageRequest.getSize(), pageRequest.getOffset());
        return ResponseEntity.ok(list);
    }

    // 알림 1개 읽음 처리 API
    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(
            @PathVariable Long id,
            @AuthenticationPrincipal MemberDetails memberDetails) {
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
}
