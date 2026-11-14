package com.restrovision.config;

import com.restrovision.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
@EnableWebSocketMessageBroker
@Order(Ordered.HIGHEST_PRECEDENCE + 99)
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private static final Logger log = LoggerFactory.getLogger(WebSocketConfig.class);

    private final JwtTokenProvider tokenProvider;

    public WebSocketConfig(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null) return message;

                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    String authHeader = accessor.getFirstNativeHeader("Authorization");
                    if (authHeader != null && authHeader.startsWith("Bearer ")) {
                        String token = authHeader.substring(7);
                        if (tokenProvider.validateToken(token)) {
                            Claims claims = tokenProvider.getClaims(token);
                            Number restIdNum = (Number) claims.get("restaurantId");
                            Long restaurantId = restIdNum != null ? restIdNum.longValue() : 1L;

                            @SuppressWarnings("unchecked")
                            List<String> roles = (List<String>) claims.get("roles");
                            List<SimpleGrantedAuthority> authorities = roles == null ? Collections.emptyList() :
                                    roles.stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r)).collect(Collectors.toList());

                            UsernamePasswordAuthenticationToken auth =
                                    new UsernamePasswordAuthenticationToken(claims.getSubject(), null, authorities);
                            accessor.setUser(auth);
                            accessor.getSessionAttributes().put("restaurantId", restaurantId);
                            log.info("WebSocket STOMP connected for {} (Restaurant ID: {})", claims.getSubject(), restaurantId);
                        }
                    }
                } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                    String destination = accessor.getDestination();
                    Long clientRestId = accessor.getSessionAttributes() != null
                            ? (Long) accessor.getSessionAttributes().get("restaurantId") : null;

                    // P0 Multi-Tenancy Fix: Restrict cross-tenant topic snooping!
                    if (destination != null && destination.startsWith("/topic/restaurants/")) {
                        String[] parts = destination.split("/");
                        if (parts.length >= 4) {
                            try {
                                Long targetRestId = Long.parseLong(parts[3]);
                                if (clientRestId != null && !clientRestId.equals(targetRestId)) {
                                    log.warn("Blocked cross-tenant WebSocket subscription: client tenant {} attempted to subscribe to {}",
                                            clientRestId, destination);
                                    throw new AccessDeniedException("Access denied: Cannot subscribe to topics of another restaurant.");
                                }
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                }
                return message;
            }
        });
    }
}
