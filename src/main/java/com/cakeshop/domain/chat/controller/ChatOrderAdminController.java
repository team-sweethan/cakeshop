package com.cakeshop.domain.chat.controller;

import com.cakeshop.domain.chat.dto.view.ChatOrderRoomView;
import com.cakeshop.domain.chat.service.ChatOrderQueryService;
import com.cakeshop.global.security.MemberDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** 주문 목록에서 생성된 빈 채팅방을 포함해 관리자 채팅방을 재진입시키는 API다. */
@RestController
@RequiredArgsConstructor
public class ChatOrderAdminController {

    private final ChatOrderQueryService chatOrderQueryService;

    @GetMapping("/api/admin/chat/order/rooms/{chatRoomId}")
    public ResponseEntity<ChatOrderRoomView> findRoom(
            @PathVariable("chatRoomId") Long chatRoomId,
            @AuthenticationPrincipal MemberDetails memberDetails
    ) {
        if (memberDetails == null || !memberDetails.isAdmin()) {
            return ResponseEntity.status(403).build();
        }

        return chatOrderQueryService.findRoomById(chatRoomId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
