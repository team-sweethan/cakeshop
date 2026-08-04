package com.cakeshop.domain.notification.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

// 고객 알림 목록 화면
@Controller
public class NotificationUserController {

    @GetMapping("/notifications")
    public String notifications() {
        return "customer/notification/list";
    }
}
