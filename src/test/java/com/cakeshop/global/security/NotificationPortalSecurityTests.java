package com.cakeshop.global.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cakeshop.domain.notification.controller.NotificationApiController;
import com.cakeshop.domain.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(NotificationApiController.class)
@Import(SecurityConfig.class)
class NotificationPortalSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void notificationList_adminRole_isAccessible() throws Exception {
        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void notificationReadAll_adminRole_isAccessible() throws Exception {
        mockMvc.perform(patch("/api/notifications/read-all").with(csrf()))
                .andExpect(status().isOk());
    }
}
