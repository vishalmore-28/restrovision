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
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.BadCredentialsException;
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
        registration.interceptors(createSecurityInterceptor());
    }

    public ChannelInterceptor createSecurityInterceptor() {
        return new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null) return message;

                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    String authHeader = accessor.getFirstNativeHeader("Authorization");
                    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                        log.warn("STOMP CONNECT rejected: Missing or invalid Authorization header");
                        throw new AuthenticationCredentialsNotFoundException("STOMP connection rejected: Missing or invalid Authorization header");
                    }

                    String token = authHeader.substring(7).trim();
                    if (!tokenProvider.validateToken(token)) {
                        log.warn("STOMP CONNECT rejected: Token validation failed or token expired");
                        throw new BadCredentialsException("STOMP connection rejected: Token validation failed or token expired");
                    }

                    Claims claims = tokenProvider.getClaims(token);
                    if ("REFRESH".equals(claims.get("type"))) {
                        log.warn("STOMP CONNECT rejected: Refresh token attempted for WebSocket authentication");
                        throw new BadCredentialsException("STOMP connection rejected: Refresh token cannot be used for WebSocket session");
                    }

                    Number restIdNum = (Number) claims.get("restaurantId");
                    if (restIdNum == null) {
                        log.warn("STOMP CONNECT rejected: Token missing restaurantId claim");
                        throw new AuthenticationCredentialsNotFoundException("STOMP connection rejected: Token missing restaurantId claim");
                    }
                    Long restaurantId = restIdNum.longValue();

                    @SuppressWarnings("unchecked")
                    List<String> roles = (List<String>) claims.get("roles");
                    List<SimpleGrantedAuthority> authorities = roles == null ? Collections.emptyList() :
                            roles.stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r)).collect(Collectors.toList());

                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(claims.getSubject(), null, authorities);
                    accessor.setUser(auth);
                    if (accessor.getSessionAttributes() != null) {
                        accessor.getSessionAttributes().put("restaurantId", restaurantId);
                        accessor.getSessionAttributes().put("userEmail", claims.getSubject());
                    }
                    log.info("WebSocket STOMP authenticated for {} (Restaurant ID: {})", claims.getSubject(), restaurantId);

                } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                    if (accessor.getUser() == null || accessor.getSessionAttributes() == null || accessor.getSessionAttributes().get("restaurantId") == null) {
                        log.warn("Blocked unauthenticated WebSocket subscription attempt to {}", accessor.getDestination());
                        throw new AccessDeniedException("Access denied: Unauthenticated subscription attempt.");
                    }

                    String destination = accessor.getDestination();
                    if (destination == null) {
                        throw new AccessDeniedException("Access denied: Subscription destination cannot be null.");
                    }

                    Long clientRestId = (Long) accessor.getSessionAttributes().get("restaurantId");
                    if (destination.startsWith("/topic/restaurants/")) {
                        String[] parts = destination.split("/");
                        if (parts.length >= 4) {
                            try {
                                Long targetRestId = Long.parseLong(parts[3]);
                                if (!clientRestId.equals(targetRestId)) {
                                    log.warn("Blocked cross-tenant WebSocket subscription: client tenant {} attempted to subscribe to {}",
                                            clientRestId, destination);
                                    throw new AccessDeniedException("Access denied: Cross-tenant subscription to restaurant " + targetRestId + " is prohibited.");
                                }
                            } catch (NumberFormatException ex) {
                                throw new AccessDeniedException("Access denied: Malformed restaurant topic destination.");
                            }
                        }
                    }

                } else if (StompCommand.SEND.equals(accessor.getCommand())) {
                    if (accessor.getUser() == null || accessor.getSessionAttributes() == null || accessor.getSessionAttributes().get("restaurantId") == null) {
                        log.warn("Blocked unauthenticated WebSocket SEND attempt to {}", accessor.getDestination());
                        throw new AccessDeniedException("Access denied: Unauthenticated message dispatch.");
                    }

                    String destination = accessor.getDestination();
                    if (destination == null || destination.trim().isEmpty()) {
                        log.warn("Blocked WebSocket SEND attempt with null or empty destination");
                        throw new AccessDeniedException("Access denied: SEND destination cannot be null or empty.");
                    }

                    // Reject client-originated SEND operations to broker-managed /topic/** destinations
                    if (destination.startsWith("/topic/") || destination.equals("/topic")) {
                        log.warn("Blocked client-originated WebSocket SEND to broadcast topic: {}", destination);
                        throw new AccessDeniedException("Access denied: Direct publishing to broadcast topics is prohibited.");
                    }

                    // Permit application messages through /app/** only
                    if (!destination.startsWith("/app/") && !destination.equals("/app")) {
                        log.warn("Blocked WebSocket SEND to unsupported destination: {}", destination);
                        throw new AccessDeniedException("Access denied: Unsupported destination prefix for SEND. Must start with /app/.");
                    }

                    // Enforce tenant isolation on tenant-scoped destinations (/app/restaurants/{id}/**)
                    Long clientRestId = (Long) accessor.getSessionAttributes().get("restaurantId");
                    if (destination.startsWith("/app/restaurants/")) {
                        String[] parts = destination.split("/");
                        if (parts.length >= 4) {
                            try {
                                Long targetRestId = Long.parseLong(parts[3]);
                                if (!clientRestId.equals(targetRestId)) {
                                    log.warn("Blocked cross-tenant WebSocket SEND: client tenant {} attempted to send to {}",
                                            clientRestId, destination);
                                    throw new AccessDeniedException("Access denied: Cross-tenant message dispatch to restaurant " + targetRestId + " is prohibited.");
                                }
                            } catch (NumberFormatException ex) {
                                log.warn("Blocked malformed tenant ID in WebSocket SEND destination: {}", destination);
                                throw new AccessDeniedException("Access denied: Malformed restaurant ID in destination.");
                            }
                        } else {
                            log.warn("Blocked malformed /app/restaurants/ path in WebSocket SEND: {}", destination);
                            throw new AccessDeniedException("Access denied: Malformed tenant destination path.");
                        }
                    }
                }

                return message;
            }
        };
    }
}
