package com.restrovision.dto;

import java.util.Set;

public class AuthDtos {
    public static class LoginRequest {
        private String email;
        private String password;
        private Long restaurantId = 1L;

        public LoginRequest() {}
        public LoginRequest(String email, String password, Long restaurantId) {
            this.email = email;
            this.password = password;
            this.restaurantId = restaurantId;
        }

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public Long getRestaurantId() { return restaurantId; }
        public void setRestaurantId(Long restaurantId) { this.restaurantId = restaurantId; }
    }

    public static class RefreshRequest {
        private String refreshToken;
        public RefreshRequest() {}
        public RefreshRequest(String refreshToken) { this.refreshToken = refreshToken; }
        public String getRefreshToken() { return refreshToken; }
        public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
    }

    public static class LoginResponse {
        private String accessToken;
        private String refreshToken;
        private String tokenType = "Bearer";
        private Long expiresIn = 900L;
        private UserProfileDto user;

        public LoginResponse() {}
        public LoginResponse(String accessToken, String refreshToken, UserProfileDto user) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.user = user;
        }

        public String getAccessToken() { return accessToken; }
        public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
        public String getRefreshToken() { return refreshToken; }
        public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
        public String getTokenType() { return tokenType; }
        public void setTokenType(String tokenType) { this.tokenType = tokenType; }
        public Long getExpiresIn() { return expiresIn; }
        public void setExpiresIn(Long expiresIn) { this.expiresIn = expiresIn; }
        public UserProfileDto getUser() { return user; }
        public void setUser(UserProfileDto user) { this.user = user; }
    }

    public static class UserProfileDto {
        private Long userId;
        private Long restaurantId;
        private String email;
        private String fullName;
        private String jobRole;
        private Set<String> roles;

        public UserProfileDto() {}
        public UserProfileDto(Long userId, Long restaurantId, String email, String fullName, String jobRole, Set<String> roles) {
            this.userId = userId;
            this.restaurantId = restaurantId;
            this.email = email;
            this.fullName = fullName;
            this.jobRole = jobRole;
            this.roles = roles;
        }

        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public Long getRestaurantId() { return restaurantId; }
        public void setRestaurantId(Long restaurantId) { this.restaurantId = restaurantId; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getFullName() { return fullName; }
        public void setFullName(String fullName) { this.fullName = fullName; }
        public String getJobRole() { return jobRole; }
        public void setJobRole(String jobRole) { this.jobRole = jobRole; }
        public Set<String> getRoles() { return roles; }
        public void setRoles(Set<String> roles) { this.roles = roles; }
    }
}
