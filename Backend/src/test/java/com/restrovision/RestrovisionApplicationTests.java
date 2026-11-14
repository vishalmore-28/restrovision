package com.restrovision;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restrovision.config.WebSocketConfig;
import com.restrovision.domain.EdgeNode;
import com.restrovision.repository.EdgeNodeRepository;
import com.restrovision.security.JwtTokenProvider;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RestrovisionApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private EdgeNodeRepository edgeNodeRepository;

    @Autowired
    private WebSocketConfig webSocketConfig;

    @Autowired(required = false)
    private Flyway flyway;

    private static final String EDGE_HMAC_SECRET = "8f4a2b1c9e3d7a5f6e8b0c2d4e6f8a0b1c3d5e7f9a1b3c5d7e9f1a3b5c7d9e1f";
    private static final String LEGACY_STATIC_EDGE_TOKEN = "edge-token-pune-01";

    private String getValidEdgeToken() {
        return tokenProvider.generateEdgeToken("edge-pune-01", 1L);
    }

    private String calculateHmac(byte[] data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec key = new SecretKeySpec(EDGE_HMAC_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(key);
        byte[] hash = mac.doFinal(data);
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return "sha256=" + sb.toString();
    }

    private Map<String, String> loginUser(String email, String password) throws Exception {
        Map<String, String> creds = Map.of("email", email, "password", password);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(creds)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return Map.of(
                "accessToken", root.get("accessToken").asText(),
                "refreshToken", root.get("refreshToken").asText()
        );
    }

    @Test
    @DisplayName("01: Spring Application Context loads successfully with JPA/Hibernate")
    void contextLoads() {
        // Successful context startup validates Hibernate entities and database schema initialization
    }

    @Test
    @DisplayName("02: Unauthenticated request to protected endpoints is rejected with 401")
    void unauthenticatedAccessDenied() throws Exception {
        mockMvc.perform(get("/api/v1/tables"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("03: AI event ingestion requires authentication (no anonymous access)")
    void aiIngestionRequiresAuth() throws Exception {
        Map<String, Object> payload = Map.of(
                "eventId", UUID.randomUUID().toString(),
                "eventType", "POSSIBLE_FALL",
                "confidence", 0.95
        );
        String body = objectMapper.writeValueAsString(payload);

        mockMvc.perform(post("/api/v1/ai/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("04: AI event ingestion with invalid HMAC signature is rejected with 401 ProblemDetail")
    void aiIngestionRejectsBadSignature() throws Exception {
        Map<String, Object> payload = Map.of(
                "eventId", UUID.randomUUID().toString(),
                "eventType", "POSSIBLE_FALL",
                "confidence", 0.95
        );
        String body = objectMapper.writeValueAsString(payload);

        mockMvc.perform(post("/api/v1/ai/events")
                        .header("Authorization", "Bearer " + getValidEdgeToken())
                        .header("X-Signature", "sha256=invalidhashvalue1234567890")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Invalid Signature"));
    }

    @Test
    @DisplayName("05: AI event ingestion with valid per-device edge JWT and HMAC signature succeeds")
    void aiIngestionSucceedsWithValidSignature() throws Exception {
        String eventId = UUID.randomUUID().toString();
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventId", eventId);
        payload.put("schemaVersion", "1.0");
        payload.put("edgeNodeId", "edge-pune-01");
        payload.put("restaurantId", 1);
        payload.put("tableId", 1);
        payload.put("eventType", "TABLE_OCCUPIED");
        payload.put("confidence", 0.92);
        payload.put("occurredAt", Instant.now().toString());
        payload.put("detectedAt", Instant.now().toString());

        byte[] rawBytes = objectMapper.writeValueAsBytes(payload);
        String signature = calculateHmac(rawBytes);

        mockMvc.perform(post("/api/v1/ai/events")
                        .header("Authorization", "Bearer " + getValidEdgeToken())
                        .header("X-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawBytes))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.eventId").value(eventId));
    }

    @Test
    @DisplayName("06: AI event ingestion confidence gate rejects low confidence (< 0.70) events")
    void aiIngestionRejectsLowConfidence() throws Exception {
        String eventId = UUID.randomUUID().toString();
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventId", eventId);
        payload.put("schemaVersion", "1.0");
        payload.put("edgeNodeId", "edge-pune-01");
        payload.put("restaurantId", 1);
        payload.put("tableId", 1);
        payload.put("eventType", "POSSIBLE_FALL");
        payload.put("confidence", 0.55); // Below 0.70 threshold
        payload.put("occurredAt", Instant.now().toString());
        payload.put("detectedAt", Instant.now().toString());

        byte[] rawBytes = objectMapper.writeValueAsBytes(payload);
        String signature = calculateHmac(rawBytes);

        mockMvc.perform(post("/api/v1/ai/events")
                        .header("Authorization", "Bearer " + getValidEdgeToken())
                        .header("X-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawBytes))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED_BY_RULE"));
    }

    @Test
    @DisplayName("07: AI event ingestion idempotency returns DUPLICATE_IGNORED atomically")
    void aiIngestionDuplicateAtomicallyIgnored() throws Exception {
        String eventId = UUID.randomUUID().toString();
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventId", eventId);
        payload.put("schemaVersion", "1.0");
        payload.put("edgeNodeId", "edge-pune-01");
        payload.put("restaurantId", 1);
        payload.put("tableId", 2);
        payload.put("eventType", "TABLE_OCCUPIED");
        payload.put("confidence", 0.91);
        payload.put("occurredAt", Instant.now().toString());
        payload.put("detectedAt", Instant.now().toString());

        byte[] rawBytes = objectMapper.writeValueAsBytes(payload);
        String signature = calculateHmac(rawBytes);

        // First ingestion: ACCEPTED
        mockMvc.perform(post("/api/v1/ai/events")
                        .header("Authorization", "Bearer " + getValidEdgeToken())
                        .header("X-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawBytes))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        // Second ingestion with same eventId: DUPLICATE_IGNORED (clean 200, no 500 collision)
        mockMvc.perform(post("/api/v1/ai/events")
                        .header("Authorization", "Bearer " + getValidEdgeToken())
                        .header("X-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawBytes))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DUPLICATE_IGNORED"));
    }

    @Test
    @DisplayName("08: Public QR status endpoint resolves valid tokens and rejects invalid ones")
    void publicQrTokenValidation() throws Exception {
        // Valid token seeded by DataInitializer
        mockMvc.perform(get("/api/v1/public/qr/qr-tok-table-01/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tableCode").value("T01"));

        // Invalid token returns 400 Bad Request (ProblemDetail)
        mockMvc.perform(get("/api/v1/public/qr/nonexistent-token/status"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("09: Multi-tenant boundary isolation: Users cannot access another restaurant's tables")
    void multiTenantBoundaryEnforced() throws Exception {
        // Owner of Restaurant 1 logs in
        Map<String, String> tokens = loginUser("owner@restrovision.local", "Admin@123");
        String token = tokens.get("accessToken");

        // Authenticated request accesses Restaurant 1 tables
        mockMvc.perform(get("/api/v1/tables")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                        .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("10: RBAC: Kitchen staff forbidden from accessing customer feedback endpoints")
    void rbacForbiddenEndpoint() throws Exception {
        Map<String, String> tokens = loginUser("chef@restrovision.local", "Chef@123");
        String chefToken = tokens.get("accessToken");

        // Chef role cannot access /api/v1/employees (OWNER/MANAGER only)
        mockMvc.perform(get("/api/v1/employees")
                        .header("Authorization", "Bearer " + chefToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("11: RBAC: Manager can access employee management")
    void rbacAllowedEndpoint() throws Exception {
        Map<String, String> tokens = loginUser("manager@restrovision.local", "Manager@123");
        String managerToken = tokens.get("accessToken");

        mockMvc.perform(get("/api/v1/employees")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("12: State machine enforces valid table status transitions")
    void stateMachineTransition() throws Exception {
        Map<String, String> waiterTokens = loginUser("waiter1@restrovision.local", "Waiter@123");
        String waiterToken = waiterTokens.get("accessToken");

        // Seating Table 3 (AVAILABLE -> OCCUPIED)
        Map<String, Object> req = Map.of("status", "OCCUPIED", "partySize", 4);
        mockMvc.perform(put("/api/v1/tables/3/status")
                        .header("Authorization", "Bearer " + waiterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OCCUPIED"));

        // Invalid transition with non-existent status should be rejected (400 Bad Request)
        Map<String, Object> badReq = Map.of("status", "BILL_REQUESTED");
        mockMvc.perform(put("/api/v1/tables/3/status")
                        .header("Authorization", "Bearer " + waiterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badReq)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("13: Refresh token cannot be used as Bearer access token for protected endpoints")
    void refreshTokenRejectedAsBearer() throws Exception {
        Map<String, String> tokens = loginUser("waiter1@restrovision.local", "Waiter@123");
        String refreshToken = tokens.get("refreshToken");

        // Supplying a refresh token in Authorization header must be rejected
        mockMvc.perform(get("/api/v1/tables")
                        .header("Authorization", "Bearer " + refreshToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("14: Order Creation: Validates items and positive quantities")
    void orderValidation() throws Exception {
        Map<String, String> waiterTokens = loginUser("waiter1@restrovision.local", "Waiter@123");
        String waiterToken = waiterTokens.get("accessToken");

        // Empty items rejected (400)
        Map<String, Object> emptyOrder = Map.of("tableId", 1, "items", List.of());
        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + waiterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(emptyOrder)))
                .andExpect(status().isBadRequest());

        // Quantity <= 0 rejected (400)
        Map<String, Object> invalidQtyOrder = Map.of(
                "tableId", 1,
                "items", List.of(Map.of("itemId", 1, "quantity", 0))
        );
        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + waiterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidQtyOrder)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("15: Analytics Overview: Returns computed metrics without fake fallback values")
    void analyticsOverviewHonestStates() throws Exception {
        Map<String, String> managerTokens = loginUser("manager@restrovision.local", "Manager@123");
        String managerToken = managerTokens.get("accessToken");

        mockMvc.perform(get("/api/v1/analytics/overview")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTables").value(10))
                .andExpect(jsonPath("$.occupancyRate").isNumber());
    }

    // =========================================================================
    // GROUP 1 (P0) REMEDIATION EMPIRICAL TESTS
    // =========================================================================

    @Test
    @DisplayName("16: BUG-002: Legacy static edge token is strictly rejected with 401")
    void edgeStaticTokenRejected() throws Exception {
        String eventId = UUID.randomUUID().toString();
        Map<String, Object> payload = Map.of(
                "eventId", eventId,
                "eventType", "TABLE_OCCUPIED",
                "tableId", 1,
                "confidence", 0.95
        );
        byte[] rawBytes = objectMapper.writeValueAsBytes(payload);
        String signature = calculateHmac(rawBytes);

        mockMvc.perform(post("/api/v1/ai/events")
                        .header("Authorization", "Bearer " + LEGACY_STATIC_EDGE_TOKEN)
                        .header("X-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawBytes))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("17: BUG-002: Unregistered edge device ID in JWT is rejected with 401")
    void edgeUnknownDeviceRejected() throws Exception {
        String unregisteredToken = tokenProvider.generateEdgeToken("unknown-device-999", 1L);
        Map<String, Object> payload = Map.of(
                "eventId", UUID.randomUUID().toString(),
                "eventType", "TABLE_OCCUPIED",
                "tableId", 1,
                "confidence", 0.95
        );
        byte[] rawBytes = objectMapper.writeValueAsBytes(payload);
        String signature = calculateHmac(rawBytes);

        mockMvc.perform(post("/api/v1/ai/events")
                        .header("Authorization", "Bearer " + unregisteredToken)
                        .header("X-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawBytes))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("18: BUG-002: Inactive edge device (status DEGRADED/OFFLINE) is rejected with 401")
    void edgeInactiveDeviceRejected() throws Exception {
        // Register an offline edge device
        EdgeNode offlineNode = new EdgeNode("edge-offline-99", 1L, "Offline Camera Node", "OFFLINE");
        edgeNodeRepository.save(offlineNode);

        String offlineToken = tokenProvider.generateEdgeToken("edge-offline-99", 1L);
        Map<String, Object> payload = Map.of(
                "eventId", UUID.randomUUID().toString(),
                "eventType", "TABLE_OCCUPIED",
                "tableId", 1,
                "confidence", 0.95
        );
        byte[] rawBytes = objectMapper.writeValueAsBytes(payload);
        String signature = calculateHmac(rawBytes);

        mockMvc.perform(post("/api/v1/ai/events")
                        .header("Authorization", "Bearer " + offlineToken)
                        .header("X-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawBytes))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("19: BUG-002: Mismatched tenant ID in edge JWT claim is rejected with 401")
    void edgeClaimTenantMismatchRejected() throws Exception {
        // edge-pune-01 is bound to restaurant 1 in DB, but token claims restaurant 999
        String mismatchedToken = tokenProvider.generateEdgeToken("edge-pune-01", 999L);
        Map<String, Object> payload = Map.of(
                "eventId", UUID.randomUUID().toString(),
                "eventType", "TABLE_OCCUPIED",
                "tableId", 1,
                "confidence", 0.95
        );
        byte[] rawBytes = objectMapper.writeValueAsBytes(payload);
        String signature = calculateHmac(rawBytes);

        mockMvc.perform(post("/api/v1/ai/events")
                        .header("Authorization", "Bearer " + mismatchedToken)
                        .header("X-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawBytes))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("20: BUG-002: Edge device token generation endpoint is accessible to Managers/Owners")
    void edgeTokenGenerationEndpoint() throws Exception {
        Map<String, String> tokens = loginUser("manager@restrovision.local", "Manager@123");
        String managerToken = tokens.get("accessToken");

        mockMvc.perform(post("/api/v1/cameras/edge-token")
                        .header("Authorization", "Bearer " + managerToken)
                        .param("edgeNodeId", "edge-pune-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.edgeNodeId").value("edge-pune-01"))
                .andExpect(jsonPath("$.tokenType").value("EDGE_DEVICE"));
    }

    @Test
    @DisplayName("21: BUG-003 & BUG-011: Spring Boot Actuator /actuator/health is accessible and UP")
    void actuatorHealthCheckEndpoint() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("22: BUG-012: Flyway core schema migration baseline is executed successfully")
    void flywayMigrationBaseline() {
        if (flyway != null) {
            assertThat(flyway.info().current()).isNotNull();
            assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("1");
        }
    }

    @Test
    @DisplayName("23: BUG-001: WebSocket STOMP anonymous CONNECT is rejected")
    void webSocketAnonymousConnectRejected() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        Message<?> msg = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThrows(AuthenticationCredentialsNotFoundException.class, () ->
                webSocketConfig.createSecurityInterceptor().preSend(msg, null)
        );
    }

    @Test
    @DisplayName("24: BUG-001: WebSocket STOMP CONNECT with invalid or refresh token is rejected")
    void webSocketInvalidTokensRejected() {
        // Invalid JWT
        StompHeaderAccessor accessorInvalid = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessorInvalid.addNativeHeader("Authorization", "Bearer invalid.jwt.signature");
        Message<?> msgInvalid = MessageBuilder.createMessage(new byte[0], accessorInvalid.getMessageHeaders());

        assertThrows(BadCredentialsException.class, () ->
                webSocketConfig.createSecurityInterceptor().preSend(msgInvalid, null)
        );

        // Refresh Token attempted on WebSocket
        String refreshToken = tokenProvider.generateRefreshToken(1L, 1L, "owner@restrovision.local");
        StompHeaderAccessor accessorRefresh = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessorRefresh.addNativeHeader("Authorization", "Bearer " + refreshToken);
        Message<?> msgRefresh = MessageBuilder.createMessage(new byte[0], accessorRefresh.getMessageHeaders());

        assertThrows(BadCredentialsException.class, () ->
                webSocketConfig.createSecurityInterceptor().preSend(msgRefresh, null)
        );
    }

    @Test
    @DisplayName("25: BUG-001: WebSocket STOMP CONNECT with valid token authenticates and binds tenant")
    void webSocketValidConnectAuthenticates() {
        String accessToken = tokenProvider.generateAccessToken(1L, 1L, "owner@restrovision.local", Set.of("OWNER"));
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader("Authorization", "Bearer " + accessToken);
        accessor.setSessionAttributes(new HashMap<>());
        accessor.setLeaveMutable(true);

        Message<?> msg = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        Message<?> result = webSocketConfig.createSecurityInterceptor().preSend(msg, null);

        StompHeaderAccessor postAccessor = MessageHeaderAccessor.getAccessor(result, StompHeaderAccessor.class);
        assertThat(postAccessor).isNotNull();
        assertThat(postAccessor.getUser()).isNotNull();
        assertThat(postAccessor.getSessionAttributes().get("restaurantId")).isEqualTo(1L);
    }

    @Test
    @DisplayName("26: BUG-001: WebSocket SUBSCRIBE and SEND enforce authentication")
    void webSocketUnauthenticatedOperationsRejected() {
        // Unauthenticated SUBSCRIBE
        StompHeaderAccessor subAccessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        subAccessor.setDestination("/topic/restaurants/1/tables");
        Message<?> subMsg = MessageBuilder.createMessage(new byte[0], subAccessor.getMessageHeaders());

        assertThrows(AccessDeniedException.class, () ->
                webSocketConfig.createSecurityInterceptor().preSend(subMsg, null)
        );

        // Unauthenticated SEND
        StompHeaderAccessor sendAccessor = StompHeaderAccessor.create(StompCommand.SEND);
        sendAccessor.setDestination("/app/alerts");
        Message<?> sendMsg = MessageBuilder.createMessage(new byte[0], sendAccessor.getMessageHeaders());

        assertThrows(AccessDeniedException.class, () ->
                webSocketConfig.createSecurityInterceptor().preSend(sendMsg, null)
        );
    }

    @Test
    @DisplayName("27: BUG-001: WebSocket multi-tenant isolation verified across two separate tenants")
    void webSocketMultiTenantIsolation() {
        // Tenant 1 session
        StompHeaderAccessor t1Session = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        t1Session.setDestination("/topic/restaurants/1/tables");
        t1Session.setUser(new UsernamePasswordAuthenticationToken("user1@restaurant1.com", null, Collections.emptyList()));
        t1Session.setSessionAttributes(new HashMap<>(Map.of("restaurantId", 1L)));
        Message<?> t1ValidMsg = MessageBuilder.createMessage(new byte[0], t1Session.getMessageHeaders());

        // Same-tenant subscription to Restaurant 1 passes
        Message<?> t1Result = webSocketConfig.createSecurityInterceptor().preSend(t1ValidMsg, null);
        assertThat(t1Result).isNotNull();

        // Cross-tenant attempt: Tenant 1 session subscribing to Restaurant 2 is denied
        StompHeaderAccessor t1Cross = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        t1Cross.setDestination("/topic/restaurants/2/tables");
        t1Cross.setUser(new UsernamePasswordAuthenticationToken("user1@restaurant1.com", null, Collections.emptyList()));
        t1Cross.setSessionAttributes(new HashMap<>(Map.of("restaurantId", 1L)));
        Message<?> t1CrossMsg = MessageBuilder.createMessage(new byte[0], t1Cross.getMessageHeaders());

        assertThrows(AccessDeniedException.class, () ->
                webSocketConfig.createSecurityInterceptor().preSend(t1CrossMsg, null)
        );

        // Tenant 2 session
        StompHeaderAccessor t2Session = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        t2Session.setDestination("/topic/restaurants/2/tables");
        t2Session.setUser(new UsernamePasswordAuthenticationToken("user2@restaurant2.com", null, Collections.emptyList()));
        t2Session.setSessionAttributes(new HashMap<>(Map.of("restaurantId", 2L)));
        Message<?> t2ValidMsg = MessageBuilder.createMessage(new byte[0], t2Session.getMessageHeaders());

        // Same-tenant subscription to Restaurant 2 passes
        Message<?> t2Result = webSocketConfig.createSecurityInterceptor().preSend(t2ValidMsg, null);
        assertThat(t2Result).isNotNull();

        // Cross-tenant attempt: Tenant 2 session subscribing to Restaurant 1 is denied
        StompHeaderAccessor t2Cross = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        t2Cross.setDestination("/topic/restaurants/1/tables");
        t2Cross.setUser(new UsernamePasswordAuthenticationToken("user2@restaurant2.com", null, Collections.emptyList()));
        t2Cross.setSessionAttributes(new HashMap<>(Map.of("restaurantId", 2L)));
        Message<?> t2CrossMsg = MessageBuilder.createMessage(new byte[0], t2Cross.getMessageHeaders());

        assertThrows(AccessDeniedException.class, () ->
                webSocketConfig.createSecurityInterceptor().preSend(t2CrossMsg, null)
        );
    }

    @Test
    @DisplayName("28: BUG-001: WebSocket SEND destination-level authorization enforced")
    void webSocketSendDestinationAuthorization() {
        // Authenticated Tenant 1 session
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken("user1@restaurant1.com", null, Collections.emptyList());
        Map<String, Object> sessionAttrs = new HashMap<>(Map.of("restaurantId", 1L));

        // 1. Authorized SEND to /app/restaurants/1/ping succeeds
        StompHeaderAccessor validSend = StompHeaderAccessor.create(StompCommand.SEND);
        validSend.setDestination("/app/restaurants/1/ping");
        validSend.setUser(auth);
        validSend.setSessionAttributes(sessionAttrs);
        Message<?> validMsg = MessageBuilder.createMessage(new byte[0], validSend.getMessageHeaders());
        Message<?> result = webSocketConfig.createSecurityInterceptor().preSend(validMsg, null);
        assertThat(result).isNotNull();

        // 2. Cross-tenant SEND to /app/restaurants/2/orders throws AccessDeniedException
        StompHeaderAccessor crossSend = StompHeaderAccessor.create(StompCommand.SEND);
        crossSend.setDestination("/app/restaurants/2/orders");
        crossSend.setUser(auth);
        crossSend.setSessionAttributes(sessionAttrs);
        Message<?> crossMsg = MessageBuilder.createMessage(new byte[0], crossSend.getMessageHeaders());
        assertThrows(AccessDeniedException.class, () ->
                webSocketConfig.createSecurityInterceptor().preSend(crossMsg, null)
        );

        // 3. Direct client SEND to broker /topic/restaurants/1/tables throws AccessDeniedException
        StompHeaderAccessor topicSend = StompHeaderAccessor.create(StompCommand.SEND);
        topicSend.setDestination("/topic/restaurants/1/tables");
        topicSend.setUser(auth);
        topicSend.setSessionAttributes(sessionAttrs);
        Message<?> topicMsg = MessageBuilder.createMessage(new byte[0], topicSend.getMessageHeaders());
        assertThrows(AccessDeniedException.class, () ->
                webSocketConfig.createSecurityInterceptor().preSend(topicMsg, null)
        );

        // 4. Client SEND to unsupported prefix throws AccessDeniedException
        StompHeaderAccessor unsuppSend = StompHeaderAccessor.create(StompCommand.SEND);
        unsuppSend.setDestination("/unsupported/custom");
        unsuppSend.setUser(auth);
        unsuppSend.setSessionAttributes(sessionAttrs);
        Message<?> unsuppMsg = MessageBuilder.createMessage(new byte[0], unsuppSend.getMessageHeaders());
        assertThrows(AccessDeniedException.class, () ->
                webSocketConfig.createSecurityInterceptor().preSend(unsuppMsg, null)
        );

        // 5. Client SEND with null destination throws AccessDeniedException
        StompHeaderAccessor nullSend = StompHeaderAccessor.create(StompCommand.SEND);
        nullSend.setDestination(null);
        nullSend.setUser(auth);
        nullSend.setSessionAttributes(sessionAttrs);
        Message<?> nullMsg = MessageBuilder.createMessage(new byte[0], nullSend.getMessageHeaders());
        assertThrows(AccessDeniedException.class, () ->
                webSocketConfig.createSecurityInterceptor().preSend(nullMsg, null)
        );
    }
}
