package com.cakeshop.domain.chat.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cakeshop.global.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = ChatController.class,
        properties = "app.mockup.public-preview=true"
)
@Import(SecurityConfig.class)
class ChatControllerSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void chat_anonymousUser_redirectsToLoginEvenInPublicPreview() throws Exception {
        mockMvc.perform(get("/chat"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }
}
