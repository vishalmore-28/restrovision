package com.restrovision.security;

import com.restrovision.domain.EdgeNode;
import com.restrovision.repository.EdgeNodeRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final UserDetailsService userDetailsService;
    private final EdgeNodeRepository edgeNodeRepository;

    public JwtAuthFilter(JwtTokenProvider tokenProvider,
                         UserDetailsService userDetailsService,
                         EdgeNodeRepository edgeNodeRepository) {
        this.tokenProvider = tokenProvider;
        this.userDetailsService = userDetailsService;
        this.edgeNodeRepository = edgeNodeRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String jwt = getJwtFromRequest(request);

            if (StringUtils.hasText(jwt) && tokenProvider.validateToken(jwt)) {
                Claims claims = tokenProvider.getClaims(jwt);
                String tokenType = claims.get("type", String.class);

                // 1. Explicitly reject refresh tokens attempted as Bearer tokens
                if ("REFRESH".equalsIgnoreCase(tokenType)) {
                    logger.warn("Security Alert: Attempt to use refresh token as Bearer access token was rejected.");
                }
                // 2. Per-Device Edge Authentication
                else if (JwtTokenProvider.TOKEN_TYPE_EDGE.equalsIgnoreCase(tokenType)) {
                    processEdgeDeviceAuthentication(claims, request);
                }
                // 3. User JWT Token Authentication
                else {
                    processUserAuthentication(claims, request);
                }
            }
        } catch (Exception ex) {
            logger.error("Could not set user/device authentication in security context", ex);
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private void processEdgeDeviceAuthentication(Claims claims, HttpServletRequest request) {
        // A. Validate Issuer
        if (!JwtTokenProvider.EDGE_ISSUER.equals(claims.getIssuer())) {
            logger.warn("Edge authentication rejected: Invalid issuer: " + claims.getIssuer());
            return;
        }

        // B. Validate Audience
        Set<String> audience = claims.getAudience();
        if (audience == null || !audience.contains(JwtTokenProvider.EDGE_AUDIENCE)) {
            logger.warn("Edge authentication rejected: Missing or invalid audience");
            return;
        }

        // C. Device Identity from subject
        String edgeNodeId = claims.getSubject();
        if (!StringUtils.hasText(edgeNodeId)) {
            logger.warn("Edge authentication rejected: Empty device identity in subject");
            return;
        }

        // D. Validate device existence in DB
        Optional<EdgeNode> nodeOpt = edgeNodeRepository.findById(edgeNodeId);
        if (nodeOpt.isEmpty()) {
            logger.warn("Edge authentication rejected: Unknown edge device: " + edgeNodeId);
            return;
        }

        EdgeNode edgeNode = nodeOpt.get();

        // E. Validate device status (must be ACTIVE or ONLINE)
        String status = edgeNode.getStatus();
        if (!"ACTIVE".equalsIgnoreCase(status) && !"ONLINE".equalsIgnoreCase(status)) {
            logger.warn("Edge authentication rejected: Device " + edgeNodeId + " has inactive status: " + status);
            return;
        }

        // F. Enforce Database-driven Tenant Relationship: NEVER trust tenantId in JWT claim alone!
        Long trustedRestaurantId = edgeNode.getRestaurantId();
        Number claimRestId = (Number) claims.get("restaurantId");
        if (claimRestId != null && !trustedRestaurantId.equals(claimRestId.longValue())) {
            logger.warn("Edge authentication rejected: Claimed restaurantId " + claimRestId +
                    " does not match DB binding " + trustedRestaurantId);
            return;
        }

        // Update last seen timestamp
        edgeNode.setLastSeenAt(Instant.now());
        edgeNodeRepository.save(edgeNode);

        // Bind trusted tenant context
        TenantContext.setRestaurantId(trustedRestaurantId);

        // Set SecurityContext with ROLE_AI_SERVICE
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        edgeNodeId,
                        null,
                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_AI_SERVICE")));
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        logger.debug("Edge device '" + edgeNodeId + "' authenticated successfully for restaurant " + trustedRestaurantId);
    }

    private void processUserAuthentication(Claims claims, HttpServletRequest request) {
        String email = claims.getSubject();
        Number restIdNum = (Number) claims.get("restaurantId");
        Long restaurantId = restIdNum != null ? restIdNum.longValue() : null;

        UserDetails userDetails = userDetailsService.loadUserByUsername(email);
        if (restaurantId == null && userDetails instanceof CustomUserDetails) {
            restaurantId = ((CustomUserDetails) userDetails).getRestaurantId();
        }

        if (restaurantId != null) {
            TenantContext.setRestaurantId(restaurantId);
        } else {
            TenantContext.clear();
        }

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
