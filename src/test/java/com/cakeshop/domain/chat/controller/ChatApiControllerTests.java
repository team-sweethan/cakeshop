package com.cakeshop.domain.chat.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.cakeshop.domain.chat.dto.view.ChatRoomListResponse;
import com.cakeshop.domain.chat.service.ChatNotificationSender;
import com.cakeshop.domain.chat.service.ChatService;
import com.cakeshop.domain.product.service.ProductChatQueryService;
import com.cakeshop.global.security.MemberDetails;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@ExtendWith(MockitoExtension.class)
class ChatApiControllerTests {

    @Mock
    private ChatService chatService;

    @Mock
    private MemberDetails admin;

    @Test
    void getAdminChatRoom_existingEmptyRoom_returnsAuthoritativeCustomer() {
        ChatRoomListResponse room = ChatRoomListResponse.builder()
                .chatRoomId(41L)
                .customerId(9L)
                .build();
        when(admin.isAdmin()).thenReturn(true);
        when(chatService.getAdminChatRoomResponse(41L)).thenReturn(room);

        var response = controller().getAdminChatRoom(41L, admin);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isSameAs(room);
        assertThat(response.getBody().getCustomerId()).isEqualTo(9L);
    }

    private ChatApiController controller() {
        return new ChatApiController(
                chatService,
                org.mockito.Mockito.mock(ChatNotificationSender.class),
                org.mockito.Mockito.mock(ProductChatQueryService.class),
                org.mockito.Mockito.mock(SimpMessagingTemplate.class)
        );
    }
}
