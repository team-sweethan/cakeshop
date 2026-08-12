package com.cakeshop.domain.chat.service;

import com.cakeshop.domain.chat.entity.ChatRoom;
import com.cakeshop.domain.chat.mapper.ChatMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ChatRoomTxHelper {

    private final ChatMapper chatMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ChatRoom findChatRoomInNewTransaction(Long customerId) {
        return chatMapper.findChatRoomByCustomerId(customerId);
    }
}
