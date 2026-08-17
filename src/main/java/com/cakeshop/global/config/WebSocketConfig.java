package com.cakeshop.global.config;

import com.cakeshop.domain.chat.entity.ChatRoom;
import com.cakeshop.domain.chat.mapper.ChatMapper;
import com.cakeshop.global.security.MemberDetails;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final ChatMapper chatMapper;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app"); // /app은 컨트롤러로 보냄
        registry.enableSimpleBroker("/topic", "/queue"); // 이 2개는 simple broker가 관리
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor != null && StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                    String destination = accessor.getDestination();
                    Principal principal = accessor.getUser();
                    if (destination != null && destination.startsWith("/topic/chat/")) {
                        String subPath = destination.substring("/topic/chat/".length());
                        String[] parts = subPath.split("/");
                        if (parts.length > 0 && !parts[0].isBlank()) {
                            try {
                                Long roomId = Long.parseLong(parts[0]);
                                if (principal instanceof Authentication auth && auth.getPrincipal() instanceof MemberDetails memberDetails) {
                                    if (!memberDetails.isAdmin()) {
                                        ChatRoom room = chatMapper.findChatRoomById(roomId);
                                        if (room == null || !memberDetails.getMemberId().equals(room.getCustomerId())) {
                                            throw new AccessDeniedException("해당 채팅방에 대한 구독 권한이 없습니다.");
                                        }
                                    }
                                }
                            } catch (NumberFormatException e) {
                                // 파싱 불가 시 무시
                            }
                        }
                    }
                }
                return message;
            }
        });
    }
}