package com.cakeshop.domain.notification.controller;

import com.cakeshop.domain.notification.dto.view.NotificationResponse;
import com.cakeshop.domain.notification.service.NotificationService;
import com.cakeshop.domain.store.service.StoreService;
import com.cakeshop.global.security.MemberDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

// 고객 알림 화면
@Controller
@RequiredArgsConstructor
public class NotificationUserController {

    private final NotificationService notificationService;
    private final StoreService storeService;

    @GetMapping("/notifications")
    public String list(@AuthenticationPrincipal MemberDetails memberDetails, Model model) {
        if (memberDetails != null) {
            List<NotificationResponse> notifications = notificationService.findUserNotificationList(
                    memberDetails.getMemberId(), 20, 0);
            model.addAttribute("notifications", notifications);
        }
        model.addAttribute("store", storeService.getPublicStore());
        return "customer/notification/list";
    }
}

