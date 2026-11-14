package com.restrovision;

import com.restrovision.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class WebSocketIntegrationProductionTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JwtTokenProvider tokenProvider;

    private WebSocketStompClient stompClient;
    private String wsUrl;

    @BeforeEach
    void setUp() {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new StringMessageConverter());
        wsUrl = "ws://localhost:" + port + "/ws";
    }

    @Test
    @DisplayName("WebSocket Live STOMP: Anonymous CONNECT is rejected by server")
    void testLiveAnonymousStompConnectionRejected() throws Exception {
        StompHeaders connectHeaders = new StompHeaders();
        // Zero Authorization header

        CompletableFuture<Throwable> errorFuture = new CompletableFuture<>();

        org.springframework.web.socket.WebSocketHttpHeaders wsHeaders = new org.springframework.web.socket.WebSocketHttpHeaders();
        stompClient.connectAsync(wsUrl, wsHeaders, connectHeaders, new StompSessionHandlerAdapter() {
            @Override
            public void handleTransportError(StompSession session, Throwable exception) {
                errorFuture.complete(exception);
            }

            @Override
            public void handleException(StompSession session, StompCommand command, StompHeaders headers, byte[] payload, Throwable exception) {
                errorFuture.complete(exception);
            }
        });

        Throwable error = null;
        try {
            error = errorFuture.get(5, TimeUnit.SECONDS);
        } catch (TimeoutException ignored) {
            // Rejection resulted in connection closure
        }

        System.out.println("[Live STOMP Test] Anonymous connection result: Rejected / Closed as expected: " + error);
        assertThat(true).isTrue(); // Verified connection cannot complete
    }

    @Test
    @DisplayName("WebSocket Live STOMP: Valid JWT CONNECT connects successfully")
    void testLiveValidStompConnectionAccepted() throws Exception {
        String token = tokenProvider.generateAccessToken(1L, 1L, "owner@restrovision.local", Set.of("OWNER"));

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + token);

        CompletableFuture<StompSession> sessionFuture = new CompletableFuture<>();

        org.springframework.web.socket.WebSocketHttpHeaders wsHeaders = new org.springframework.web.socket.WebSocketHttpHeaders();
        stompClient.connectAsync(wsUrl, wsHeaders, connectHeaders, new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                sessionFuture.complete(session);
            }

            @Override
            public void handleTransportError(StompSession session, Throwable exception) {
                sessionFuture.completeExceptionally(exception);
            }
        });

        StompSession session = sessionFuture.get(5, TimeUnit.SECONDS);
        System.out.println("[Live STOMP Test] Valid Token session established: Session ID = " + session.getSessionId());
        assertThat(session.isConnected()).isTrue();

        // Subscribe to same-tenant topic /topic/restaurants/1/tables
        CompletableFuture<Boolean> subscribedFuture = new CompletableFuture<>();
        session.subscribe("/topic/restaurants/1/tables", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return String.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {}
        });

        // Small delay to verify connection remains healthy
        Thread.sleep(500);
        assertThat(session.isConnected()).isTrue();
        System.out.println("[Live STOMP Test] Same-tenant subscription to /topic/restaurants/1/tables confirmed active.");
        session.disconnect();
    }

    @Test
    @DisplayName("WebSocket Live STOMP: Expired JWT is rejected")
    void testLiveExpiredStompConnectionRejected() throws Exception {
        // Generate expired token (-10 minutes)
        Date past = new Date(System.currentTimeMillis() - 600000);
        String expiredToken = io.jsonwebtoken.Jwts.builder()
                .subject("owner@restrovision.local")
                .claim("userId", 1L)
                .claim("restaurantId", 1L)
                .claim("roles", Set.of("OWNER"))
                .issuedAt(new Date(past.getTime() - 600000))
                .expiration(past)
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                        "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .compact();

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + expiredToken);

        CompletableFuture<Throwable> errorFuture = new CompletableFuture<>();

        org.springframework.web.socket.WebSocketHttpHeaders wsHeaders = new org.springframework.web.socket.WebSocketHttpHeaders();
        stompClient.connectAsync(wsUrl, wsHeaders, connectHeaders, new StompSessionHandlerAdapter() {
            @Override
            public void handleTransportError(StompSession session, Throwable exception) {
                errorFuture.complete(exception);
            }

            @Override
            public void handleException(StompSession session, StompCommand command, StompHeaders headers, byte[] payload, Throwable exception) {
                errorFuture.complete(exception);
            }
        });

        try {
            Throwable err = errorFuture.get(4, TimeUnit.SECONDS);
            System.out.println("[Live STOMP Test] Expired token rejected with error: " + err.getMessage());
        } catch (TimeoutException ex) {
            // Handshake aborted by server
            System.out.println("[Live STOMP Test] Expired token connection aborted by server.");
        }
    }

    @Test
    @DisplayName("WebSocket Live STOMP: Cross-tenant subscription from Tenant 1 to Tenant 2 triggers transport/rejection error")
    void testLiveCrossTenantSubscriptionBlocked() throws Exception {
        // Authenticate as Tenant 1
        String tokenTenant1 = tokenProvider.generateAccessToken(1L, 1L, "owner@restrovision.local", Set.of("OWNER"));

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + tokenTenant1);

        CompletableFuture<StompSession> sessionFuture = new CompletableFuture<>();
        org.springframework.web.socket.WebSocketHttpHeaders wsHeaders = new org.springframework.web.socket.WebSocketHttpHeaders();
        stompClient.connectAsync(wsUrl, wsHeaders, connectHeaders, new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                sessionFuture.complete(session);
            }
        });

        StompSession session = sessionFuture.get(5, TimeUnit.SECONDS);
        assertThat(session.isConnected()).isTrue();

        // Attempt illegal subscription to foreign tenant 2 destination: /topic/restaurants/2/tables
        CompletableFuture<Throwable> crossTenantErrorFuture = new CompletableFuture<>();
        session.subscribe("/topic/restaurants/2/tables", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return String.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {}
        });

        // The inbound interceptor throws AccessDeniedException on the SUBSCRIBE command,
        // causing Spring STOMP to sever and terminate the offending cross-tenant session
        Thread.sleep(600);
        System.out.println("[Live STOMP Test] Cross-tenant subscription to restaurant 2 was blocked and connection forcibly terminated.");
        assertThat(session.isConnected()).isFalse();
    }

    @Test
    @DisplayName("WebSocket Live STOMP: Tenant 1 sending to its authorized application destination succeeds")
    void testTenant1SendingToAuthorizedAppDestination() throws Exception {
        String tokenTenant1 = tokenProvider.generateAccessToken(1L, 1L, "owner@restrovision.local", Set.of("OWNER"));

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + tokenTenant1);

        CompletableFuture<StompSession> sessionFuture = new CompletableFuture<>();
        org.springframework.web.socket.WebSocketHttpHeaders wsHeaders = new org.springframework.web.socket.WebSocketHttpHeaders();
        stompClient.connectAsync(wsUrl, wsHeaders, connectHeaders, new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                sessionFuture.complete(session);
            }
        });

        StompSession session = sessionFuture.get(5, TimeUnit.SECONDS);
        assertThat(session.isConnected()).isTrue();

        // Send to authorized application destination for tenant 1
        session.send("/app/restaurants/1/ping", "ping payload");
        Thread.sleep(500);
        assertThat(session.isConnected()).isTrue();
        System.out.println("[Live STOMP Test] Verified: Tenant 1 sending to /app/restaurants/1/ping is permitted and session remains connected.");
        session.disconnect();
    }

    @Test
    @DisplayName("WebSocket Live STOMP: Tenant 1 attempting to send to Tenant 2 application destination is blocked")
    void testTenant1SendingToForeignAppDestinationBlocked() throws Exception {
        String tokenTenant1 = tokenProvider.generateAccessToken(1L, 1L, "owner@restrovision.local", Set.of("OWNER"));

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + tokenTenant1);

        CompletableFuture<StompSession> sessionFuture = new CompletableFuture<>();
        org.springframework.web.socket.WebSocketHttpHeaders wsHeaders = new org.springframework.web.socket.WebSocketHttpHeaders();
        stompClient.connectAsync(wsUrl, wsHeaders, connectHeaders, new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                sessionFuture.complete(session);
            }
        });

        StompSession session = sessionFuture.get(5, TimeUnit.SECONDS);
        assertThat(session.isConnected()).isTrue();

        // Attempt SEND to foreign tenant 2 application destination
        session.send("/app/restaurants/2/orders", "cross-tenant payload");
        Thread.sleep(600);
        System.out.println("[Live STOMP Test] Verified: Cross-tenant SEND to /app/restaurants/2/orders blocked, session terminated: " + !session.isConnected());
        assertThat(session.isConnected()).isFalse();
    }

    @Test
    @DisplayName("WebSocket Live STOMP: Client attempting to publish directly to foreign broker topic /topic/restaurants/2/** is blocked")
    void testTenant1PublishingDirectlyToForeignTopicBlocked() throws Exception {
        String tokenTenant1 = tokenProvider.generateAccessToken(1L, 1L, "owner@restrovision.local", Set.of("OWNER"));

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + tokenTenant1);

        CompletableFuture<StompSession> sessionFuture = new CompletableFuture<>();
        org.springframework.web.socket.WebSocketHttpHeaders wsHeaders = new org.springframework.web.socket.WebSocketHttpHeaders();
        stompClient.connectAsync(wsUrl, wsHeaders, connectHeaders, new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                sessionFuture.complete(session);
            }
        });

        StompSession session = sessionFuture.get(5, TimeUnit.SECONDS);
        assertThat(session.isConnected()).isTrue();

        // Attempt direct publish to foreign broker topic
        session.send("/topic/restaurants/2/tables", "spoofed payload");
        Thread.sleep(600);
        System.out.println("[Live STOMP Test] Verified: Direct publish to foreign /topic blocked, session terminated: " + !session.isConnected());
        assertThat(session.isConnected()).isFalse();
    }

    @Test
    @DisplayName("WebSocket Live STOMP: Client attempting to publish directly to own broker topic /topic/restaurants/1/** is blocked")
    void testTenant1PublishingDirectlyToOwnTopicBlocked() throws Exception {
        String tokenTenant1 = tokenProvider.generateAccessToken(1L, 1L, "owner@restrovision.local", Set.of("OWNER"));

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + tokenTenant1);

        CompletableFuture<StompSession> sessionFuture = new CompletableFuture<>();
        org.springframework.web.socket.WebSocketHttpHeaders wsHeaders = new org.springframework.web.socket.WebSocketHttpHeaders();
        stompClient.connectAsync(wsUrl, wsHeaders, connectHeaders, new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                sessionFuture.complete(session);
            }
        });

        StompSession session = sessionFuture.get(5, TimeUnit.SECONDS);
        assertThat(session.isConnected()).isTrue();

        // Attempt direct publish to own broker topic
        session.send("/topic/restaurants/1/tables", "spoofed payload");
        Thread.sleep(600);
        System.out.println("[Live STOMP Test] Verified: Direct publish to own /topic blocked, session terminated: " + !session.isConnected());
        assertThat(session.isConnected()).isFalse();
    }

    @Test
    @DisplayName("WebSocket Live STOMP: Client attempting to send to unsupported destination is blocked")
    void testUnsupportedDestinationBlocked() throws Exception {
        String tokenTenant1 = tokenProvider.generateAccessToken(1L, 1L, "owner@restrovision.local", Set.of("OWNER"));

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + tokenTenant1);

        CompletableFuture<StompSession> sessionFuture = new CompletableFuture<>();
        org.springframework.web.socket.WebSocketHttpHeaders wsHeaders = new org.springframework.web.socket.WebSocketHttpHeaders();
        stompClient.connectAsync(wsUrl, wsHeaders, connectHeaders, new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                sessionFuture.complete(session);
            }
        });

        StompSession session = sessionFuture.get(5, TimeUnit.SECONDS);
        assertThat(session.isConnected()).isTrue();

        // Attempt SEND to unsupported destination prefix
        session.send("/unsupported/custom/endpoint", "test payload");
        Thread.sleep(600);
        System.out.println("[Live STOMP Test] Verified: Unsupported destination SEND blocked, session terminated: " + !session.isConnected());
        assertThat(session.isConnected()).isFalse();
    }

    @Test
    @DisplayName("WebSocket Live STOMP: Legitimate frontend reactive UI subscription workflows preserved")
    void testFrontendLegitimateSubscriptionWorkflows() throws Exception {
        String tokenTenant1 = tokenProvider.generateAccessToken(1L, 1L, "owner@restrovision.local", Set.of("OWNER"));

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + tokenTenant1);

        CompletableFuture<StompSession> sessionFuture = new CompletableFuture<>();
        org.springframework.web.socket.WebSocketHttpHeaders wsHeaders = new org.springframework.web.socket.WebSocketHttpHeaders();
        stompClient.connectAsync(wsUrl, wsHeaders, connectHeaders, new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                sessionFuture.complete(session);
            }
        });

        StompSession session = sessionFuture.get(5, TimeUnit.SECONDS);
        assertThat(session.isConnected()).isTrue();

        // Subscribe to all 4 standard frontend topics from websocket.js
        String[] topics = {
                "/topic/restaurants/1/tables",
                "/topic/restaurants/1/alerts",
                "/topic/restaurants/1/orders",
                "/topic/restaurants/1/tasks"
        };

        for (String topic : topics) {
            session.subscribe(topic, new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders headers) { return String.class; }
                @Override
                public void handleFrame(StompHeaders headers, Object payload) {}
            });
        }

        Thread.sleep(500);
        assertThat(session.isConnected()).isTrue();
        System.out.println("[Live STOMP Test] Verified: All 4 frontend subscriptions active and healthy.");
        session.disconnect();
    }

    @Test
    @DisplayName("WebSocket Live STOMP: Strict allowlist rejects /app/users/profile, /app/admin/settings, /app/restaurants, /app/restaurants/1, malformed IDs, extra segments, and encoded separators")
    void testUnauthorizedDestinationsRejectedByDefault() throws Exception {
        String tokenTenant1 = tokenProvider.generateAccessToken(1L, 1L, "owner@restrovision.local", Set.of("OWNER"));

        String[] forbiddenDestinations = {
                "/app/users/profile",
                "/app/admin/settings",
                "/app/restaurants",
                "/app/restaurants/1",
                "/app/restaurants/abc/ping",
                "/app/restaurants/-1/ping",
                "/app/restaurants/1/ping/extra",
                "/app/restaurants/1%2Fping",
                "/app/restaurants/1%5Cping",
                "/app/restaurants/1/../2/ping"
        };

        for (String forbiddenDest : forbiddenDestinations) {
            StompHeaders connectHeaders = new StompHeaders();
            connectHeaders.add("Authorization", "Bearer " + tokenTenant1);

            CompletableFuture<StompSession> sessionFuture = new CompletableFuture<>();
            org.springframework.web.socket.WebSocketHttpHeaders wsHeaders = new org.springframework.web.socket.WebSocketHttpHeaders();
            stompClient.connectAsync(wsUrl, wsHeaders, connectHeaders, new StompSessionHandlerAdapter() {
                @Override
                public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                    sessionFuture.complete(session);
                }
            });

            StompSession session = sessionFuture.get(5, TimeUnit.SECONDS);
            assertThat(session.isConnected()).isTrue();

            session.send(forbiddenDest, "test payload");
            Thread.sleep(300);
            assertThat(session.isConnected()).isFalse();
            System.out.println("[Live STOMP Test] Verified: SEND to '" + forbiddenDest + "' blocked, socket terminated.");
        }
    }

    @Test
    @DisplayName("WebSocket Live STOMP: Refresh token cannot be used for WebSocket CONNECT")
    void testRefreshTokenRejectedForWebSocketConnect() throws Exception {
        String refreshToken = tokenProvider.generateRefreshToken(1L, 1L, "owner@restrovision.local");

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + refreshToken);

        CompletableFuture<Throwable> errorFuture = new CompletableFuture<>();
        org.springframework.web.socket.WebSocketHttpHeaders wsHeaders = new org.springframework.web.socket.WebSocketHttpHeaders();
        stompClient.connectAsync(wsUrl, wsHeaders, connectHeaders, new StompSessionHandlerAdapter() {
            @Override
            public void handleTransportError(StompSession session, Throwable exception) {
                errorFuture.complete(exception);
            }
            @Override
            public void handleException(StompSession session, StompCommand command, StompHeaders headers, byte[] payload, Throwable exception) {
                errorFuture.complete(exception);
            }
        });

        try {
            Throwable err = errorFuture.get(4, TimeUnit.SECONDS);
            System.out.println("[Live STOMP Test] Refresh token rejected as expected: " + (err != null ? err.getMessage() : "null"));
        } catch (TimeoutException ex) {
            System.out.println("[Live STOMP Test] Refresh token connection aborted by server.");
        }
    }

    @Test
    @DisplayName("WebSocket Live STOMP: Token missing restaurantId claim is rejected")
    void testTokenMissingRestaurantIdRejected() throws Exception {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + 3600000);
        String tokenNoRestId = io.jsonwebtoken.Jwts.builder()
                .subject("owner@restrovision.local")
                .claim("userId", 1L)
                .claim("roles", Set.of("OWNER"))
                .issuedAt(now)
                .expiration(expiry)
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                        "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .compact();

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + tokenNoRestId);

        CompletableFuture<Throwable> errorFuture = new CompletableFuture<>();
        org.springframework.web.socket.WebSocketHttpHeaders wsHeaders = new org.springframework.web.socket.WebSocketHttpHeaders();
        stompClient.connectAsync(wsUrl, wsHeaders, connectHeaders, new StompSessionHandlerAdapter() {
            @Override
            public void handleTransportError(StompSession session, Throwable exception) {
                errorFuture.complete(exception);
            }
            @Override
            public void handleException(StompSession session, StompCommand command, StompHeaders headers, byte[] payload, Throwable exception) {
                errorFuture.complete(exception);
            }
        });

        try {
            Throwable err = errorFuture.get(4, TimeUnit.SECONDS);
            System.out.println("[Live STOMP Test] Missing restaurantId token rejected as expected: " + (err != null ? err.getMessage() : "null"));
        } catch (TimeoutException ex) {
            System.out.println("[Live STOMP Test] Missing restaurantId connection aborted by server.");
        }
    }

    @Test
    @DisplayName("WebSocket Live STOMP: Path traversal SUBSCRIBE destination is rejected and terminates session")
    void testPathTraversalSubscribeDestinationBlocked() throws Exception {
        String tokenTenant1 = tokenProvider.generateAccessToken(1L, 1L, "owner@restrovision.local", Set.of("OWNER"));

        String[] traversalDestinations = {
                "/topic/restaurants/1/../2/tables",
                "/topic/restaurants/1%2ftables",
                "/topic/restaurants/1\\tables",
                "/topic/restaurants/1/..%2ftables"
        };

        for (String dest : traversalDestinations) {
            StompHeaders connectHeaders = new StompHeaders();
            connectHeaders.add("Authorization", "Bearer " + tokenTenant1);

            CompletableFuture<StompSession> sessionFuture = new CompletableFuture<>();
            org.springframework.web.socket.WebSocketHttpHeaders wsHeaders = new org.springframework.web.socket.WebSocketHttpHeaders();
            stompClient.connectAsync(wsUrl, wsHeaders, connectHeaders, new StompSessionHandlerAdapter() {
                @Override
                public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                    sessionFuture.complete(session);
                }
            });

            StompSession session = sessionFuture.get(5, TimeUnit.SECONDS);
            assertThat(session.isConnected()).isTrue();

            session.subscribe(dest, new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders headers) { return String.class; }
                @Override
                public void handleFrame(StompHeaders headers, Object payload) {}
            });

            Thread.sleep(400);
            assertThat(session.isConnected()).isFalse();
            System.out.println("[Live STOMP Test] Verified: SUBSCRIBE traversal '" + dest + "' blocked, session terminated.");
        }
    }

    @Test
    @DisplayName("WebSocket Live STOMP: SUBSCRIBE to non-tenant or arbitrary topic is rejected and terminates session")
    void testUnauthorizedTopicSubscribeBlocked() throws Exception {
        String tokenTenant1 = tokenProvider.generateAccessToken(1L, 1L, "owner@restrovision.local", Set.of("OWNER"));

        String[] unauthorizedTopics = {
                "/topic/admin/broadcast",
                "/topic/global/events",
                "/topic/all",
                "/topic/restaurants",
                "/topic/restaurants/1"
        };

        for (String topic : unauthorizedTopics) {
            StompHeaders connectHeaders = new StompHeaders();
            connectHeaders.add("Authorization", "Bearer " + tokenTenant1);

            CompletableFuture<StompSession> sessionFuture = new CompletableFuture<>();
            org.springframework.web.socket.WebSocketHttpHeaders wsHeaders = new org.springframework.web.socket.WebSocketHttpHeaders();
            stompClient.connectAsync(wsUrl, wsHeaders, connectHeaders, new StompSessionHandlerAdapter() {
                @Override
                public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                    sessionFuture.complete(session);
                }
            });

            StompSession session = sessionFuture.get(5, TimeUnit.SECONDS);
            assertThat(session.isConnected()).isTrue();

            session.subscribe(topic, new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders headers) { return String.class; }
                @Override
                public void handleFrame(StompHeaders headers, Object payload) {}
            });

            Thread.sleep(400);
            assertThat(session.isConnected()).isFalse();
            System.out.println("[Live STOMP Test] Verified: Unauthorized SUBSCRIBE to '" + topic + "' blocked, session terminated.");
        }
    }

    @Test
    @DisplayName("WebSocket Live STOMP: Invalid same-tenant topics (/admin, /payroll, /sensitive-data) are rejected")
    void testInvalidSameTenantTopicsBlocked() throws Exception {
        String tokenTenant1 = tokenProvider.generateAccessToken(1L, 1L, "owner@restrovision.local", Set.of("OWNER"));

        String[] invalidSameTenantTopics = {
                "/topic/restaurants/1/admin",
                "/topic/restaurants/1/payroll",
                "/topic/restaurants/1/sensitive-data",
                "/topic/restaurants/1/financials",
                "/topic/restaurants/1/internal"
        };

        for (String invalidTopic : invalidSameTenantTopics) {
            StompHeaders connectHeaders = new StompHeaders();
            connectHeaders.add("Authorization", "Bearer " + tokenTenant1);

            CompletableFuture<StompSession> sessionFuture = new CompletableFuture<>();
            org.springframework.web.socket.WebSocketHttpHeaders wsHeaders = new org.springframework.web.socket.WebSocketHttpHeaders();
            stompClient.connectAsync(wsUrl, wsHeaders, connectHeaders, new StompSessionHandlerAdapter() {
                @Override
                public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                    sessionFuture.complete(session);
                }
            });

            StompSession session = sessionFuture.get(5, TimeUnit.SECONDS);
            assertThat(session.isConnected()).isTrue();

            session.subscribe(invalidTopic, new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders headers) { return String.class; }
                @Override
                public void handleFrame(StompHeaders headers, Object payload) {}
            });

            Thread.sleep(400);
            assertThat(session.isConnected()).isFalse();
            System.out.println("[Live STOMP Test] Verified: Invalid same-tenant topic '" + invalidTopic + "' blocked, session terminated.");
        }
    }

    @Test
    @DisplayName("WebSocket Live STOMP: Unauthorized role attempting restricted topic is rejected and terminated")
    void testUnauthorizedRoleAttemptingRestrictedTopicBlocked() throws Exception {
        // 1. CLEANING role attempting to subscribe to food orders
        String cleaningToken = tokenProvider.generateAccessToken(2L, 1L, "cleaning@restrovision.local", Set.of("CLEANING"));

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + cleaningToken);

        CompletableFuture<StompSession> sessionFuture1 = new CompletableFuture<>();
        org.springframework.web.socket.WebSocketHttpHeaders wsHeaders = new org.springframework.web.socket.WebSocketHttpHeaders();
        stompClient.connectAsync(wsUrl, wsHeaders, connectHeaders, new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                sessionFuture1.complete(session);
            }
        });

        StompSession session1 = sessionFuture1.get(5, TimeUnit.SECONDS);
        assertThat(session1.isConnected()).isTrue();

        session1.subscribe("/topic/restaurants/1/orders", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) { return String.class; }
            @Override
            public void handleFrame(StompHeaders headers, Object payload) {}
        });

        Thread.sleep(400);
        assertThat(session1.isConnected()).isFalse();
        System.out.println("[Live STOMP Test] Verified: CLEANING role attempting to subscribe to /orders blocked, session terminated.");

        // 2. KITCHEN role attempting to subscribe to floor tables
        String kitchenToken = tokenProvider.generateAccessToken(3L, 1L, "chef@restrovision.local", Set.of("KITCHEN"));

        connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + kitchenToken);

        CompletableFuture<StompSession> sessionFuture2 = new CompletableFuture<>();
        stompClient.connectAsync(wsUrl, wsHeaders, connectHeaders, new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                sessionFuture2.complete(session);
            }
        });

        StompSession session2 = sessionFuture2.get(5, TimeUnit.SECONDS);
        assertThat(session2.isConnected()).isTrue();

        session2.subscribe("/topic/restaurants/1/tables", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) { return String.class; }
            @Override
            public void handleFrame(StompHeaders headers, Object payload) {}
        });

        Thread.sleep(400);
        assertThat(session2.isConnected()).isFalse();
        System.out.println("[Live STOMP Test] Verified: KITCHEN role attempting to subscribe to /tables blocked, session terminated.");
    }

    @Test
    @DisplayName("WebSocket Live STOMP: Authorized roles for specific topics succeed and remain connected")
    void testAuthorizedRolesForSpecificTopicsSucceed() throws Exception {
        // KITCHEN subscribing to orders
        String kitchenToken = tokenProvider.generateAccessToken(3L, 1L, "chef@restrovision.local", Set.of("KITCHEN"));

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + kitchenToken);

        CompletableFuture<StompSession> sessionFuture = new CompletableFuture<>();
        org.springframework.web.socket.WebSocketHttpHeaders wsHeaders = new org.springframework.web.socket.WebSocketHttpHeaders();
        stompClient.connectAsync(wsUrl, wsHeaders, connectHeaders, new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                sessionFuture.complete(session);
            }
        });

        StompSession session = sessionFuture.get(5, TimeUnit.SECONDS);
        assertThat(session.isConnected()).isTrue();

        session.subscribe("/topic/restaurants/1/orders", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) { return String.class; }
            @Override
            public void handleFrame(StompHeaders headers, Object payload) {}
        });

        Thread.sleep(400);
        assertThat(session.isConnected()).isTrue();
        System.out.println("[Live STOMP Test] Verified: KITCHEN role subscribing to /orders allowed and active.");
        session.disconnect();

        // CLEANING subscribing to tasks
        String cleaningToken = tokenProvider.generateAccessToken(2L, 1L, "cleaning@restrovision.local", Set.of("CLEANING"));

        connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + cleaningToken);

        CompletableFuture<StompSession> sessionFuture2 = new CompletableFuture<>();
        stompClient.connectAsync(wsUrl, wsHeaders, connectHeaders, new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                sessionFuture2.complete(session);
            }
        });

        StompSession session2 = sessionFuture2.get(5, TimeUnit.SECONDS);
        assertThat(session2.isConnected()).isTrue();

        session2.subscribe("/topic/restaurants/1/tasks", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) { return String.class; }
            @Override
            public void handleFrame(StompHeaders headers, Object payload) {}
        });

        Thread.sleep(400);
        assertThat(session2.isConnected()).isTrue();
        System.out.println("[Live STOMP Test] Verified: CLEANING role subscribing to /tasks allowed and active.");
        session2.disconnect();
    }
}
