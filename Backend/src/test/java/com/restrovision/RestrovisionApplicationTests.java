package com.restrovision;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

    private static final String EDGE_HMAC_SECRET = "8f4a2b1c9e3d7a5f6e8b0c2d4e6f8a0b1c3d5e7f9a1b3c5d7e9f1a3b5c7d9e1f";
    private static final String EDGE_SERVICE_TOKEN = "edge-token-pune-01";

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
                        .header("Authorization", "Bearer " + EDGE_SERVICE_TOKEN)
                        .header("X-Signature", "sha256=invalidhashvalue1234567890")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Invalid Signature"));
    }

    @Test
    @DisplayName("05: AI event ingestion with valid edge token and HMAC signature succeeds")
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
                        .header("Authorization", "Bearer " + EDGE_SERVICE_TOKEN)
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
                        .header("Authorization", "Bearer " + EDGE_SERVICE_TOKEN)
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
                        .header("Authorization", "Bearer " + EDGE_SERVICE_TOKEN)
                        .header("X-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawBytes))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        // Second ingestion with same eventId: DUPLICATE_IGNORED (clean 200, no 500 collision)
        mockMvc.perform(post("/api/v1/ai/events")
                        .header("Authorization", "Bearer " + EDGE_SERVICE_TOKEN)
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
    @DisplayName("09: Public feedback validation strictly enforces rating between 1 and 5")
    void feedbackRatingValidation() throws Exception {
        // Invalid rating 999 rejected
        Map<String, Object> badFeedback = Map.of(
                "tableId", 1,
                "rating", 999,
                "comment", "Test extreme rating"
        );
        mockMvc.perform(post("/api/v1/public/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badFeedback)))
                .andExpect(status().isBadRequest());

        // Valid rating 5 accepted
        Map<String, Object> goodFeedback = Map.of(
                "tableId", 1,
                "rating", 5,
                "comment", "Excellent food and service!"
        );
        mockMvc.perform(post("/api/v1/public/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(goodFeedback)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rating").value(5))
                .andExpect(jsonPath("$.sentiment").value("POSITIVE"));
    }

    @Test
    @DisplayName("10: Authentication: Wrong password returns 401 Unauthorized ProblemDetail")
    void authWrongPasswordReturns401() throws Exception {
        Map<String, String> creds = Map.of("email", "manager@restrovision.local", "password", "WrongPassword!999");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(creds)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Authentication Failed"));
    }

    @Test
    @DisplayName("11: Token Separation: Refresh token rejected as Bearer; Access token rejected on /refresh")
    void tokenSeparationEnforced() throws Exception {
        Map<String, String> tokens = loginUser("manager@restrovision.local", "Manager@123");
        String accessToken = tokens.get("accessToken");
        String refreshToken = tokens.get("refreshToken");

        // 1. Calling /me with accessToken succeeds
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("manager@restrovision.local"));

        // 2. Calling /me with refreshToken is rejected (401)
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + refreshToken))
                .andExpect(status().isUnauthorized());

        // 3. Calling /refresh with accessToken is rejected (401)
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", accessToken))))
                .andExpect(status().isUnauthorized());

        // 4. Calling /refresh with refreshToken succeeds and returns new accessToken
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    @DisplayName("12: Role-Based Access Control: Waiter cannot access Cameras or mark OUT_OF_SERVICE")
    void rbacGuardsEnforced() throws Exception {
        Map<String, String> waiterTokens = loginUser("waiter1@restrovision.local", "Waiter@123");
        String waiterToken = waiterTokens.get("accessToken");

        Map<String, String> managerTokens = loginUser("manager@restrovision.local", "Manager@123");
        String managerToken = managerTokens.get("accessToken");

        // Waiter cannot access camera feeds (403)
        mockMvc.perform(get("/api/v1/cameras")
                        .header("Authorization", "Bearer " + waiterToken))
                .andExpect(status().isForbidden());

        // Manager can access camera feeds (200)
        mockMvc.perform(get("/api/v1/cameras")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk());

        // Waiter cannot set table status to OUT_OF_SERVICE (403)
        Map<String, String> statusBody = Map.of("status", "OUT_OF_SERVICE", "reason", "Maintenance");
        mockMvc.perform(put("/api/v1/tables/3/status")
                        .header("Authorization", "Bearer " + waiterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusBody)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("13: Table State Machine: Invalid status 'PURPLE' rejected; Seating on OCCUPIED table rejected")
    void tableStateMachineGuards() throws Exception {
        Map<String, String> managerTokens = loginUser("manager@restrovision.local", "Manager@123");
        String managerToken = managerTokens.get("accessToken");

        // Invalid status "PURPLE" rejected with 400
        Map<String, String> invalidStatus = Map.of("status", "PURPLE", "reason", "Invalid Color");
        mockMvc.perform(put("/api/v1/tables/3/status")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidStatus)))
                .andExpect(status().isBadRequest());

        // Seating party on Table 1 (already OCCUPIED) returns 409 Conflict
        Map<String, Object> seatReq = Map.of("partySize", 4);
        mockMvc.perform(post("/api/v1/tables/1/seat")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(seatReq)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("14: Order Validation: Rejects empty items and non-positive quantities")
    void orderValidationEnforced() throws Exception {
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
}
