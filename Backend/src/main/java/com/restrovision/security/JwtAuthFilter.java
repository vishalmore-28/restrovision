package com.restrovision.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.Collections;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final UserDetailsService userDetailsService;
    private final String edgeServiceToken;

    public JwtAuthFilter(JwtTokenProvider tokenProvider,
                         UserDetailsService userDetailsService,
                         @Value("${restrovision.edge.service-token:edge-token-pune-01}") String edgeServiceToken) {
        this.tokenProvider = tokenProvider;
        this.userDetailsService = userDetailsService;
        this.edgeServiceToken = edgeServiceToken;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String jwt = getJwtFromRequest(request);

            if (StringUtils.hasText(jwt)) {
                // 1. Edge service credential check
                if (jwt.equals(edgeServiceToken)) {
                    TenantContext.setRestaurantId(1L); // Edge node bound to restaurant 1
                    List<SimpleGrantedAuthority> authorities = Collections.singletonList(new SimpleGrantedAuthority("ROLE_AI_SERVICE"));
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken("edge-pune-01", null, authorities);
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
                // 2. User JWT token check
                else if (tokenProvider.validateToken(jwt)) {
                    Claims claims = tokenProvider.getClaims(jwt);

                    // P0 Security Fix: Reject refresh tokens used as bearer access tokens!
                    String tokenType = claims.get("type", String.class);
                    if ("REFRESH".equalsIgnoreCase(tokenType)) {
                        logger.warn("Security Alert: Attempt to use refresh token as Bearer access token was rejected.");
                    } else {
                        String email = claims.getSubject();
                        Number restIdNum = (Number) claims.get("restaurantId");
                        Long restaurantId = restIdNum != null ? restIdNum.longValue() : 1L;
                        TenantContext.setRestaurantId(restaurantId);

                        // P0 Fix: Populate actual CustomUserDetails as the principal, not a raw String!
                        UserDetails userDetails = userDetailsService.loadUserByUsername(email);
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                }
            }
        } catch (Exception ex) {
            logger.error("Could not set user authentication in security context", ex);
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
