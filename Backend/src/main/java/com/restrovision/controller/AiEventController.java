package com.restrovision.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.restrovision.domain.AiEvent;
import com.restrovision.dto.AiEventEnvelopeDto;
import com.restrovision.repository.AiEventRepository;
import com.restrovision.security.HmacValidator;
import com.restrovision.security.TenantContext;
import com.restrovision.service.AiEventIngestionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ai/events")
public class AiEventController {

    private final AiEventIngestionService eventIngestionService;
    private final AiEventRepository aiEventRepository;
    private final HmacValidator hmacValidator;
    private final ObjectMapper objectMapper;

    public AiEventController(
            AiEventIngestionService eventIngestionService,
            AiEventRepository aiEventRepository,
            HmacValidator hmacValidator) {
        this.eventIngestionService = eventIngestionService;
        this.aiEventRepository = aiEventRepository;
        this.hmacValidator = hmacValidator;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @PostMapping
    @PreAuthorize("hasRole('AI_SERVICE')")
    public ResponseEntity<?> ingestAiEvent(
            @RequestBody byte[] rawBytes,
            @RequestHeader(value = "X-Signature", required = false) String signature) {

        // 1. Mandatory HMAC verification (§5.3 D5 & §7.1)
        if (signature == null || signature.isBlank()) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                    HttpStatus.UNAUTHORIZED, "Missing mandatory X-Signature header");
            problem.setType(URI.create("urn:restrovision:error:unauthorized"));
            problem.setTitle("Missing Signature");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
        }

        if (!hmacValidator.verifySignature(rawBytes, signature)) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                    HttpStatus.UNAUTHORIZED, "Invalid HMAC signature for raw event payload");
            problem.setType(URI.create("urn:restrovision:error:unauthorized"));
            problem.setTitle("Invalid Signature");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
        }

        // 2. Deserialize payload
        AiEventEnvelopeDto envelope;
        try {
            envelope = objectMapper.readValue(rawBytes, AiEventEnvelopeDto.class);
        } catch (Exception ex) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST, "Malformed JSON schema in event envelope: " + ex.getMessage());
            problem.setType(URI.create("urn:restrovision:error:schema-invalid"));
            problem.setTitle("Schema Invalid");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
        }

        // 3. Strict Tenant Isolation: Edge credentials dictate the tenant. Client cannot inject arbitrary restaurantId!
        Long verifiedRestaurantId = TenantContext.getRestaurantId();
        envelope.setRestaurantId(verifiedRestaurantId);

        Map<String, Object> result = eventIngestionService.ingestEvent(envelope);
        return ResponseEntity.ok(result);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
    public ResponseEntity<List<AiEvent>> getRecentEvents() {
        Long restaurantId = TenantContext.getRestaurantId();
        return ResponseEntity.ok(aiEventRepository.findTop50ByRestaurantIdOrderByOccurredAtDesc(restaurantId));
    }
}
