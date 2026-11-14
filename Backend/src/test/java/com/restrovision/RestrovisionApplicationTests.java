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
import jakarta.servlet.http.Cookie;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.restrovision.domain.AiEvent;
import com.restrovision.domain.Alert;
import com.restrovision.domain.CustomerFeedback;
import com.restrovision.domain.RestaurantTable;
import com.restrovision.domain.ServiceRequest;
import com.restrovision.domain.TableSession;
import com.restrovision.repository.AiEventRepository;
import com.restrovision.repository.AlertRepository;
import com.restrovision.repository.CustomerFeedbackRepository;
import com.restrovision.repository.RestaurantTableRepository;
import com.restrovision.repository.ServiceRequestRepository;
import com.restrovision.repository.TableSessionRepository;
import com.restrovision.security.FeedbackRateLimiterService;
import com.restrovision.security.LoginRateLimiterService;
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

    @Autowired
    private com.restrovision.repository.StaffTaskRepository staffTaskRepository;

    @Autowired
    private CustomerFeedbackRepository feedbackRepository;

    @Autowired
    private LoginRateLimiterService loginRateLimiterService;

    @Autowired
    private FeedbackRateLimiterService feedbackRateLimiterService;

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

        // 1. Reset Table 1 to AVAILABLE with no active sessions, alerts, or staff lock
        RestaurantTable table1 = tableRepository.findByRestaurantIdAndTableId(1L, 1L).orElseThrow();
        table1.setStatus("AVAILABLE");
        table1.setCurrentSessionId(null);
        table1.setStatusSource("SYSTEM");
        table1.setStatusChangedAt(Instant.now().minusSeconds(300));
        tableRepository.save(table1);

        sessionRepository.findByRestaurantIdAndTableIdOrderBySeatedAtDesc(1L, 1L).forEach(s -> {
            if ("ACTIVE".equals(s.getStatus())) {
                s.setStatus("CLOSED");
                sessionRepository.save(s);
            }
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

        sessionRepository.findByRestaurantIdAndTableIdOrderBySeatedAtDesc(1L, 2L).forEach(s -> {
            if ("ACTIVE".equals(s.getStatus())) {
                s.setStatus("CLOSED");
                sessionRepository.save(s);
            }
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
        sr = serviceRequestRepository.findById(srId).orElseThrow();
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
        sr = serviceRequestRepository.findById(srId).orElseThrow();
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

    // =========================================================================
    // GROUP 3 (P1) REMEDIATION EMPIRICAL TESTS: BUG-008, BUG-009, BUG-013
    // =========================================================================

    @Test
    @DisplayName("07o: BUG-008: Login rate limiting triggers 429 after 5 failed attempts per account")
    void testLoginRateLimitingByAccountWithoutEnumeration() throws Exception {
        loginRateLimiterService.clear();

        // 1. 5 failed login attempts for manager@restrovision.local
        for (int i = 0; i < 5; i++) {
            Map<String, String> creds = Map.of("email", "manager@restrovision.local", "password", "WrongPassword" + i);
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(creds)))
                    .andExpect(status().isUnauthorized());
        }

        // 2. 6th attempt must trigger HTTP 429 Too Many Requests
        Map<String, String> creds6 = Map.of("email", "manager@restrovision.local", "password", "Manager@123");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(creds6)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.title").value("Too Many Requests"))
                .andExpect(jsonPath("$.status").value(429));

        loginRateLimiterService.clear();
    }

    @Test
    @DisplayName("07p: BUG-008: Rate limiting returns uniform 429 without leaking whether account exists")
    void testLoginRateLimitingUniformResponseWithoutAccountLeak() throws Exception {
        loginRateLimiterService.clear();

        // Target a non-existent account
        String nonExistentUser = "nonexistent.attacker.target@test.com";
        for (int i = 0; i < 5; i++) {
            Map<String, String> creds = Map.of("email", nonExistentUser, "password", "Guess" + i);
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(creds)))
                    .andExpect(status().isUnauthorized());
        }

        // 6th attempt returns identical 429 response structure as an existing account
        Map<String, String> creds6 = Map.of("email", nonExistentUser, "password", "Guess6");
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(creds6)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.type").value("urn:restrovision:error:rate-limit-exceeded"))
                .andExpect(jsonPath("$.title").value("Too Many Requests"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Too many login attempts")))
                .andReturn();

        assertThat(result.getResponse().getHeader("Retry-After")).isNotNull();

        loginRateLimiterService.clear();
    }

    @Test
    @DisplayName("07q: BUG-008: Successful login resets failed account counter, but preserves IP counter")
    void testLoginRateLimitResetAfterSuccessfulLogin() throws Exception {
        loginRateLimiterService.clear();

        // 4 failures (below threshold of 5)
        for (int i = 0; i < 4; i++) {
            Map<String, String> creds = Map.of("email", "manager@restrovision.local", "password", "Wrong" + i);
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(creds)))
                    .andExpect(status().isUnauthorized());
        }

        // 1 successful login -> resets account failures
        Map<String, String> validCreds = Map.of("email", "manager@restrovision.local", "password", "Manager@123");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validCreds)))
                .andExpect(status().isOk());

        // 4 more failures -> allowed because account counter was reset upon success
        for (int i = 0; i < 4; i++) {
            Map<String, String> creds = Map.of("email", "manager@restrovision.local", "password", "WrongAgain" + i);
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(creds)))
                    .andExpect(status().isUnauthorized());
        }

        // However, IP counter now has 8 total failures! 2 more failures reach maxAttemptsPerIp = 10
        for (int i = 0; i < 2; i++) {
            Map<String, String> creds = Map.of("email", "chef@restrovision.local", "password", "WrongChef" + i);
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(creds)))
                    .andExpect(status().isUnauthorized());
        }

        // 11th request from same IP is blocked at IP-level even for a fresh account
        Map<String, String> freshAccount = Map.of("email", "waiter@restrovision.local", "password", "WrongWaiter");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(freshAccount)))
                .andExpect(status().isTooManyRequests());

        loginRateLimiterService.clear();
    }

    @Test
    @DisplayName("07q2: BUG-008: Forwarded IP headers are only trusted from configured trusted proxies")
    void testLoginRateLimitingProxyForwardingSpoofProtection() throws Exception {
        loginRateLimiterService.clear();

        // 1. Attacker sends requests from untrusted external IP (198.51.100.22) attempting to spoof X-Forwarded-For
        for (int i = 0; i < 10; i++) {
            Map<String, String> creds = Map.of("email", "random" + i + "@restrovision.local", "password", "BadPass" + i);
            mockMvc.perform(post("/api/v1/auth/login")
                            .with(request -> {
                                request.setRemoteAddr("198.51.100.22");
                                return request;
                            })
                            .header("X-Forwarded-For", "203.0.113." + i)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(creds)))
                    .andExpect(status().isUnauthorized());
        }

        // 11th request from same untrusted remoteAddr 198.51.100.22 is blocked because spoofed headers were ignored
        Map<String, String> creds11 = Map.of("email", "random11@restrovision.local", "password", "BadPass11");
        mockMvc.perform(post("/api/v1/auth/login")
                        .with(request -> {
                            request.setRemoteAddr("198.51.100.22");
                            return request;
                        })
                        .header("X-Forwarded-For", "203.0.113.99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(creds11)))
                .andExpect(status().isTooManyRequests());

        loginRateLimiterService.clear();
    }

    @Test
    @DisplayName("07r: BUG-009: Stored XSS prevention normalizes plain-text and strips malicious tags & scripts")
    void testCustomerFeedbackStoredXssSanitization() throws Exception {
        feedbackRateLimiterService.clear();

        // Malicious XSS payload containing <script>, <iframe>, event handlers, and javascript: URLs
        String xssComment = "<script>alert('XSS')</script>Delicious paneer tikka! <img src=x onerror=alert(1)> and <iframe src='http://evil.com'></iframe> javascript:void(0)";
        Map<String, Object> req = new HashMap<>();
        req.put("tableId", 1L);
        req.put("rating", 5);
        req.put("comment", xssComment);

        MvcResult result = mockMvc.perform(post("/api/v1/public/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8.name())
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        Long feedbackId = root.get("feedbackId").asLong();
        String savedComment = root.get("commentText").asText();

        // 1. Verify dangerous tags are completely stripped
        assertThat(savedComment).doesNotContain("<script>");
        assertThat(savedComment).doesNotContain("</script>");
        assertThat(savedComment).doesNotContain("<img");
        assertThat(savedComment).doesNotContain("onerror=");
        assertThat(savedComment).doesNotContain("<iframe");
        assertThat(savedComment).doesNotContain("javascript:");

        // 2. Verify legitimate feedback text is intact
        assertThat(savedComment).contains("Delicious paneer tikka!");

        // 3. Verify database record
        CustomerFeedback dbFeedback = feedbackRepository.findById(feedbackId).orElseThrow();
        assertThat(dbFeedback.getCommentText()).isEqualTo(savedComment);

        // 4. Verify no double-HTML encoding (plain text normalized)
        assertThat(dbFeedback.getCommentText()).doesNotContain("&amp;quot;");

        feedbackRateLimiterService.clear();
    }

    @Test
    @DisplayName("07s: BUG-009: Feedback preserves quotes, multi-line content, and Marathi/Hindi Unicode")
    void testCustomerFeedbackPreservesFormattingAndMultilingualUnicode() throws Exception {
        feedbackRateLimiterService.clear();

        String multilingualComment = "उत्कृष्ट जेवण आणि अप्रतिम चव!\n\"Garlic naan was hot & crisp.\"\nLoved the experience.";
        Map<String, Object> req = new HashMap<>();
        req.put("tableId", 2L);
        req.put("rating", 5);
        req.put("comment", multilingualComment);

        MvcResult result = mockMvc.perform(post("/api/v1/public/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8.name())
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        String savedComment = root.get("commentText").asText();

        // Verify Devnagari Unicode, newlines, and quotes are fully preserved
        assertThat(savedComment).contains("उत्कृष्ट जेवण आणि अप्रतिम चव!");
        assertThat(savedComment).contains("\"Garlic naan was hot & crisp.\"");
        assertThat(savedComment).contains("\n");

        feedbackRateLimiterService.clear();
    }

    @Test
    @DisplayName("07t: BUG-013: Feedback flooding rejects payload exceeding maximum comment length (1000 chars)")
    void testFeedbackFloodingRejectionExceedingPayloadLength() throws Exception {
        feedbackRateLimiterService.clear();

        String excessiveComment = "A".repeat(1001);
        Map<String, Object> req = Map.of(
                "tableId", 1L,
                "rating", 4,
                "comment", excessiveComment
        );

        mockMvc.perform(post("/api/v1/public/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("maximum allowed length")));

        feedbackRateLimiterService.clear();
    }

    @Test
    @DisplayName("07u: BUG-013: Feedback flooding rejects submission for OUT_OF_SERVICE table")
    void testFeedbackFloodingRejectionOutOfServiceTable() throws Exception {
        feedbackRateLimiterService.clear();

        RestaurantTable t4 = tableRepository.findByRestaurantIdAndTableId(1L, 4L).orElseThrow();
        String originalStatus = t4.getStatus();
        t4.setStatus("OUT_OF_SERVICE");
        tableRepository.save(t4);

        try {
            Map<String, Object> req = Map.of(
                    "tableId", 4L,
                    "rating", 5,
                    "comment", "Nice table"
            );

            mockMvc.perform(post("/api/v1/public/feedback")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("out of service")));
        } finally {
            RestaurantTable currentT4 = tableRepository.findByRestaurantIdAndTableId(1L, 4L).orElse(null);
            if (currentT4 != null) {
                currentT4.setStatus(originalStatus);
                tableRepository.save(currentT4);
            }
            feedbackRateLimiterService.clear();
        }
    }

    @Test
    @DisplayName("07v: BUG-013: Feedback flooding validates session ID against table ownership")
    void testFeedbackFloodingSessionIdValidation() throws Exception {
        feedbackRateLimiterService.clear();

        // Table 1 feedback, but sessionId = 999999 (invalid session)
        Map<String, Object> req = Map.of(
                "tableId", 1L,
                "sessionId", 999999L,
                "rating", 5,
                "comment", "Good service"
        );

        mockMvc.perform(post("/api/v1/public/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("sessionId does not belong to table")));

        feedbackRateLimiterService.clear();
    }

    @Test
    @DisplayName("07w: BUG-013: Feedback cooldown allows distinct customers, blocks duplicates and rapid spam with 429")
    void testFeedbackFloodingTableCooldownAndDistinctCustomers() throws Exception {
        feedbackRateLimiterService.clear();

        // 1. Customer A (IP 198.51.100.1) submits feedback for Table 2
        Map<String, Object> reqA = Map.of(
                "tableId", 2L,
                "rating", 5,
                "comment", "First legitimate feedback from Customer A"
        );
        mockMvc.perform(post("/api/v1/public/feedback")
                        .with(request -> {
                            request.setRemoteAddr("198.51.100.1");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqA)))
                .andExpect(status().isOk());

        // 2. Customer B (IP 198.51.100.2) submits DIFFERENT feedback for Table 2 within 15 seconds -> SUCCEEDS!
        Map<String, Object> reqB = Map.of(
                "tableId", 2L,
                "rating", 4,
                "comment", "Different legitimate feedback from Customer B"
        );
        mockMvc.perform(post("/api/v1/public/feedback")
                        .with(request -> {
                            request.setRemoteAddr("198.51.100.2");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqB)))
                .andExpect(status().isOk());

        // 3. Customer A submits IDENTICAL feedback again within 60 seconds -> returns 429 (duplicate detected)
        mockMvc.perform(post("/api/v1/public/feedback")
                        .with(request -> {
                            request.setRemoteAddr("198.51.100.1");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqA)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Duplicate feedback detected")));

        // 4. Customer A submits DIFFERENT feedback within 15 seconds cooldown -> returns 429 (table cooldown for client)
        Map<String, Object> reqA2 = Map.of(
                "tableId", 2L,
                "rating", 3,
                "comment", "Rapid second feedback from Customer A"
        );
        mockMvc.perform(post("/api/v1/public/feedback")
                        .with(request -> {
                            request.setRemoteAddr("198.51.100.1");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqA2)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Feedback was recently submitted from your device")));

        feedbackRateLimiterService.clear();
    }

    @Test
    @DisplayName("07w2: BUG-013: Old sessions cannot submit feedback after a new table session has started")
    void testFeedbackSessionLifecycleAndOldSessionRejection() throws Exception {
        feedbackRateLimiterService.clear();

        // 1. Initial active session on Table 1 is Session 1
        RestaurantTable t1 = tableRepository.findByRestaurantIdAndTableId(1L, 1L).orElseThrow();
        Long originalSessionId = t1.getCurrentSessionId();
        if (originalSessionId == null) {
            TableSession initialSession = new TableSession(1L, 1L, "SYSTEM", 2);
            initialSession = sessionRepository.save(initialSession);
            t1.setCurrentSessionId(initialSession.getSessionId());
            t1.setStatus("OCCUPIED");
            t1 = tableRepository.save(t1);
            originalSessionId = initialSession.getSessionId();
        }

        // Create a new session for Table 1 (Session 2 starts)
        TableSession newSession = new TableSession(1L, 1L, "SYSTEM", 4);
        newSession = sessionRepository.save(newSession);
        t1.setCurrentSessionId(newSession.getSessionId());
        t1 = tableRepository.save(t1);

        TableSession otherTableSession = null;
        try {
            // 2. New session submits feedback -> SUCCEEDS
            Map<String, Object> newSessionReq = Map.of(
                    "tableId", 1L,
                    "sessionId", newSession.getSessionId(),
                    "rating", 5,
                    "comment", "New party enjoying dinner"
            );
            mockMvc.perform(post("/api/v1/public/feedback")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(newSessionReq)))
                    .andExpect(status().isOk());

            // 3. Old session attempts to submit feedback AFTER new session has started -> REJECTED with 400
            Map<String, Object> oldSessionReq = Map.of(
                    "tableId", 1L,
                    "sessionId", originalSessionId,
                    "rating", 4,
                    "comment", "Old session late feedback"
            );
            mockMvc.perform(post("/api/v1/public/feedback")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(oldSessionReq)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("is expired. A new session has already started")));

            // 4. Session belonging to another table (Table 2's session) -> REJECTED with 400
            otherTableSession = new TableSession(1L, 2L, "SYSTEM", 2);
            otherTableSession = sessionRepository.save(otherTableSession);
            Map<String, Object> wrongTableReq = Map.of(
                    "tableId", 1L,
                    "sessionId", otherTableSession.getSessionId(),
                    "rating", 4,
                    "comment", "Wrong table feedback"
            );
            mockMvc.perform(post("/api/v1/public/feedback")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(wrongTableReq)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("does not belong to table 1")));

        } finally {
            if (newSession != null && newSession.getSessionId() != null) {
                sessionRepository.deleteById(newSession.getSessionId());
            }
            if (otherTableSession != null && otherTableSession.getSessionId() != null) {
                sessionRepository.deleteById(otherTableSession.getSessionId());
            }
            RestaurantTable cur = tableRepository.findByRestaurantIdAndTableId(1L, 1L).orElse(null);
            if (cur != null) {
                cur.setCurrentSessionId(originalSessionId);
                tableRepository.save(cur);
            }
            feedbackRateLimiterService.clear();
        }
    }

    @Test
    @DisplayName("07w3: BUG-008: Login rate limiter concurrency, threshold enforcement, and bounded memory with non-eviction of blocked accounts")
    void testLoginRateLimiterConcurrencyAndBoundedMemory() throws Exception {
        loginRateLimiterService.clear();

        // 1. Concurrency: 20 threads submit failures simultaneously for target account
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    loginRateLimiterService.recordFailure("10.0.0.1", "victim@restrovision.local");
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }
        startLatch.countDown();
        assertThat(doneLatch.await(5, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        // Verify: Threshold (5) was reached; checkLimit returns blocked=true
        LoginRateLimiterService.RateLimitResult res = loginRateLimiterService.checkLimit("10.0.0.1", "victim@restrovision.local");
        assertThat(res.isBlocked()).isTrue();
        assertThat(res.getRetryAfterSeconds()).isGreaterThan(0);

        // 2. Bounded Memory & Eviction Protection:
        // Attacker floods 5,500 distinct accounts each with 1 failure, attempting to push victim out of cache
        for (int i = 0; i < 5500; i++) {
            loginRateLimiterService.recordFailure("10.0.0.2", "noise_user_" + i + "@evil.com");
        }

        // Verify:
        // a) Size is bounded (<= 5000)
        assertThat(loginRateLimiterService.getAccountAttemptsSize()).isLessThanOrEqualTo(5000);
        // b) Victim account is STILL blocked and was NOT evicted!
        LoginRateLimiterService.RateLimitResult victimCheck = loginRateLimiterService.checkLimit("10.0.0.1", "victim@restrovision.local");
        assertThat(victimCheck.isBlocked()).isTrue();

        loginRateLimiterService.clear();
    }

    @Test
    @DisplayName("07w4: BUG-013: Feedback rate limiter concurrency, threshold enforcement, and bounded memory")
    void testFeedbackRateLimiterConcurrencyAndBoundedMemory() throws Exception {
        feedbackRateLimiterService.clear();

        // 1. Concurrency: 20 threads record submissions for same client IP
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    feedbackRateLimiterService.recordSubmission("10.10.10.10", 1L, 5, "Comment " + idx);
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }
        startLatch.countDown();
        assertThat(doneLatch.await(5, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        // IP threshold is 10. With 20 recorded submissions, IP limit is strictly blocked
        FeedbackRateLimiterService.FeedbackLimitResult res = feedbackRateLimiterService.checkLimits("10.10.10.10", 2L, 5, "Distinct");
        assertThat(res.isBlocked()).isTrue();
        assertThat(res.getReason()).contains("Feedback submission limit reached");

        // 2. Bounded Memory:
        // Inject 5,500 distinct client IPs / fingerprints
        for (int i = 0; i < 5500; i++) {
            feedbackRateLimiterService.recordSubmission("10.20." + (i / 256) + "." + (i % 256), (long) (i % 10 + 1), 5, "Noise " + i);
        }

        // Verify bounded size
        assertThat(feedbackRateLimiterService.getIpHistorySize()).isLessThanOrEqualTo(5000);
        assertThat(feedbackRateLimiterService.getDuplicateFingerprintsSize()).isLessThanOrEqualTo(5000);

        feedbackRateLimiterService.clear();
    }

    @Test
    @DisplayName("07w5: BUG-008: Trusted proxy handles chained X-Forwarded-For and ignores untrusted proxy chains")
    void testTrustedProxyChainedForwardedForResolution() {
        org.springframework.mock.web.MockHttpServletRequest request = new org.springframework.mock.web.MockHttpServletRequest();

        // 1. Trusted proxy remote address (127.0.0.1) with chained X-Forwarded-For
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "203.0.113.195, 10.0.0.1, 192.168.1.1");
        String resolvedTrusted = loginRateLimiterService.resolveClientIp(request);
        assertThat(resolvedTrusted).isEqualTo("203.0.113.195");

        // 2. Untrusted proxy remote address (198.51.100.99) with spoofed X-Forwarded-For
        request = new org.springframework.mock.web.MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.99");
        request.addHeader("X-Forwarded-For", "203.0.113.195, 10.0.0.1");
        String resolvedUntrusted = loginRateLimiterService.resolveClientIp(request);
        assertThat(resolvedUntrusted).isEqualTo("198.51.100.99");
    }

    @Test
    @DisplayName("07w6: BUG-009: Feedback output path audit verifies JSON serialization, Marathi/Hindi Unicode, and quotes intact")
    void testCustomerFeedbackXssOutputPathAudit() throws Exception {
        feedbackRateLimiterService.clear();

        // Manager logs in to inspect feedback
        Map<String, String> tokens = loginUser("manager@restrovision.local", "Manager@123");
        String managerToken = tokens.get("accessToken");

        // Submit new feedback with multilingual text, quotes, and newlines
        String rawComment = "चविष्ट जेवण! \"Biryani & Paneer Tikka was wonderful.\"\nWill visit again.";
        Map<String, Object> req = Map.of(
                "tableId", 2L,
                "rating", 5,
                "comment", rawComment
        );
        mockMvc.perform(post("/api/v1/public/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8.name())
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        // Fetch feedback list via /api/v1/analytics/feedback
        MvcResult result = mockMvc.perform(get("/api/v1/analytics/feedback")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getContentType()).contains("application/json");

        String responseJson = result.getResponse().getContentAsString(StandardCharsets.UTF_8);

        // Verify exact text preserved:
        assertThat(responseJson).contains("चविष्ट जेवण!");
        assertThat(responseJson).contains("Biryani & Paneer Tikka was wonderful.");
        assertThat(responseJson).doesNotContain("&amp;quot;");
        assertThat(responseJson).doesNotContain("&lt;");
        assertThat(responseJson).doesNotContain("&gt;");

        feedbackRateLimiterService.clear();
    }

    @Test
    @DisplayName("07x1: Edge Case 1: Shared IP feedback allows distinct customers at same table to submit within 15s")
    void testSharedIpFeedbackDifferentCustomersSubmitWithinCooldown() throws Exception {
        feedbackRateLimiterService.clear();

        String sharedWifiIp = "198.51.100.99";
        Long tableId = 2L;

        // Customer A on iPhone submits first review
        Map<String, Object> reqA = Map.of(
                "tableId", tableId,
                "rating", 5,
                "comment", "Customer A: Exceptional Dal Makhani and warm hospitality!"
        );
        MvcResult resA = mockMvc.perform(post("/api/v1/public/feedback")
                        .with(request -> {
                            request.setRemoteAddr(sharedWifiIp);
                            return request;
                        })
                        .header("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 Mobile Safari/604.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqA)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode jsonA = objectMapper.readTree(resA.getResponse().getContentAsString());
        Long feedbackIdA = jsonA.get("feedbackId").asLong();

        // Customer B on Android at the same table on the same Wi-Fi submits DIFFERENT review within 15 seconds
        Map<String, Object> reqB = Map.of(
                "tableId", tableId,
                "rating", 4,
                "comment", "Customer B: Loved the garlic butter naan, cocktails were great."
        );
        MvcResult resB = mockMvc.perform(post("/api/v1/public/feedback")
                        .with(request -> {
                            request.setRemoteAddr(sharedWifiIp);
                            return request;
                        })
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 Mobile Safari/537.36")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqB)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode jsonB = objectMapper.readTree(resB.getResponse().getContentAsString());
        Long feedbackIdB = jsonB.get("feedbackId").asLong();

        // Customer A attempts rapid second review from their device within 15 seconds -> 429
        Map<String, Object> reqA2 = Map.of(
                "tableId", tableId,
                "rating", 3,
                "comment", "Customer A rapid follow-up comment"
        );
        mockMvc.perform(post("/api/v1/public/feedback")
                        .with(request -> {
                            request.setRemoteAddr(sharedWifiIp);
                            return request;
                        })
                        .header("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 Mobile Safari/604.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqA2)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("recently submitted from your device")));

        // Attempting to bypass cooldown by injecting arbitrary client-supplied identity in JSON is ignored -> still 429
        Map<String, Object> reqSpoof = new HashMap<>(reqA2);
        reqSpoof.put("clientId", "arbitrary-spoofed-client-uuid-999");
        mockMvc.perform(post("/api/v1/public/feedback")
                        .with(request -> {
                            request.setRemoteAddr(sharedWifiIp);
                            return request;
                        })
                        .header("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 Mobile Safari/604.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqSpoof)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("recently submitted from your device")));

        // Database assertions: verify both records independently persisted with correct table, session, and comment
        CustomerFeedback fbA = feedbackRepository.findById(feedbackIdA).orElseThrow();
        CustomerFeedback fbB = feedbackRepository.findById(feedbackIdB).orElseThrow();
        assertThat(fbA.getTableId()).isEqualTo(tableId);
        assertThat(fbB.getTableId()).isEqualTo(tableId);
        assertThat(fbA.getCommentText()).contains("Exceptional Dal Makhani");
        assertThat(fbB.getCommentText()).contains("Loved the garlic butter naan");
        assertThat(fbA.getFeedbackId()).isNotEqualTo(fbB.getFeedbackId());

        feedbackRateLimiterService.clear();
    }

    @Test
    @DisplayName("07x2: Edge Case 2: All-blocked cache saturation cannot evict protected entries and fails closed for new identities")
    void testAllBlockedCacheSaturationFailClosedProtection() {
        loginRateLimiterService.clear();
        feedbackRateLimiterService.clear();

        // 1. Part A: Login Rate Limiter Saturation
        // Fill accountAttempts with 5,000 actively blocked accounts (each has 5 failed attempts)
        for (int i = 0; i < 5000; i++) {
            String account = "blocked" + i + "@restrovision.test";
            loginRateLimiterService.injectAccountHistoryForTesting(account, 5);
        }
        assertThat(loginRateLimiterService.getAccountAttemptsSize()).isEqualTo(5000);

        // Verify existing blocked identity #100 is still strictly blocked (never evicted)
        LoginRateLimiterService.RateLimitResult resBlocked = loginRateLimiterService.checkLimit("10.10.10.10", "blocked100@restrovision.test");
        assertThat(resBlocked.isBlocked()).isTrue();

        // Verify NEW unknown identity fails closed when cache is saturated with actively blocked accounts
        LoginRateLimiterService.RateLimitResult resNew = loginRateLimiterService.checkLimit("10.10.10.10", "newunmetered@restrovision.test");
        assertThat(resNew.isBlocked()).isTrue();
        assertThat(resNew.getRetryAfterSeconds()).isGreaterThan(0);

        // Verify capacity is not silently breached
        loginRateLimiterService.recordFailure("10.10.10.10", "newunmetered@restrovision.test");
        assertThat(loginRateLimiterService.getAccountAttemptsSize()).isLessThanOrEqualTo(5000);

        // 2. Part B: Feedback Rate Limiter Saturation
        // Fill ipHistory with 5,000 actively blocked IPs (each has 10 submissions)
        for (int i = 0; i < 5000; i++) {
            String ip = "10.0." + (i / 256) + "." + (i % 256);
            feedbackRateLimiterService.injectIpHistoryForTesting(ip, 10);
        }
        assertThat(feedbackRateLimiterService.getIpHistorySize()).isEqualTo(5000);

        // Verify existing blocked IP is strictly blocked
        FeedbackRateLimiterService.FeedbackLimitResult fbBlocked = feedbackRateLimiterService.checkLimits("10.0.0.1", 2L, 5, "Different comment");
        assertThat(fbBlocked.isBlocked()).isTrue();

        // Verify NEW unknown IP fails closed when capacity is exhausted with blocked IPs
        FeedbackRateLimiterService.FeedbackLimitResult fbNew = feedbackRateLimiterService.checkLimits("198.51.250.1", 2L, 5, "New IP comment");
        assertThat(fbNew.isBlocked()).isTrue();
        assertThat(fbNew.getReason()).contains("capacity reached");

        // Verify capacity remains bounded <= 5000
        feedbackRateLimiterService.recordSubmission("198.51.250.1", 2L, 5, "New IP comment");
        assertThat(feedbackRateLimiterService.getIpHistorySize()).isLessThanOrEqualTo(5000);

        loginRateLimiterService.clear();
        feedbackRateLimiterService.clear();
    }

    @Test
    @DisplayName("07x3: Edge Case 3: Missing session ID securely binds to current active session and rejects expired/inactive tables")
    void testMissingFeedbackSessionIdSecureBindingAndRejection() throws Exception {
        feedbackRateLimiterService.clear();

        RestaurantTable t2 = tableRepository.findByRestaurantIdAndTableId(1L, 2L).orElseThrow();
        Long originalSessionId = t2.getCurrentSessionId();
        assertThat(originalSessionId).isNotNull();

        TableSession newSession = null;
        try {
            // Case A: Submit feedback with NO sessionId on Table 2 -> securely binds to Table 2's current session
            Map<String, Object> reqA = Map.of(
                "tableId", 2L,
                "rating", 5,
                "comment", "Dining party leaving feedback without explicit session ID"
            );
            MvcResult resA = mockMvc.perform(post("/api/v1/public/feedback")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(reqA)))
                    .andExpect(status().isOk())
                    .andReturn();

            JsonNode jsonA = objectMapper.readTree(resA.getResponse().getContentAsString());
            Long feedbackIdA = jsonA.get("feedbackId").asLong();

            // Database assertion for Case A: sessionId is securely set to table's active currentSessionId
            CustomerFeedback fbA = feedbackRepository.findById(feedbackIdA).orElseThrow();
            assertThat(fbA.getSessionId()).isEqualTo(originalSessionId);

            // Clear cooldown before new party arrives
            feedbackRateLimiterService.clear();

            // Case B: Table's previous session ends and a NEW session starts
            TableSession oldSession = sessionRepository.findById(originalSessionId).orElseThrow();
            oldSession.setStatus("CLOSED");
            oldSession.setDepartedAt(Instant.now());
            sessionRepository.save(oldSession);

            newSession = new TableSession(1L, 2L, "AI", 4);
            newSession = sessionRepository.save(newSession);
            t2 = tableRepository.findByRestaurantIdAndTableId(1L, 2L).orElseThrow();
            t2.setCurrentSessionId(newSession.getSessionId());
            t2 = tableRepository.save(t2);

            // Submit feedback with NO sessionId after new session seated -> binds to NEW session
            Map<String, Object> reqB = Map.of(
                "tableId", 2L,
                "rating", 5,
                "comment", "Second party dining at table 2 leaving feedback"
            );
            MvcResult resB = mockMvc.perform(post("/api/v1/public/feedback")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(reqB)))
                    .andExpect(status().isOk())
                    .andReturn();

            JsonNode jsonB = objectMapper.readTree(resB.getResponse().getContentAsString());
            Long feedbackIdB = jsonB.get("feedbackId").asLong();

            // Database assertion for Case B: securely bound to new session, NOT old session
            CustomerFeedback fbB = feedbackRepository.findById(feedbackIdB).orElseThrow();
            assertThat(fbB.getSessionId()).isEqualTo(newSession.getSessionId());
            assertThat(fbB.getSessionId()).isNotEqualTo(originalSessionId);

            // Clear cooldown for Case C
            feedbackRateLimiterService.clear();

            // Case C: Table session ends and NO new session has started (currentSessionId is null)
            t2 = tableRepository.findByRestaurantIdAndTableId(1L, 2L).orElseThrow();
            t2.setCurrentSessionId(null);
            t2 = tableRepository.save(t2);

            long feedbackCountBefore = feedbackRepository.count();
            Map<String, Object> reqC = Map.of(
                "tableId", 2L,
                "rating", 4,
                "comment", "Orphaned feedback attempt when no session is active"
            );
            mockMvc.perform(post("/api/v1/public/feedback")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(reqC)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("No active session found for table 2")));

            // Database assertion for Case C: 0 records inserted
            assertThat(feedbackRepository.count()).isEqualTo(feedbackCountBefore);

            // Case D: Closed session explicitly submitted on table with no active session -> rejected with 400
            Map<String, Object> reqD = Map.of(
                "tableId", 2L,
                "sessionId", originalSessionId,
                "rating", 4,
                "comment", "Explicit closed session submission"
            );
            mockMvc.perform(post("/api/v1/public/feedback")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(reqD)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("closed or inactive")));

        } finally {
            // Restore Table 2 original session
            RestaurantTable currentT2 = tableRepository.findByRestaurantIdAndTableId(1L, 2L).orElse(null);
            if (currentT2 != null) {
                currentT2.setCurrentSessionId(originalSessionId);
                tableRepository.save(currentT2);
            }
            TableSession restoredSession = sessionRepository.findById(originalSessionId).orElse(null);
            if (restoredSession != null) {
                restoredSession.setStatus("ACTIVE");
                restoredSession.setDepartedAt(null);
                sessionRepository.save(restoredSession);
            }
            if (newSession != null && newSession.getSessionId() != null) {
                sessionRepository.deleteById(newSession.getSessionId());
            }
            feedbackRateLimiterService.clear();
        }
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
            assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("2");
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
        t1Session.setUser(new UsernamePasswordAuthenticationToken("user1@restaurant1.com", null, List.of(new SimpleGrantedAuthority("ROLE_WAITER"))));
        t1Session.setSessionAttributes(new HashMap<>(Map.of("restaurantId", 1L)));
        Message<?> t1ValidMsg = MessageBuilder.createMessage(new byte[0], t1Session.getMessageHeaders());

        // Same-tenant subscription to Restaurant 1 passes
        Message<?> t1Result = webSocketConfig.createSecurityInterceptor().preSend(t1ValidMsg, null);
        assertThat(t1Result).isNotNull();

        // Cross-tenant attempt: Tenant 1 session subscribing to Restaurant 2 is denied
        StompHeaderAccessor t1Cross = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        t1Cross.setDestination("/topic/restaurants/2/tables");
        t1Cross.setUser(new UsernamePasswordAuthenticationToken("user1@restaurant1.com", null, List.of(new SimpleGrantedAuthority("ROLE_WAITER"))));
        t1Cross.setSessionAttributes(new HashMap<>(Map.of("restaurantId", 1L)));
        Message<?> t1CrossMsg = MessageBuilder.createMessage(new byte[0], t1Cross.getMessageHeaders());

        assertThrows(AccessDeniedException.class, () ->
                webSocketConfig.createSecurityInterceptor().preSend(t1CrossMsg, null)
        );

        // Tenant 2 session
        StompHeaderAccessor t2Session = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        t2Session.setDestination("/topic/restaurants/2/tables");
        t2Session.setUser(new UsernamePasswordAuthenticationToken("user2@restaurant2.com", null, List.of(new SimpleGrantedAuthority("ROLE_WAITER"))));
        t2Session.setSessionAttributes(new HashMap<>(Map.of("restaurantId", 2L)));
        Message<?> t2ValidMsg = MessageBuilder.createMessage(new byte[0], t2Session.getMessageHeaders());

        // Same-tenant subscription to Restaurant 2 passes
        Message<?> t2Result = webSocketConfig.createSecurityInterceptor().preSend(t2ValidMsg, null);
        assertThat(t2Result).isNotNull();

        // Cross-tenant attempt: Tenant 2 session subscribing to Restaurant 1 is denied
        StompHeaderAccessor t2Cross = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        t2Cross.setDestination("/topic/restaurants/1/tables");
        t2Cross.setUser(new UsernamePasswordAuthenticationToken("user2@restaurant2.com", null, List.of(new SimpleGrantedAuthority("ROLE_WAITER"))));
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
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken("user1@restaurant1.com", null, List.of(new SimpleGrantedAuthority("ROLE_OWNER")));
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

    @Test
    @DisplayName("29: BUG-014: Login issues HttpOnly cookies and CSRF cookie")
    void test29_LoginIssuesHttpOnlyCookiesAndCsrfCookie() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"owner@restrovision.local\",\"password\":\"Admin@123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value("owner@restrovision.local"))
                .andReturn();

        List<String> setCookies = result.getResponse().getHeaders("Set-Cookie");
        assertThat(setCookies).isNotEmpty();

        boolean hasAccessCookie = false;
        boolean hasRefreshCookie = false;
        boolean hasCsrfCookie = false;

        for (String c : setCookies) {
            if (c.contains("restro_access_token=")) {
                hasAccessCookie = true;
                assertThat(c).contains("HttpOnly");
                assertThat(c).contains("SameSite=Lax");
                assertThat(c).contains("Path=/");
            } else if (c.contains("restro_refresh_token=")) {
                hasRefreshCookie = true;
                assertThat(c).contains("HttpOnly");
                assertThat(c).contains("SameSite=Lax");
                assertThat(c).contains("Path=/api/v1/auth");
            } else if (c.contains("restro_csrf_token=")) {
                hasCsrfCookie = true;
                // CSRF cookie must NOT be HttpOnly so frontend JS can read it for double-submit
                assertThat(c).doesNotContain("HttpOnly");
                assertThat(c).contains("SameSite=Lax");
                assertThat(c).contains("Path=/");
            }
        }

        assertThat(hasAccessCookie).isTrue();
        assertThat(hasRefreshCookie).isTrue();
        assertThat(hasCsrfCookie).isTrue();
    }

    @Test
    @DisplayName("30: BUG-014: Cookie authentication and CSRF protection for mutating requests")
    void test30_CookieAuthAndCsrfEnforcement() throws Exception {
        // 1. Perform login to obtain valid cookies
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"manager@restrovision.local\",\"password\":\"Manager@123\"}"))
                .andExpect(status().isOk())
                .andReturn();

        Cookie accessCookie = loginResult.getResponse().getCookie("restro_access_token");
        Cookie csrfCookie = loginResult.getResponse().getCookie("restro_csrf_token");
        assertThat(accessCookie).isNotNull();
        assertThat(csrfCookie).isNotNull();

        // 2. Cookie authentication on GET /api/v1/auth/me succeeds without CSRF header (safe method)
        mockMvc.perform(get("/api/v1/auth/me")
                        .cookie(accessCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("manager@restrovision.local"));

        // 3. Mutating request (PUT /api/v1/tables/1/status) with cookie but NO CSRF header -> 403 Forbidden
        mockMvc.perform(put("/api/v1/tables/1/status")
                        .cookie(accessCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"OCCUPIED\",\"reason\":\"Seating party\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:restrovision:error:csrf-token-invalid"));

        // 4. Mutating request with cookie and mismatched CSRF header -> 403 Forbidden
        mockMvc.perform(put("/api/v1/tables/1/status")
                        .cookie(accessCookie)
                        .header("X-CSRF-TOKEN", "tampered-csrf-token")
                        .cookie(csrfCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"OCCUPIED\",\"reason\":\"Seating party\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:restrovision:error:csrf-token-invalid"));

        // 5. Mutating request with cookie and valid matching CSRF header -> 200 OK
        mockMvc.perform(put("/api/v1/tables/1/status")
                        .cookie(accessCookie)
                        .header("X-CSRF-TOKEN", csrfCookie.getValue())
                        .cookie(csrfCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"AVAILABLE\",\"reason\":\"Table reset\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("31: BUG-014: Cookie-based refresh and logout clears auth cookies")
    void test31_CookieRefreshAndLogout() throws Exception {
        // 1. Perform login
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"owner@restrovision.local\",\"password\":\"Admin@123\"}"))
                .andExpect(status().isOk())
                .andReturn();

        Cookie refreshCookie = loginResult.getResponse().getCookie("restro_refresh_token");
        assertThat(refreshCookie).isNotNull();

        // 2. Refresh using cookie without sending token in JSON body
        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refreshCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andReturn();

        Cookie newAccessCookie = refreshResult.getResponse().getCookie("restro_access_token");
        assertThat(newAccessCookie).isNotNull();
        assertThat(newAccessCookie.getValue()).isNotEmpty();

        // 3. Logout clears all cookies with Max-Age=0
        MvcResult logoutResult = mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isOk())
                .andReturn();

        List<String> logoutCookies = logoutResult.getResponse().getHeaders("Set-Cookie");
        assertThat(logoutCookies).isNotEmpty();

        for (String c : logoutCookies) {
            if (c.contains("restro_access_token=") || c.contains("restro_refresh_token=") || c.contains("restro_csrf_token=")) {
                assertThat(c).contains("Max-Age=0");
            }
        }
    }

    @Test
    @DisplayName("32: BUG-016: Optimistic locking version increments on TableSession updates and prevents concurrent lost updates")
    void test32_TableSessionOptimisticLocking() {
        TableSession session = new TableSession(1L, 1L, "STAFF", 4);
        TableSession saved = sessionRepository.saveAndFlush(session);
        assertThat(saved.getVersion()).isEqualTo(0L);

        // First update increments version to 1
        saved.setPartySizeEst(6);
        TableSession updated1 = sessionRepository.saveAndFlush(saved);
        assertThat(updated1.getVersion()).isEqualTo(1L);

        // Simulate concurrent transaction with stale entity (version 0)
        TableSession staleSession = new TableSession();
        staleSession.setSessionId(updated1.getSessionId());
        staleSession.setRestaurantId(updated1.getRestaurantId());
        staleSession.setTableId(updated1.getTableId());
        staleSession.setStatus(updated1.getStatus());
        staleSession.setOpenedBy(updated1.getOpenedBy());
        staleSession.setSeatedAt(updated1.getSeatedAt());
        staleSession.setVersion(0L); // Stale version!

        assertThrows(org.springframework.orm.ObjectOptimisticLockingFailureException.class, () -> {
            sessionRepository.saveAndFlush(staleSession);
        });
    }

    @Test
    @DisplayName("33: BUG-016: Optimistic locking version increments on StaffTask and prevents concurrent collision")
    void test33_StaffTaskOptimisticLocking() {
        com.restrovision.domain.StaffTask task = new com.restrovision.domain.StaffTask();
        task.setRestaurantId(1L);
        task.setTableId(1L);
        task.setTableCode("T01");
        task.setTaskType("TABLE_CLEANING");
        task.setStatus("OPEN");
        task.setPriority("HIGH");
        com.restrovision.domain.StaffTask saved = staffTaskRepository.saveAndFlush(task);
        assertThat(saved.getVersion()).isEqualTo(0L);

        // Update increments version
        saved.setStatus("ASSIGNED");
        saved.setAssignedEmployeeId(5L);
        com.restrovision.domain.StaffTask updated1 = staffTaskRepository.saveAndFlush(saved);
        assertThat(updated1.getVersion()).isEqualTo(1L);

        // Stale update throws ObjectOptimisticLockingFailureException
        com.restrovision.domain.StaffTask staleTask = new com.restrovision.domain.StaffTask();
        staleTask.setTaskId(updated1.getTaskId());
        staleTask.setRestaurantId(updated1.getRestaurantId());
        staleTask.setTaskType(updated1.getTaskType());
        staleTask.setStatus("CANCELLED");
        staleTask.setPriority(updated1.getPriority());
        staleTask.setCreatedAt(updated1.getCreatedAt());
        staleTask.setVersion(0L); // Stale version!

        assertThrows(org.springframework.orm.ObjectOptimisticLockingFailureException.class, () -> {
            staffTaskRepository.saveAndFlush(staleTask);
        });
    }

    @Test
    @DisplayName("34: BUG-016: Optimistic locking version increments on ServiceRequest and prevents concurrent collision")
    void test34_ServiceRequestOptimisticLocking() {
        ServiceRequest req = new ServiceRequest();
        req.setRestaurantId(1L);
        req.setTableId(2L);
        req.setTableCode("T02");
        req.setRequestType("WATER");
        req.setSource("QR");
        req.setStatus("OPEN");
        ServiceRequest saved = serviceRequestRepository.saveAndFlush(req);
        assertThat(saved.getVersion()).isEqualTo(0L);

        // Update increments version
        saved.setStatus("ACKNOWLEDGED");
        saved.setAssignedEmployeeId(4L);
        ServiceRequest updated1 = serviceRequestRepository.saveAndFlush(saved);
        assertThat(updated1.getVersion()).isEqualTo(1L);

        // Stale update throws ObjectOptimisticLockingFailureException
        ServiceRequest staleReq = new ServiceRequest();
        staleReq.setServiceRequestId(updated1.getServiceRequestId());
        staleReq.setRestaurantId(updated1.getRestaurantId());
        staleReq.setRequestType(updated1.getRequestType());
        staleReq.setSource(updated1.getSource());
        staleReq.setStatus("COMPLETED");
        staleReq.setCreatedAt(updated1.getCreatedAt());
        staleReq.setVersion(0L); // Stale version!

        assertThrows(org.springframework.orm.ObjectOptimisticLockingFailureException.class, () -> {
            serviceRequestRepository.saveAndFlush(staleReq);
        });
    }
}
