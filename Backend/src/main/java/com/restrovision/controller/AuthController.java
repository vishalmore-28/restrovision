package com.restrovision.controller;

import com.restrovision.domain.Employee;
import com.restrovision.domain.User;
import com.restrovision.dto.AuthDtos.*;
import com.restrovision.repository.EmployeeRepository;
import com.restrovision.repository.UserRepository;
import com.restrovision.security.CustomUserDetails;
import com.restrovision.security.JwtTokenProvider;
import com.restrovision.security.LoginRateLimiterService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final LoginRateLimiterService loginRateLimiterService;

    public AuthController(
            AuthenticationManager authenticationManager,
            JwtTokenProvider tokenProvider,
            UserRepository userRepository,
            EmployeeRepository employeeRepository,
            LoginRateLimiterService loginRateLimiterService) {
        this.authenticationManager = authenticationManager;
        this.tokenProvider = tokenProvider;
        this.userRepository = userRepository;
        this.employeeRepository = employeeRepository;
        this.loginRateLimiterService = loginRateLimiterService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String rawEmail = request != null ? request.getEmail() : "";
        String normalizedEmail = loginRateLimiterService.normalizeAccount(rawEmail);
        String clientIp = loginRateLimiterService.resolveClientIp(httpRequest);

        LoginRateLimiterService.RateLimitResult limit = loginRateLimiterService.checkLimit(clientIp, normalizedEmail);
        if (limit.isBlocked()) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Too many login attempts. Please try again in " + limit.getRetryAfterSeconds() + " seconds."
            );
            problem.setType(URI.create("urn:restrovision:error:rate-limit-exceeded"));
            problem.setTitle("Too Many Requests");
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("Retry-After", String.valueOf(limit.getRetryAfterSeconds()))
                    .body(problem);
        }

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(rawEmail, request != null ? request.getPassword() : ""));
        } catch (BadCredentialsException ex) {
            loginRateLimiterService.recordFailure(clientIp, normalizedEmail);
            throw ex;
        }

        loginRateLimiterService.recordSuccess(clientIp, normalizedEmail);

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        User user = userRepository.findById(userDetails.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        String accessToken = tokenProvider.generateAccessToken(
                user.getUserId(), user.getRestaurantId(), user.getEmail(), user.getRoles());
        String refreshToken = tokenProvider.generateRefreshToken(
                user.getUserId(), user.getRestaurantId(), user.getEmail());

        Optional<Employee> emp = employeeRepository.findByRestaurantIdAndUserId(user.getRestaurantId(), user.getUserId());
        String jobRole = emp.map(Employee::getJobRole).orElse("OWNER");

        UserProfileDto profile = new UserProfileDto(
                user.getUserId(),
                user.getRestaurantId(),
                user.getEmail(),
                user.getFullName(),
                jobRole,
                user.getRoles()
        );

        return ResponseEntity.ok(new LoginResponse(accessToken, refreshToken, profile));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody RefreshRequest request) {
        if (!tokenProvider.validateToken(request.getRefreshToken())) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                    HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token");
            problem.setType(URI.create("urn:restrovision:error:unauthorized"));
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
        }

        var claims = tokenProvider.getClaims(request.getRefreshToken());

        // P0 Security Fix: Verify token has type: REFRESH (reject access tokens presented to refresh endpoint!)
        String tokenType = claims.get("type", String.class);
        if (!"REFRESH".equalsIgnoreCase(tokenType)) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                    HttpStatus.UNAUTHORIZED, "Provided token is not a refresh token");
            problem.setType(URI.create("urn:restrovision:error:unauthorized"));
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
        }

        String email = claims.getSubject();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        String accessToken = tokenProvider.generateAccessToken(
                user.getUserId(), user.getRestaurantId(), user.getEmail(), user.getRoles());
        return ResponseEntity.ok(new LoginResponse(accessToken, request.getRefreshToken(), null));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout() {
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileDto> getCurrentUser(@AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        User user = userRepository.findById(userDetails.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        Optional<Employee> emp = employeeRepository.findByRestaurantIdAndUserId(user.getRestaurantId(), user.getUserId());
        String jobRole = emp.map(Employee::getJobRole).orElse("OWNER");

        UserProfileDto profile = new UserProfileDto(
                user.getUserId(),
                user.getRestaurantId(),
                user.getEmail(),
                user.getFullName(),
                jobRole,
                user.getRoles()
        );
        return ResponseEntity.ok(profile);
    }
}
