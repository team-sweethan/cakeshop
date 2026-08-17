package com.cakeshop.global.config;

import com.cakeshop.domain.chat.service.ChatService;
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

    private final ChatService chatService;

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
                if (accessor != null) {
                    StompCommand command = accessor.getCommand();
                    String destination = accessor.getDestination();
                    Principal principal = accessor.getUser();

                    // A. 클라이언트가 /topic/이나 /queue/로 직접 SEND하는 행위 차단 및 정지 회원 발신 거부
                    if (StompCommand.SEND.equals(command) && destination != null) {
                        if (destination.startsWith("/topic/") || destination.startsWith("/queue/")) {
                            throw new AccessDeniedException("브로커 목적지로 직접 메시지를 발신할 수 없습니다.");
                        }
                        if (principal instanceof Authentication auth && auth.getPrincipal() instanceof MemberDetails memberDetails) {
                            if (!memberDetails.isAdmin() && !chatService.existsCustomer(memberDetails.getMemberId())) {
                                throw new AccessDeniedException("정지되었거나 유효하지 않은 회원은 메시지를 발신할 수 없습니다.");
                            }
                        }
                    }

                    // B. 토픽 구독(SUBSCRIBE) 권한 및 보안 검증 (허용 목록 이외 상위 와일드카드 /topic/** 전체 차단)
                    if (StompCommand.SUBSCRIBE.equals(command) && destination != null) {
                        // B-1. 관리자 전용 토픽(/topic/admin/**) 구독은 ADMIN 권한만 허용
                        if (destination.startsWith("/topic/admin/")) {
                            if (!(principal instanceof Authentication auth
                                    && auth.getPrincipal() instanceof MemberDetails memberDetails
                                    && memberDetails.isAdmin())) {
                                throw new AccessDeniedException("관리자만 해당 토픽을 구독할 수 있습니다.");
                            }
                        }
                        // B-2. 채팅방 토픽(/topic/chat/**) 구독은 방 소유자 및 활성 회원 검증 (와일드카드 패턴 차단)
                        else if (destination.startsWith("/topic/chat/")) {
                            String subPath = destination.substring("/topic/chat/".length());
                            String[] parts = subPath.split("/");
                            if (parts.length > 0 && !parts[0].isBlank()) {
                                try {
                                    Long roomId = Long.parseLong(parts[0]);
                                    if (principal instanceof Authentication auth && auth.getPrincipal() instanceof MemberDetails memberDetails) {
                                        chatService.validateSubscribeAccess(
                                                roomId,
                                                memberDetails.getMemberId(),
                                                memberDetails.isAdmin()
                                        );
                                    }
                                } catch (NumberFormatException e) {
                                    throw new AccessDeniedException("올바르지 않은 채팅방 구독 목적지입니다.");
                                }
                            } else {
                                throw new AccessDeniedException("올바르지 않은 채팅방 구독 목적지입니다.");
                            }
                        }
                        // B-3. 개인 알림 토픽(/topic/notifications/{memberId}) 구독은 본인 또는 관리자 검증
                        else if (destination.startsWith("/topic/notifications/")) {
                            String subPath = destination.substring("/topic/notifications/".length());
                            try {
                                Long targetMemberId = Long.parseLong(subPath);
                                if (principal instanceof Authentication auth && auth.getPrincipal() instanceof MemberDetails memberDetails) {
                                    if (!memberDetails.getMemberId().equals(targetMemberId) && !memberDetails.isAdmin()) {
                                        throw new AccessDeniedException("본인의 알림 토픽만 구독할 수 있습니다.");
                                    }
                                }
                            } catch (NumberFormatException e) {
                                throw new AccessDeniedException("올바르지 않은 알림 구독 목적지입니다.");
                            }
                        }
                        // B-4. 그 외 허용되지 않은 상위 와일드카드(/topic/**, /topic/* 등) 구독 전면 차단
                        else {
                            throw new AccessDeniedException("허용되지 않은 구독 목적지입니다.");
                        }
                    }
                }
                return message;
            }
        });
    }
}