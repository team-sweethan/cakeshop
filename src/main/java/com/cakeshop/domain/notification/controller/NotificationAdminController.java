package com.cakeshop.domain.notification.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

// 관리자 알림 화면
@Controller
public class NotificationAdminController {

    @GetMapping("/admin/notifications")
    public String notifications() { return "admin/notification/list"; }
}
