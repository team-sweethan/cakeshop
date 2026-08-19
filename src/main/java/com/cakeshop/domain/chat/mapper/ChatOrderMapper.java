package com.cakeshop.domain.chat.mapper;

import com.cakeshop.domain.chat.dto.view.ChatOrderRoomView;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 주문 도메인의 관리자 채팅방 재진입을 위한 조회 전용 계약이다. */
@Mapper
public interface ChatOrderMapper {

    Optional<ChatOrderRoomView> findRoomById(@Param("chatRoomId") Long chatRoomId);
}
