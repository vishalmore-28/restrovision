package com.restrovision;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restrovision.domain.EdgeNode;
import com.restrovision.domain.Restaurant;
import com.restrovision.repository.EdgeNodeRepository;
import com.restrovision.repository.RestaurantRepository;
import com.restrovision.security.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class EdgeSecurityProductionVerificationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private EdgeNodeRepository edgeNodeRepository;

    @Autowired
    private RestaurantRepository restaurantRepository;

    private static final String EDGE_HMAC_SECRET = "8f4a2b1c9e3d7a5f6e8b0c2d4e6f8a0b1c3d5e7f9a1b3c5d7e9f1a3b5c7d9e1f";

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

    private Map<String, Object> createEventPayload(String edgeNodeId, Long restaurantId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventId", UUID.randomUUID().toString());
        payload.put("schemaVersion", "1.0");
        payload.put("edgeNodeId", edgeNodeId);
        payload.put("restaurantId", restaurantId);
        payload.put("tableId", 1);
        payload.put("eventType", "TABLE_OCCUPIED");
        payload.put("confidence", 0.95);
        return payload;
    }

    @Test
    @DisplayName("Edge Security 1: Immediate Revocation - Deactivating device in DB immediately invalidates unexpired token")
    void testImmediateDeviceRevocation() throws Exception {
        String nodeId = "edge-revocable-01";
        EdgeNode node = new EdgeNode(nodeId, 1L, "Temporary Revocable Edge Node", "ONLINE");
        edgeNodeRepository.save(node);

        // Generate a 30-day token
        String validToken = tokenProvider.generateEdgeToken(nodeId, 1L);

        // 1. Initial request succeeds
        byte[] rawBytes = objectMapper.writeValueAsBytes(createEventPayload(nodeId, 1L));
        mockMvc.perform(post("/api/v1/ai/events")
                        .header("Authorization", "Bearer " + validToken)
                        .header("X-Signature", calculateHmac(rawBytes))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawBytes))
                .andExpect(status().isOk());

        // 2. Administrator revokes device by setting status to REVOKED in database
        node.setStatus("REVOKED");
        edgeNodeRepository.save(node);

        // 3. Next request with the SAME unexpired token is IMMEDIATELY rejected with 401
        byte[] rawBytes2 = objectMapper.writeValueAsBytes(createEventPayload(nodeId, 1L));
        mockMvc.perform(post("/api/v1/ai/events")
                        .header("Authorization", "Bearer " + validToken)
                        .header("X-Signature", calculateHmac(rawBytes2))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawBytes2))
                .andExpect(status().isUnauthorized());

        System.out.println("[Edge Security Test] Verified: Immediate revocation in DB rejects unexpired token with 401.");
    }

    @Test
    @DisplayName("Edge Security 2: Token Expiry - Expired device token is rejected with 401")
    void testExpiredDeviceTokenRejected() throws Exception {
        // Generate an expired edge device token (-1 second)
        String expiredToken = tokenProvider.generateEdgeToken("edge-pune-01", 1L, -1000L);

        byte[] rawBytes = objectMapper.writeValueAsBytes(createEventPayload("edge-pune-01", 1L));
        mockMvc.perform(post("/api/v1/ai/events")
                        .header("Authorization", "Bearer " + expiredToken)
                        .header("X-Signature", calculateHmac(rawBytes))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawBytes))
                .andExpect(status().isUnauthorized());

        System.out.println("[Edge Security Test] Verified: Expired device token rejected with 401.");
    }

    @Test
    @DisplayName("Edge Security 3: RBAC on Provisioning - Waiter role is forbidden from generating edge tokens")
    void testProvisioningRbacRestricted() throws Exception {
        // Waiter attempts to generate an edge token
        String waiterToken = tokenProvider.generateAccessToken(2L, 1L, "waiter1@restrovision.local", Set.of("WAITER"));

        mockMvc.perform(post("/api/v1/cameras/edge-token")
                        .header("Authorization", "Bearer " + waiterToken)
                        .param("edgeNodeId", "edge-pune-01"))
                .andExpect(status().isForbidden());

        System.out.println("[Edge Security Test] Verified: Waiter role forbidden from provisioning edge tokens (403 Forbidden).");
    }

    @Test
    @DisplayName("Edge Security 4: Cross-Tenant Provisioning Isolation - Manager cannot provision token for foreign restaurant's node")
    void testCrossTenantProvisioningBlocked() throws Exception {
        // Register a second restaurant and its edge node
        Restaurant r2 = restaurantRepository.save(new Restaurant("Second Branch", "Asia/Kolkata", "ACTIVE"));
        EdgeNode foreignNode = new EdgeNode("edge-foreign-99", r2.getRestaurantId(), "Restaurant 2 Node", "ONLINE");
        edgeNodeRepository.save(foreignNode);

        // Manager of Restaurant 1 tries to generate a token for edge-foreign-99
        String managerR1Token = tokenProvider.generateAccessToken(1L, 1L, "manager@restrovision.local", Set.of("MANAGER"));

        mockMvc.perform(post("/api/v1/cameras/edge-token")
                        .header("Authorization", "Bearer " + managerR1Token)
                        .param("edgeNodeId", "edge-foreign-99"))
                .andExpect(status().isBadRequest()); // IllegalArgumentException: Edge node not found for this restaurant -> 400

        System.out.println("[Edge Security Test] Verified: Manager cannot provision token for foreign tenant's edge node.");
    }
}
