package com.cakeshop.domain.chat.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.cakeshop.domain.chat.dto.view.ChatOrderRoomView;
import com.cakeshop.domain.chat.service.ChatOrderQueryService;
import com.cakeshop.domain.member.dto.view.MemberAuthenticationView;
import com.cakeshop.global.security.MemberDetails;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class ChatOrderAdminControllerTests {

    @Mock
    private ChatOrderQueryService chatOrderQueryService;

    @Test
    void findRoom_adminReceivesAuthoritativeRoomIdentity() {
        ChatOrderRoomView room = new ChatOrderRoomView(31L, 7L);
        when(chatOrderQueryService.findRoomById(31L)).thenReturn(Optional.of(room));
        ChatOrderAdminController controller = new ChatOrderAdminController(chatOrderQueryService);

        var response = controller.findRoom(31L, admin());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(room);
    }

    @Test
    void findRoom_nonAdminIsForbidden() {
        ChatOrderAdminController controller = new ChatOrderAdminController(chatOrderQueryService);

        var response = controller.findRoom(31L, customer());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private MemberDetails admin() {
        return member("ADMIN");
    }

    private MemberDetails customer() {
        return member("USER");
    }

    private MemberDetails member(String role) {
        return new MemberDetails(new MemberAuthenticationView(
                1L, "member@example.com", "password", role, true, "관리자"
        ));
    }
}
