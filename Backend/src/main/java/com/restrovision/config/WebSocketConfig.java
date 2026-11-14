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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import jakarta.servlet.http.Cookie;
import java.util.Map;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Configuration
@EnableWebSocketMessageBroker
@Order(Ordered.HIGHEST_PRECEDENCE + 99)
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private static final Logger log = LoggerFactory.getLogger(WebSocketConfig.class);

    private static final Set<String> GLOBAL_ALLOWED_DESTINATIONS = Set.of("/app/ping");
    private static final Pattern TENANT_APP_DESTINATION_PATTERN = Pattern.compile("^/app/restaurants/(\\d+)/(ping|heartbeat)$");
    private static final Pattern TENANT_TOPIC_DESTINATION_PATTERN = Pattern.compile("^/topic/restaurants/(\\d+)/([a-zA-Z0-9_-]+)$");

    public static final Map<String, Set<String>> TOPIC_ROLE_AUTHORIZATIONS = Map.of(
            "tables", Set.of("ROLE_OWNER", "ROLE_MANAGER", "ROLE_WAITER", "ROLE_CLEANING"),
            "orders", Set.of("ROLE_OWNER", "ROLE_MANAGER", "ROLE_WAITER", "ROLE_KITCHEN"),
            "tasks", Set.of("ROLE_OWNER", "ROLE_MANAGER", "ROLE_WAITER", "ROLE_CLEANING"),
            "alerts", Set.of("ROLE_OWNER", "ROLE_MANAGER", "ROLE_WAITER", "ROLE_KITCHEN", "ROLE_CLEANING")
    );

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
        HandshakeInterceptor interceptor = cookieHandshakeInterceptor();
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .addInterceptors(interceptor)
                .withSockJS();
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .addInterceptors(interceptor);
    }

    private HandshakeInterceptor cookieHandshakeInterceptor() {
        return new HandshakeInterceptor() {
            @Override
            public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                           WebSocketHandler wsHandler, Map<String, Object> attributes) {
                if (request instanceof ServletServerHttpRequest servletRequest) {
                    Cookie[] cookies = servletRequest.getServletRequest().getCookies();
                    if (cookies != null) {
                        for (Cookie cookie : cookies) {
                            if ("restro_access_token".equals(cookie.getName())) {
                                attributes.put("restro_access_token", cookie.getValue());
                                break;
                            }
                        }
                    }
                }
                return true;
            }

            @Override
            public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                       WebSocketHandler wsHandler, Exception exception) {}
        };
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
                    String token = null;
                    if (authHeader != null && authHeader.startsWith("Bearer ")) {
                        token = authHeader.substring(7).trim();
                    } else if (accessor.getSessionAttributes() != null && accessor.getSessionAttributes().containsKey("restro_access_token")) {
                        token = (String) accessor.getSessionAttributes().get("restro_access_token");
                    } else {
                        List<String> cookieHeaders = accessor.getNativeHeader("cookie");
                        if (cookieHeaders != null) {
                            for (String ch : cookieHeaders) {
                                for (String part : ch.split(";")) {
                                    String p = part.trim();
                                    if (p.startsWith("restro_access_token=")) {
                                        token = p.substring("restro_access_token=".length()).trim();
                                        break;
                                    }
                                }
                            }
                        }
                    }

                    if (token == null) {
                        log.warn("STOMP CONNECT rejected: Missing or invalid Authorization header or cookie");
                        throw new AuthenticationCredentialsNotFoundException("STOMP connection rejected: Missing or invalid credentials");
                    }

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
                    // 1. Require a valid authenticated principal
                    if (accessor.getUser() == null || !(accessor.getUser() instanceof Authentication auth) || auth.getName() == null || auth.getName().trim().isEmpty()) {
                        log.warn("Blocked unauthenticated WebSocket subscription attempt to {}", accessor.getDestination());
                        throw new AccessDeniedException("Access denied: Valid authenticated principal is required.");
                    }

                    if (!auth.isAuthenticated()) {
                        log.warn("Blocked unauthenticated WebSocket subscription attempt by unauthenticated principal: {}", auth.getName());
                        throw new AccessDeniedException("Access denied: Principal is not authenticated.");
                    }

                    if (accessor.getSessionAttributes() == null || accessor.getSessionAttributes().get("restaurantId") == null) {
                        log.warn("Blocked subscription with missing tenant context: {}", accessor.getDestination());
                        throw new AccessDeniedException("Access denied: Missing tenant context in session.");
                    }

                    // 2. Require at least one granted authority
                    if (auth.getAuthorities() == null || auth.getAuthorities().isEmpty()) {
                        log.warn("Blocked subscription: User {} has no granted authorities", auth.getName());
                        throw new AccessDeniedException("Access denied: Authenticated principal has no granted authorities.");
                    }

                    String destination = accessor.getDestination();
                    if (destination == null || destination.trim().isEmpty()) {
                        throw new AccessDeniedException("Access denied: Subscription destination cannot be null or empty.");
                    }

                    // Security: Reject encoded path separators or path traversal sequences
                    String lowerDest = destination.toLowerCase();
                    if (lowerDest.contains("%2f") || lowerDest.contains("%5c") || destination.contains("..") || destination.contains("\\")) {
                        log.warn("Blocked WebSocket SUBSCRIBE with encoded separators or path traversal: {}", destination);
                        throw new AccessDeniedException("Access denied: Encoded path separators or directory traversal sequences are prohibited.");
                    }

                    Matcher matcher = TENANT_TOPIC_DESTINATION_PATTERN.matcher(destination);
                    if (!matcher.matches()) {
                        log.warn("Blocked WebSocket SUBSCRIBE to unauthorized destination format: {}", destination);
                        throw new AccessDeniedException("Access denied: Unauthorized subscription destination.");
                    }

                    Long clientRestId = (Long) accessor.getSessionAttributes().get("restaurantId");
                    try {
                        Long targetRestId = Long.parseLong(matcher.group(1));
                        if (!clientRestId.equals(targetRestId)) {
                            log.warn("Blocked cross-tenant WebSocket subscription: client tenant {} attempted to subscribe to {}",
                                    clientRestId, destination);
                            throw new AccessDeniedException("Access denied: Cross-tenant subscription to restaurant " + targetRestId + " is prohibited.");
                        }

                        // 3. Resolve the requested topic from the explicit allowlist
                        String subtopic = matcher.group(2);
                        Set<String> allowedRoles = TOPIC_ROLE_AUTHORIZATIONS.get(subtopic);
                        if (allowedRoles == null) {
                            log.warn("Blocked subscription to unapproved same-tenant topic: {}", destination);
                            throw new AccessDeniedException("Access denied: Destination '" + destination + "' is not a permitted application topic.");
                        }

                        // 4. Require an intersection between the principal's authorities and that topic's allowed roles
                        boolean hasRole = auth.getAuthorities().stream()
                                .map(GrantedAuthority::getAuthority)
                                .anyMatch(allowedRoles::contains);

                        // 5. Reject the subscription if any check fails
                        if (!hasRole) {
                            log.warn("Blocked subscription: User {} lacks required role for topic {}",
                                    auth.getName(), destination);
                            throw new AccessDeniedException("Access denied: User lacks authorized role for destination " + destination);
                        }
                    } catch (NumberFormatException ex) {
                        throw new AccessDeniedException("Access denied: Malformed restaurant topic destination.");
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

                    // Security: Reject encoded path separators or path traversal sequences
                    String lowerDest = destination.toLowerCase();
                    if (lowerDest.contains("%2f") || lowerDest.contains("%5c") || destination.contains("..") || destination.contains("\\")) {
                        log.warn("Blocked WebSocket SEND with encoded separators or path traversal: {}", destination);
                        throw new AccessDeniedException("Access denied: Encoded path separators or directory traversal sequences are prohibited.");
                    }

                    // Reject client-originated SEND operations to all broker-managed /topic/** destinations
                    if (destination.startsWith("/topic/") || destination.equals("/topic")) {
                        log.warn("Blocked client-originated WebSocket SEND to broadcast topic: {}", destination);
                        throw new AccessDeniedException("Access denied: Direct publishing to broadcast topics is prohibited.");
                    }

                    // Must start with /app/
                    if (!destination.startsWith("/app/")) {
                        log.warn("Blocked WebSocket SEND to unsupported destination prefix: {}", destination);
                        throw new AccessDeniedException("Access denied: Unsupported destination prefix for SEND. Must start with /app/.");
                    }

                    // Explicit allowlist evaluation
                    Long clientRestId = (Long) accessor.getSessionAttributes().get("restaurantId");
                    if (GLOBAL_ALLOWED_DESTINATIONS.contains(destination)) {
                        return message;
                    }

                    Matcher matcher = TENANT_APP_DESTINATION_PATTERN.matcher(destination);
                    if (matcher.matches()) {
                        try {
                            Long targetRestId = Long.parseLong(matcher.group(1));
                            if (!clientRestId.equals(targetRestId)) {
                                log.warn("Blocked cross-tenant WebSocket SEND: client tenant {} attempted to send to {}",
                                        clientRestId, destination);
                                throw new AccessDeniedException("Access denied: Cross-tenant message dispatch to restaurant " + targetRestId + " is prohibited.");
                            }
                            return message;
                        } catch (NumberFormatException ex) {
                            log.warn("Blocked malformed tenant ID in WebSocket SEND destination: {}", destination);
                            throw new AccessDeniedException("Access denied: Malformed restaurant ID in destination.");
                        }
                    }

                    // Reject unknown destinations by default
                    log.warn("Blocked WebSocket SEND to unauthorized/unknown destination: {}", destination);
                    throw new AccessDeniedException("Access denied: Destination '" + destination + "' is not an authorized client SEND endpoint.");
                }

                return message;
            }
        };
    }
}
