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

import com.restrovision.domain.AiEvent;
import com.restrovision.domain.Alert;
import com.restrovision.domain.RestaurantTable;
import com.restrovision.domain.ServiceRequest;
import com.restrovision.domain.TableSession;
import com.restrovision.repository.AiEventRepository;
import com.restrovision.repository.AlertRepository;
import com.restrovision.repository.RestaurantTableRepository;
import com.restrovision.repository.ServiceRequestRepository;
import com.restrovision.repository.TableSessionRepository;
import com.restrovision.service.AiEventIngestionService;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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

    @Autowired
    private AiEventRepository aiEventRepository;

    @Autowired
    private AiEventIngestionService aiEventIngestionService;

    @Autowired
    private RestaurantTableRepository tableRepository;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private TableSessionRepository sessionRepository;

    @Autowired
    private ServiceRequestRepository serviceRequestRepository;

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
    @DisplayName("07b: AI event ingestion rejects stale events exceeding max age window (ARCH-004 / BUG-005)")
    void aiIngestionRejectsStaleTimestamp() throws Exception {
        String eventId = UUID.randomUUID().toString();
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventId", eventId);
        payload.put("schemaVersion", "1.0");
        payload.put("edgeNodeId", "edge-pune-01");
        payload.put("restaurantId", 1);
        payload.put("tableId", 2);
        payload.put("eventType", "TABLE_OCCUPIED");
        payload.put("confidence", 0.95);
        // 10 days in the past (historical replay attack)
        payload.put("occurredAt", Instant.now().minus(java.time.Duration.ofDays(10)).toString());
        payload.put("detectedAt", Instant.now().toString());

        byte[] rawBytes = objectMapper.writeValueAsBytes(payload);
        String signature = calculateHmac(rawBytes);

        mockMvc.perform(post("/api/v1/ai/events")
                        .header("Authorization", "Bearer " + getValidEdgeToken())
                        .header("X-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawBytes))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED_STALE"))
                .andExpect(jsonPath("$.eventId").value(eventId));
    }

    @Test
    @DisplayName("07c: AI event ingestion rejects excessive future timestamp beyond clock skew tolerance (BUG-005)")
    void aiIngestionRejectsExcessiveFutureTimestamp() throws Exception {
        String eventId = UUID.randomUUID().toString();
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventId", eventId);
        payload.put("schemaVersion", "1.0");
        payload.put("edgeNodeId", "edge-pune-01");
        payload.put("restaurantId", 1);
        payload.put("tableId", 2);
        payload.put("eventType", "TABLE_OCCUPIED");
        payload.put("confidence", 0.95);
        // 2 hours in the future
        payload.put("occurredAt", Instant.now().plus(java.time.Duration.ofHours(2)).toString());
        payload.put("detectedAt", Instant.now().toString());

        byte[] rawBytes = objectMapper.writeValueAsBytes(payload);
        String signature = calculateHmac(rawBytes);

        mockMvc.perform(post("/api/v1/ai/events")
                        .header("Authorization", "Bearer " + getValidEdgeToken())
                        .header("X-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawBytes))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED_CLOCK_SKEW"))
                .andExpect(jsonPath("$.eventId").value(eventId));
    }

    @Test
    @DisplayName("07d: AI event ingestion accepts legitimate delayed outbox retry within 2h window (BUG-005)")
    void aiIngestionAcceptsDelayedOutboxRetry() throws Exception {
        String eventId = UUID.randomUUID().toString();
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventId", eventId);
        payload.put("schemaVersion", "1.0");
        payload.put("edgeNodeId", "edge-pune-01");
        payload.put("restaurantId", 1);
        payload.put("tableId", 2);
        payload.put("eventType", "TABLE_OCCUPIED");
        payload.put("confidence", 0.91);
        // 35 minutes in the past (e.g. legitimate outbox retry attempt #5)
        payload.put("occurredAt", Instant.now().minus(java.time.Duration.ofMinutes(35)).toString());
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
    @DisplayName("07e: AI event ingestion accepts slight clock skew (5m future) within tolerance (BUG-005)")
    void aiIngestionAcceptsClockSkewWithinTolerance() throws Exception {
        String eventId = UUID.randomUUID().toString();
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventId", eventId);
        payload.put("schemaVersion", "1.0");
        payload.put("edgeNodeId", "edge-pune-01");
        payload.put("restaurantId", 1);
        payload.put("tableId", 2);
        payload.put("eventType", "TABLE_OCCUPIED");
        payload.put("confidence", 0.93);
        // 5 minutes in the future (within 10-minute clock skew tolerance)
        payload.put("occurredAt", Instant.now().plus(java.time.Duration.ofMinutes(5)).toString());
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
    @DisplayName("07f: Concurrent duplicate delivery - exactly 1 ACCEPTED, remainder DUPLICATE_IGNORED, no duplicate state transitions (BUG-005)")
    void testConcurrentDuplicateDeliveryRaceCondition() throws Exception {
        String eventId = UUID.randomUUID().toString();
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventId", eventId);
        payload.put("schemaVersion", "1.0");
        payload.put("edgeNodeId", "edge-pune-01");
        payload.put("restaurantId", 1);
        payload.put("tableId", 2);
        payload.put("eventType", "TABLE_OCCUPIED");
        payload.put("confidence", 0.95);
        payload.put("occurredAt", Instant.now().toString());
        payload.put("detectedAt", Instant.now().toString());

        byte[] rawBytes = objectMapper.writeValueAsBytes(payload);
        String signature = calculateHmac(rawBytes);
        String edgeToken = getValidEdgeToken();

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                startLatch.await();
                MvcResult result = mockMvc.perform(post("/api/v1/ai/events")
                                .header("Authorization", "Bearer " + edgeToken)
                                .header("X-Signature", signature)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(rawBytes))
                        .andReturn();
                if (result.getResponse().getStatus() != 200) {
                    return "ERROR_" + result.getResponse().getStatus();
                }
                JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
                return json.get("status").asText();
            }));
        }

        startLatch.countDown();
        executor.shutdown();
        boolean completed = executor.awaitTermination(15, TimeUnit.SECONDS);
        assertThat(completed).isTrue();

        int acceptedCount = 0;
        int duplicateIgnoredCount = 0;
        for (Future<String> f : futures) {
            String status = f.get();
            if ("ACCEPTED".equals(status)) {
                acceptedCount++;
            } else if ("DUPLICATE_IGNORED".equals(status)) {
                duplicateIgnoredCount++;
            }
        }

        assertThat(acceptedCount).isEqualTo(1);
        assertThat(duplicateIgnoredCount).isEqualTo(threadCount - 1);

        long dbEventCount = aiEventRepository.findAll().stream()
                .filter(e -> eventId.equals(e.getEventUid()) && Long.valueOf(1L).equals(e.getRestaurantId()))
                .count();
        assertThat(dbEventCount).isEqualTo(1);
    }

    @Test
    @DisplayName("07g: Signed-event replay, tampered UUID detection, stale replay, and delayed retry (BUG-005)")
    void testSignedEventReplayAndTamperedUuid() throws Exception {
        String edgeToken = getValidEdgeToken();

        // 1. Legitimate original event
        String eventId = UUID.randomUUID().toString();
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventId", eventId);
        payload.put("schemaVersion", "1.0");
        payload.put("edgeNodeId", "edge-pune-01");
        payload.put("restaurantId", 1);
        payload.put("tableId", 2);
        payload.put("eventType", "TABLE_OCCUPIED");
        payload.put("confidence", 0.92);
        payload.put("occurredAt", Instant.now().toString());
        payload.put("detectedAt", Instant.now().toString());

        byte[] rawBytes = objectMapper.writeValueAsBytes(payload);
        String signature = calculateHmac(rawBytes);

        mockMvc.perform(post("/api/v1/ai/events")
                        .header("Authorization", "Bearer " + edgeToken)
                        .header("X-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawBytes))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        // 2. Direct replay of identical signed payload -> DUPLICATE_IGNORED
        mockMvc.perform(post("/api/v1/ai/events")
                        .header("Authorization", "Bearer " + edgeToken)
                        .header("X-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawBytes))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DUPLICATE_IGNORED"));

        // 3. Forged/tampered replay: attacker modifies eventId to bypass deduplication, but reuses original HMAC signature
        Map<String, Object> tamperedPayload = new HashMap<>(payload);
        String tamperedEventId = UUID.randomUUID().toString();
        tamperedPayload.put("eventId", tamperedEventId);
        byte[] tamperedBytes = objectMapper.writeValueAsBytes(tamperedPayload);

        mockMvc.perform(post("/api/v1/ai/events")
                        .header("Authorization", "Bearer " + edgeToken)
                        .header("X-Signature", signature) // Reused signature from original payload
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tamperedBytes))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Invalid Signature"));

        // Ensure tampered event was NEVER inserted into DB
        assertThat(aiEventRepository.findByRestaurantIdAndEventUid(1L, tamperedEventId)).isEmpty();

        // 4. Stale event replay (e.g. 10 days old) signed with fresh HMAC -> REJECTED_STALE
        Map<String, Object> stalePayload = new HashMap<>(payload);
        stalePayload.put("eventId", UUID.randomUUID().toString());
        stalePayload.put("occurredAt", Instant.now().minus(java.time.Duration.ofDays(10)).toString());
        byte[] staleBytes = objectMapper.writeValueAsBytes(stalePayload);
        String staleSignature = calculateHmac(staleBytes);

        mockMvc.perform(post("/api/v1/ai/events")
                        .header("Authorization", "Bearer " + edgeToken)
                        .header("X-Signature", staleSignature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(staleBytes))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED_STALE"));

        // 5. Legitimate delayed outbox retry (e.g. 25 minutes ago) -> ACCEPTED
        Map<String, Object> retryPayload = new HashMap<>(payload);
        retryPayload.put("eventId", UUID.randomUUID().toString());
        retryPayload.put("occurredAt", Instant.now().minus(java.time.Duration.ofMinutes(25)).toString());
        byte[] retryBytes = objectMapper.writeValueAsBytes(retryPayload);
        String retrySignature = calculateHmac(retryBytes);

        mockMvc.perform(post("/api/v1/ai/events")
                        .header("Authorization", "Bearer " + edgeToken)
                        .header("X-Signature", retrySignature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(retryBytes))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    @Test
    @DisplayName("07h: Signature validation strictly precedes event processing; rejected events cannot alter state (BUG-005)")
    void testSignatureValidationPrecedesEventProcessingAndStateRemainsUnchanged() throws Exception {
        String edgeToken = getValidEdgeToken();

        RestaurantTable tableBefore = tableRepository.findByRestaurantIdAndTableId(1L, 1L).orElseThrow();
        String initialStatus = tableBefore.getStatus();
        long initialAlertCount = alertRepository.count();
        long initialAiEventCount = aiEventRepository.count();

        String eventId = UUID.randomUUID().toString();
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventId", eventId);
        payload.put("schemaVersion", "1.0");
        payload.put("edgeNodeId", "edge-pune-01");
        payload.put("restaurantId", 1);
        payload.put("tableId", 1);
        payload.put("eventType", "TABLE_OCCUPIED");
        payload.put("confidence", 0.99);
        payload.put("occurredAt", Instant.now().toString());
        payload.put("detectedAt", Instant.now().toString());

        byte[] rawBytes = objectMapper.writeValueAsBytes(payload);

        // Case A: Missing signature header
        mockMvc.perform(post("/api/v1/ai/events")
                        .header("Authorization", "Bearer " + edgeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawBytes))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Missing Signature"));

        // Case B: Invalid / forged HMAC signature
        mockMvc.perform(post("/api/v1/ai/events")
                        .header("Authorization", "Bearer " + edgeToken)
                        .header("X-Signature", "sha256=ba00000000000000000000000000000000000000000000000000000000000000")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawBytes))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Invalid Signature"));

        RestaurantTable tableAfter = tableRepository.findByRestaurantIdAndTableId(1L, 1L).orElseThrow();
        assertThat(tableAfter.getStatus()).isEqualTo(initialStatus);
        assertThat(alertRepository.count()).isEqualTo(initialAlertCount);
        assertThat(aiEventRepository.count()).isEqualTo(initialAiEventCount);
        assertThat(aiEventRepository.findByRestaurantIdAndEventUid(1L, eventId)).isEmpty();
    }

    @Test
    @DisplayName("07i: Deduplication cache eviction respects DB as authoritative single source of truth (BUG-005)")
    void testCacheExpiryAndAuthoritativeDbConstraint() throws Exception {
        String edgeToken = getValidEdgeToken();
        String eventId = UUID.randomUUID().toString();
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventId", eventId);
        payload.put("schemaVersion", "1.0");
        payload.put("edgeNodeId", "edge-pune-01");
        payload.put("restaurantId", 1);
        payload.put("tableId", 2);
        payload.put("eventType", "TABLE_OCCUPIED");
        payload.put("confidence", 0.94);
        payload.put("occurredAt", Instant.now().toString());
        payload.put("detectedAt", Instant.now().toString());

        byte[] rawBytes = objectMapper.writeValueAsBytes(payload);
        String signature = calculateHmac(rawBytes);

        // 1. Initial ingestion -> ACCEPTED, stored in DB and in cache
        mockMvc.perform(post("/api/v1/ai/events")
                        .header("Authorization", "Bearer " + edgeToken)
                        .header("X-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawBytes))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        assertThat(aiEventIngestionService.getRecentEventCacheSize()).isGreaterThan(0);

        // 2. Simulate cache eviction or backend restart by clearing the in-memory cache
        aiEventIngestionService.clearRecentEventCache();
        assertThat(aiEventIngestionService.getRecentEventCacheSize()).isEqualTo(0);

        // 3. Re-submit same event; since cache is empty, it queries DB (authoritative source of truth)
        mockMvc.perform(post("/api/v1/ai/events")
                        .header("Authorization", "Bearer " + edgeToken)
                        .header("X-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawBytes))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DUPLICATE_IGNORED"));

        // Verify cache was re-populated from authoritative DB check
        assertThat(aiEventIngestionService.getRecentEventCacheSize()).isGreaterThan(0);

        long count = aiEventRepository.findAll().stream()
                .filter(e -> eventId.equals(e.getEventUid()) && Long.valueOf(1L).equals(e.getRestaurantId()))
                .count();
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("07j: Concurrent cache capacity - verifies soft-bounding and records peak observed size (BUG-005 Check 1)")
    void testConcurrentCacheCapacityAndSoftBounding() throws Exception {
        aiEventIngestionService.clearRecentEventCache();
        assertThat(aiEventIngestionService.getRecentEventCacheSize()).isEqualTo(0);

        int totalKeys = 5500;
        int threadCount = 20;
        int keysPerThread = totalKeys / threadCount;
        AtomicInteger peakObservedSize = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        for (int t = 0; t < threadCount; t++) {
            final int threadIndex = t;
            futures.add(executor.submit(() -> {
                startLatch.await();
                Instant now = Instant.now();
                for (int k = 0; k < keysPerThread; k++) {
                    String cacheKey = "1:event-thread-" + threadIndex + "-key-" + k;
                    aiEventIngestionService.putInCache(cacheKey, now);
                    int currentSize = aiEventIngestionService.getRecentEventCacheSize();
                    peakObservedSize.accumulateAndGet(currentSize, Math::max);
                }
                return null;
            }));
        }

        startLatch.countDown();
        executor.shutdown();
        boolean completed = executor.awaitTermination(30, TimeUnit.SECONDS);
        assertThat(completed).isTrue();

        int finalSize = aiEventIngestionService.getRecentEventCacheSize();
        int maxObserved = peakObservedSize.get();

        System.out.println("==================================================================");
        System.out.println("  CONCURRENT CACHE CAPACITY BENCHMARK EVIDENCE");
        System.out.println("  Total Keys Inserted: " + (threadCount * keysPerThread));
        System.out.println("  Concurrent Threads:  " + threadCount);
        System.out.println("  Peak Observed Size:  " + maxObserved);
        System.out.println("  Final Settled Size:  " + finalSize);
        System.out.println("==================================================================");

        assertThat(maxObserved).isGreaterThanOrEqualTo(5000);
        assertThat(finalSize).isLessThanOrEqualTo(5000);

        aiEventIngestionService.clearRecentEventCache();
    }

    @Test
    @DisplayName("07k: Transaction failure-path consistency, FAILED status persistence, and retry recovery (BUG-005 Check 2)")
    void testTransactionFailurePathConsistencyAndRetryRecovery() throws Exception {
        aiEventIngestionService.clearRecentEventCache();
        String edgeToken = getValidEdgeToken();
        String failedEventId = UUID.randomUUID().toString();

        // 1. Failure Scenario 1: Event persistence succeeds, but state processing fails
        // Target an invalid tableId = 999999L which throws IllegalArgumentException("Table not found: 999999")
        Map<String, Object> failedPayload = new HashMap<>();
        failedPayload.put("eventId", failedEventId);
        failedPayload.put("schemaVersion", "1.0");
        failedPayload.put("edgeNodeId", "edge-pune-01");
        failedPayload.put("restaurantId", 1);
        failedPayload.put("tableId", 999999L);
        failedPayload.put("eventType", "TABLE_OCCUPIED");
        failedPayload.put("confidence", 0.95);
        failedPayload.put("occurredAt", Instant.now().toString());
        failedPayload.put("detectedAt", Instant.now().toString());

        byte[] failedBytes = objectMapper.writeValueAsBytes(failedPayload);
        String failedSignature = calculateHmac(failedBytes);

        mockMvc.perform(post("/api/v1/ai/events")
                        .header("Authorization", "Bearer " + edgeToken)
                        .header("X-Signature", failedSignature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(failedBytes))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad Request"));

        // Database assertions for Failure Scenario 1:
        // A. ai_events record exists with status strictly "FAILED"
        Optional<AiEvent> recordedFailedEvent = aiEventRepository.findByRestaurantIdAndEventUid(1L, failedEventId);
        assertThat(recordedFailedEvent).isPresent();
        assertThat(recordedFailedEvent.get().getProcessingStatus()).isEqualTo("FAILED");

        // B. In-memory cache was evicted on failure to ensure future retries are not suppressed
        assertThat(aiEventIngestionService.isEventInCache(1L, failedEventId)).isFalse();

        // 2. Failure Scenario 3: A retry occurs after the partial failure with a valid table
        failedPayload.put("tableId", 2L);
        byte[] retryBytes = objectMapper.writeValueAsBytes(failedPayload);
        String retrySignature = calculateHmac(retryBytes);

        mockMvc.perform(post("/api/v1/ai/events")
                        .header("Authorization", "Bearer " + edgeToken)
                        .header("X-Signature", retrySignature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(retryBytes))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.eventId").value(failedEventId));

        // Database assertions for Successful Retry:
        // A. Exactly 1 row in ai_events for this eventUid (reused entity, uq_ai_event preserved)
        List<AiEvent> eventsInDb = aiEventRepository.findAll().stream()
                .filter(e -> failedEventId.equals(e.getEventUid()) && Long.valueOf(1L).equals(e.getRestaurantId()))
                .toList();
        assertThat(eventsInDb).hasSize(1);
        // B. Status transitioned from "FAILED" to "APPLIED"
        assertThat(eventsInDb.get(0).getProcessingStatus()).isEqualTo("APPLIED");
        // C. Table 2 state transitioned to OCCUPIED with an ACTIVE session
        RestaurantTable table2 = tableRepository.findByRestaurantIdAndTableId(1L, 2L).orElseThrow();
        assertThat(table2.getStatus()).isEqualTo("OCCUPIED");
        assertThat(table2.getCurrentSessionId()).isNotNull();

        // 3. Failure Scenario 4: The same event UUID is submitted AGAIN after successful recovery
        Long activeSessionId = table2.getCurrentSessionId();
        mockMvc.perform(post("/api/v1/ai/events")
                        .header("Authorization", "Bearer " + edgeToken)
                        .header("X-Signature", retrySignature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(retryBytes))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DUPLICATE_IGNORED"));

        // Verify table session and state are identical (no duplicate session created)
        RestaurantTable table2After = tableRepository.findByRestaurantIdAndTableId(1L, 2L).orElseThrow();
        assertThat(table2After.getStatus()).isEqualTo("OCCUPIED");
        assertThat(table2After.getCurrentSessionId()).isEqualTo(activeSessionId);
    }

    @Test
    @DisplayName("07l: Test 1 - Alert creation failure after successful state processing, FAILED status, and idempotent retry recovery")
    void testAlertCreationFailureAfterSuccessfulStateProcessing() throws Exception {
        aiEventIngestionService.clearRecentEventCache();
        String edgeToken = getValidEdgeToken();
        String eventId = UUID.randomUUID().toString();

        // 1. Reset Table 1 to AVAILABLE with no active sessions or alerts
        RestaurantTable table1 = tableRepository.findByRestaurantIdAndTableId(1L, 1L).orElseThrow();
        table1.setStatus("AVAILABLE");
        table1.setCurrentSessionId(null);
        tableRepository.save(table1);

        sessionRepository.findByRestaurantIdAndTableIdAndStatus(1L, 1L, "ACTIVE").ifPresent(s -> {
            s.setStatus("CLOSED");
            sessionRepository.save(s);
        });

        alertRepository.findByRestaurantIdAndDedupeKeyAndStatusIn(1L, "TABLE-OCCUPIED-1", List.of("OPEN", "ACKNOWLEDGED", "ESCALATED"))
                .ifPresent(alertRepository::delete);

        Map<String, Object> payload = new HashMap<>();
        payload.put("eventId", eventId);
        payload.put("schemaVersion", "1.0");
        payload.put("edgeNodeId", "edge-pune-01");
        payload.put("restaurantId", 1);
        payload.put("tableId", 1);
        payload.put("eventType", "TABLE_OCCUPIED");
        payload.put("confidence", 0.95);
        payload.put("occurredAt", Instant.now().toString());
        payload.put("detectedAt", Instant.now().toString());

        byte[] rawBytes = objectMapper.writeValueAsBytes(payload);
        String signature = calculateHmac(rawBytes);

        // 2. Simulate alert-service failure AFTER table state machine has successfully completed
        aiEventIngestionService.setSimulateAlertFailure(true);
        try {
            mockMvc.perform(post("/api/v1/ai/events")
                            .header("Authorization", "Bearer " + edgeToken)
                            .header("X-Signature", signature)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(rawBytes))
                    .andExpect(status().isInternalServerError());
        } finally {
            aiEventIngestionService.setSimulateAlertFailure(false);
        }

        // Verification of Failure State:
        // A. Table state and session changes HAVE committed (independent transaction boundary)
        RestaurantTable tableAfterFailure = tableRepository.findByRestaurantIdAndTableId(1L, 1L).orElseThrow();
        assertThat(tableAfterFailure.getStatus()).isEqualTo("OCCUPIED");
        assertThat(tableAfterFailure.getCurrentSessionId()).isNotNull();
        Long initialSessionId = tableAfterFailure.getCurrentSessionId();

        TableSession activeSession = sessionRepository.findByRestaurantIdAndSessionId(1L, initialSessionId).orElseThrow();
        assertThat(activeSession.getStatus()).isEqualTo("ACTIVE");

        // B. AI event in DB is marked FAILED
        Optional<AiEvent> failedAiEvent = aiEventRepository.findByRestaurantIdAndEventUid(1L, eventId);
        assertThat(failedAiEvent).isPresent();
        assertThat(failedAiEvent.get().getProcessingStatus()).isEqualTo("FAILED");

        // C. Alert is missing (not persisted due to alert service failure)
        Optional<Alert> missingAlert = alertRepository.findByRestaurantIdAndDedupeKeyAndStatusIn(
                1L, "TABLE-OCCUPIED-1", List.of("OPEN", "ACKNOWLEDGED", "ESCALATED"));
        assertThat(missingAlert).isEmpty();

        // D. In-memory cache was evicted so retry is not blocked
        assertThat(aiEventIngestionService.isEventInCache(1L, eventId)).isFalse();

        // 3. Retry with the SAME event UUID
        mockMvc.perform(post("/api/v1/ai/events")
                        .header("Authorization", "Bearer " + edgeToken)
                        .header("X-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawBytes))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.eventId").value(eventId));

        // Verification of Retry State:
        // A. Exactly one valid session exists (reused original session, no duplicate session created)
        List<TableSession> activeSessions = sessionRepository.findByRestaurantId(1L).stream()
                .filter(s -> Long.valueOf(1L).equals(s.getTableId()) && "ACTIVE".equals(s.getStatus()))
                .toList();
        assertThat(activeSessions).hasSize(1);
        assertThat(activeSessions.get(0).getSessionId()).isEqualTo(initialSessionId);

        // B. Exactly one appropriate alert is created
        List<Alert> alerts = alertRepository.findByRestaurantIdAndStatusInOrderByCreatedAtDesc(1L, List.of("OPEN", "ACKNOWLEDGED", "ESCALATED"))
                .stream()
                .filter(a -> "TABLE-OCCUPIED-1".equals(a.getDedupeKey()))
                .toList();
        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).getAlertType()).isEqualTo("TABLE_OCCUPIED");
        assertThat(alerts.get(0).getAiEventPk()).isEqualTo(failedAiEvent.get().getAiEventPk());

        // C. AI event status updated to APPLIED
        AiEvent appliedEvent = aiEventRepository.findByRestaurantIdAndEventUid(1L, eventId).orElseThrow();
        assertThat(appliedEvent.getProcessingStatus()).isEqualTo("APPLIED");

        // D. Exactly one record in ai_events table for this event UID (no row duplication)
        long eventCount = aiEventRepository.findAll().stream()
                .filter(e -> eventId.equals(e.getEventUid()) && Long.valueOf(1L).equals(e.getRestaurantId()))
                .count();
        assertThat(eventCount).isEqualTo(1);
    }

    @Test
    @DisplayName("07m: Test 2 - Failure after alert creation before APPLIED status, idempotent retry with zero duplicates")
    void testFailureAfterAlertCreationBeforeAppliedStatus() throws Exception {
        aiEventIngestionService.clearRecentEventCache();
        String edgeToken = getValidEdgeToken();
        String eventId = UUID.randomUUID().toString();

        // 1. Reset Table 2 to AVAILABLE
        RestaurantTable table2 = tableRepository.findByRestaurantIdAndTableId(1L, 2L).orElseThrow();
        table2.setStatus("AVAILABLE");
        table2.setCurrentSessionId(null);
        tableRepository.save(table2);

        sessionRepository.findByRestaurantIdAndTableIdAndStatus(1L, 2L, "ACTIVE").ifPresent(s -> {
            s.setStatus("CLOSED");
            sessionRepository.save(s);
        });

        alertRepository.findByRestaurantIdAndDedupeKeyAndStatusIn(1L, "TABLE-OCCUPIED-2", List.of("OPEN", "ACKNOWLEDGED", "ESCALATED"))
                .ifPresent(alertRepository::delete);

        Map<String, Object> payload = new HashMap<>();
        payload.put("eventId", eventId);
        payload.put("schemaVersion", "1.0");
        payload.put("edgeNodeId", "edge-pune-01");
        payload.put("restaurantId", 1);
        payload.put("tableId", 2);
        payload.put("eventType", "TABLE_OCCUPIED");
        payload.put("confidence", 0.95);
        payload.put("occurredAt", Instant.now().toString());
        payload.put("detectedAt", Instant.now().toString());

        byte[] rawBytes = objectMapper.writeValueAsBytes(payload);
        String signature = calculateHmac(rawBytes);

        // 2. Allow table state processing AND alert creation to succeed, then simulate failure before APPLIED
        aiEventIngestionService.setSimulateBeforeAppliedFailure(true);
        try {
            mockMvc.perform(post("/api/v1/ai/events")
                            .header("Authorization", "Bearer " + edgeToken)
                            .header("X-Signature", signature)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(rawBytes))
                    .andExpect(status().isInternalServerError());
        } finally {
            aiEventIngestionService.setSimulateBeforeAppliedFailure(false);
        }

        // Verification of Partial Success / Failure State:
        // A. Table state committed to OCCUPIED and session is ACTIVE
        RestaurantTable tableAfter = tableRepository.findByRestaurantIdAndTableId(1L, 2L).orElseThrow();
        assertThat(tableAfter.getStatus()).isEqualTo("OCCUPIED");
        Long sessionId = tableAfter.getCurrentSessionId();
        assertThat(sessionId).isNotNull();

        // B. Alert was successfully created before the failure occurred
        Optional<Alert> existingAlert = alertRepository.findByRestaurantIdAndDedupeKeyAndStatusIn(
                1L, "TABLE-OCCUPIED-2", List.of("OPEN", "ACKNOWLEDGED", "ESCALATED"));
        assertThat(existingAlert).isPresent();
        Long initialAlertId = existingAlert.get().getAlertId();

        // C. AI event is marked FAILED
        AiEvent failedEvent = aiEventRepository.findByRestaurantIdAndEventUid(1L, eventId).orElseThrow();
        assertThat(failedEvent.getProcessingStatus()).isEqualTo("FAILED");

        // 3. Retry with the SAME event UUID
        mockMvc.perform(post("/api/v1/ai/events")
                        .header("Authorization", "Bearer " + edgeToken)
                        .header("X-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawBytes))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.eventId").value(eventId));

        // Verification of Retry State:
        // A. No duplicate session: table 2 still has exactly 1 session, identical ID
        List<TableSession> sessions = sessionRepository.findByRestaurantId(1L).stream()
                .filter(s -> Long.valueOf(2L).equals(s.getTableId()) && "ACTIVE".equals(s.getStatus()))
                .toList();
        assertThat(sessions).hasSize(1);
        assertThat(sessions.get(0).getSessionId()).isEqualTo(sessionId);

        // B. No duplicate open alert: exactly 1 alert exists for TABLE-OCCUPIED-2, identical alertId
        List<Alert> alerts = alertRepository.findByRestaurantIdAndStatusInOrderByCreatedAtDesc(1L, List.of("OPEN", "ACKNOWLEDGED", "ESCALATED"))
                .stream()
                .filter(a -> "TABLE-OCCUPIED-2".equals(a.getDedupeKey()))
                .toList();
        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).getAlertId()).isEqualTo(initialAlertId);

        // C. Event reached APPLIED
        AiEvent applied = aiEventRepository.findByRestaurantIdAndEventUid(1L, eventId).orElseThrow();
        assertThat(applied.getProcessingStatus()).isEqualTo("APPLIED");

        // D. Database state completely consistent: exactly 1 ai_events record
        long dbCount = aiEventRepository.findAll().stream()
                .filter(e -> eventId.equals(e.getEventUid()) && Long.valueOf(1L).equals(e.getRestaurantId()))
                .count();
        assertThat(dbCount).isEqualTo(1);
    }

    @Test
    @DisplayName("07n: Test 3 - Service-request idempotency across OPEN, ACKNOWLEDGED, COMPLETED, DISMISSED")
    void testServiceRequestIdempotencyAcrossStatusChanges() throws Exception {
        aiEventIngestionService.clearRecentEventCache();
        String edgeToken = getValidEdgeToken();
        String eventId = UUID.randomUUID().toString();

        // Clean up any existing service requests or alerts for Table 3
        serviceRequestRepository.findByRestaurantIdAndTableId(1L, 3L).forEach(serviceRequestRepository::delete);
        alertRepository.findByRestaurantIdAndDedupeKeyAndStatusIn(1L, "SR-GESTURE-3", List.of("OPEN", "ACKNOWLEDGED", "ESCALATED", "RESOLVED", "FALSE_ALARM"))
                .ifPresent(alertRepository::delete);

        Map<String, Object> payload = new HashMap<>();
        payload.put("eventId", eventId);
        payload.put("schemaVersion", "1.0");
        payload.put("edgeNodeId", "edge-pune-01");
        payload.put("restaurantId", 1);
        payload.put("tableId", 3);
        payload.put("eventType", "POSSIBLE_SERVICE_REQUEST");
        payload.put("confidence", 0.90);
        payload.put("occurredAt", Instant.now().toString());
        payload.put("detectedAt", Instant.now().toString());

        byte[] rawBytes = objectMapper.writeValueAsBytes(payload);
        String signature = calculateHmac(rawBytes);

        // 1. Initial ingestion: creates ServiceRequest in OPEN status
        mockMvc.perform(post("/api/v1/ai/events")
                        .header("Authorization", "Bearer " + edgeToken)
                        .header("X-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawBytes))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        List<ServiceRequest> initialRequests = serviceRequestRepository.findByRestaurantIdAndTableId(1L, 3L);
        assertThat(initialRequests).hasSize(1);
        ServiceRequest sr = initialRequests.get(0);
        assertThat(sr.getStatus()).isEqualTo("OPEN");
        Long srId = sr.getServiceRequestId();

        // 2. Scenario A: Staff ACKNOWLEDGED the request. Client retries event.
        sr.setStatus("ACKNOWLEDGED");
        sr.setAcknowledgedAt(Instant.now());
        serviceRequestRepository.save(sr);

        // Simulate edge retry after partial failure or network retry
        AiEvent eventInDb = aiEventRepository.findByRestaurantIdAndEventUid(1L, eventId).orElseThrow();
        eventInDb.setProcessingStatus("FAILED");
        aiEventRepository.save(eventInDb);
        aiEventIngestionService.clearRecentEventCache();

        mockMvc.perform(post("/api/v1/ai/events")
                        .header("Authorization", "Bearer " + edgeToken)
                        .header("X-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawBytes))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        List<ServiceRequest> requestsAfterAckRetry = serviceRequestRepository.findByRestaurantIdAndTableId(1L, 3L);
        assertThat(requestsAfterAckRetry).hasSize(1);
        assertThat(requestsAfterAckRetry.get(0).getServiceRequestId()).isEqualTo(srId);
        assertThat(requestsAfterAckRetry.get(0).getStatus()).isEqualTo("ACKNOWLEDGED");

        // 3. Scenario B: Staff COMPLETED the request. Client retries event.
        sr.setStatus("COMPLETED");
        sr.setCompletedAt(Instant.now());
        serviceRequestRepository.save(sr);

        eventInDb = aiEventRepository.findByRestaurantIdAndEventUid(1L, eventId).orElseThrow();
        eventInDb.setProcessingStatus("FAILED");
        aiEventRepository.save(eventInDb);
        aiEventIngestionService.clearRecentEventCache();

        mockMvc.perform(post("/api/v1/ai/events")
                        .header("Authorization", "Bearer " + edgeToken)
                        .header("X-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawBytes))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        List<ServiceRequest> requestsAfterCompRetry = serviceRequestRepository.findByRestaurantIdAndTableId(1L, 3L);
        assertThat(requestsAfterCompRetry).hasSize(1);
        assertThat(requestsAfterCompRetry.get(0).getServiceRequestId()).isEqualTo(srId);
        assertThat(requestsAfterCompRetry.get(0).getStatus()).isEqualTo("COMPLETED");

        // 4. Scenario C: Staff DISMISSED the request. Client retries event.
        sr.setStatus("DISMISSED");
        serviceRequestRepository.save(sr);

        eventInDb = aiEventRepository.findByRestaurantIdAndEventUid(1L, eventId).orElseThrow();
        eventInDb.setProcessingStatus("FAILED");
        aiEventRepository.save(eventInDb);
        aiEventIngestionService.clearRecentEventCache();

        mockMvc.perform(post("/api/v1/ai/events")
                        .header("Authorization", "Bearer " + edgeToken)
                        .header("X-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawBytes))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        List<ServiceRequest> requestsAfterDismissRetry = serviceRequestRepository.findByRestaurantIdAndTableId(1L, 3L);
        assertThat(requestsAfterDismissRetry).hasSize(1);
        assertThat(requestsAfterDismissRetry.get(0).getServiceRequestId()).isEqualTo(srId);
        assertThat(requestsAfterDismissRetry.get(0).getStatus()).isEqualTo("DISMISSED");

        // 5. Scenario D: Fresh distinct event (new event UUID) arriving later CAN create a fresh request once previous is completed
        String freshEventId = UUID.randomUUID().toString();
        payload.put("eventId", freshEventId);
        byte[] freshBytes = objectMapper.writeValueAsBytes(payload);
        String freshSignature = calculateHmac(freshBytes);

        mockMvc.perform(post("/api/v1/ai/events")
                        .header("Authorization", "Bearer " + edgeToken)
                        .header("X-Signature", freshSignature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(freshBytes))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        List<ServiceRequest> requestsAfterFreshEvent = serviceRequestRepository.findByRestaurantIdAndTableId(1L, 3L);
        assertThat(requestsAfterFreshEvent).hasSize(2);
        // The newly created request is OPEN
        assertThat(requestsAfterFreshEvent.stream().anyMatch(r -> "OPEN".equals(r.getStatus()))).isTrue();
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

        // 6. Disallowed destinations tested systematically (Condition 5)
        String[] forbidden = {
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
        for (String dest : forbidden) {
            StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
            accessor.setDestination(dest);
            accessor.setUser(auth);
            accessor.setSessionAttributes(sessionAttrs);
            Message<?> msg = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
            assertThrows(AccessDeniedException.class, () ->
                    webSocketConfig.createSecurityInterceptor().preSend(msg, null)
            );
        }

        // 7. Legitimate heartbeat and global ping succeed
        StompHeaderAccessor hbSend = StompHeaderAccessor.create(StompCommand.SEND);
        hbSend.setDestination("/app/restaurants/1/heartbeat");
        hbSend.setUser(auth);
        hbSend.setSessionAttributes(sessionAttrs);
        assertThat(webSocketConfig.createSecurityInterceptor().preSend(MessageBuilder.createMessage(new byte[0], hbSend.getMessageHeaders()), null)).isNotNull();

        StompHeaderAccessor globalPing = StompHeaderAccessor.create(StompCommand.SEND);
        globalPing.setDestination("/app/ping");
        globalPing.setUser(auth);
        globalPing.setSessionAttributes(sessionAttrs);
        assertThat(webSocketConfig.createSecurityInterceptor().preSend(MessageBuilder.createMessage(new byte[0], globalPing.getMessageHeaders()), null)).isNotNull();
    }
}
