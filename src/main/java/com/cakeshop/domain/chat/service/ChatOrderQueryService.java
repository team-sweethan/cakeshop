package com.cakeshop.domain.chat.service;

import com.cakeshop.domain.chat.dto.view.ChatOrderRoomView;
import com.cakeshop.domain.chat.mapper.ChatOrderMapper;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 주문 도메인이 관리자 채팅방을 안전하게 재진입하도록 제공하는 조회 계약이다. */
@Service
@RequiredArgsConstructor
public class ChatOrderQueryService {

    private final ChatOrderMapper chatOrderMapper;

    @Transactional(readOnly = true)
    public Optional<ChatOrderRoomView> findRoomById(Long chatRoomId) {
        if (chatRoomId == null || chatRoomId <= 0) {
            return Optional.empty();
        }
        return chatOrderMapper.findRoomById(chatRoomId);
    }
}
