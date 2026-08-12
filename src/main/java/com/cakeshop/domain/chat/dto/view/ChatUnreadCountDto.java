package com.cakeshop.domain.chat.dto.view;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChatUnreadCountDto {
    private Long chatRoomId;
    private int unreadCount;
}
