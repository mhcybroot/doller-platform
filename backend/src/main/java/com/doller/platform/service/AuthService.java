package com.doller.platform.service;

import com.doller.platform.common.ApiException;
import com.doller.platform.domain.RefreshToken;
import com.doller.platform.domain.UserAccount;
import com.doller.platform.domain.enums.Role;
import com.doller.platform.dto.AuthDtos;
import com.doller.platform.repo.RefreshTokenRepository;
import com.doller.platform.repo.UserAccountRepository;
import com.doller.platform.security.JwtService;
import io.jsonwebtoken.Claims;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Objects;

@Service
public class AuthService {
    private final UserAccountRepository userRepo;
    private final RefreshTokenRepository refreshRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;
    private final AuthRateLimitService authRateLimitService;
    private final boolean initEnabled;
    private final String bootstrapToken;

    public AuthService(
            UserAccountRepository userRepo,
            RefreshTokenRepository refreshRepo,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AuditService auditService,
            AuthRateLimitService authRateLimitService,
            @Value("${app.init.enabled:false}") boolean initEnabled,
            @Value("${app.init.bootstrap-token:}") String bootstrapToken
    ) {
        this.userRepo = userRepo;
        this.refreshRepo = refreshRepo;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditService = auditService;
        this.authRateLimitService = authRateLimitService;
        this.initEnabled = initEnabled;
        this.bootstrapToken = bootstrapToken == null ? "" : bootstrapToken.trim();
    }

    @Transactional
    public AuthDtos.AuthResponse initOwner(AuthDtos.InitOwnerRequest req, String providedBootstrapToken, String clientKey) {
        String rateKey = safeRateKey(clientKey);
        authRateLimitService.checkAllowed("init-owner", rateKey);
        try {
            if (!initEnabled || userRepo.count() > 0) {
                throw new ApiException(HttpStatus.FORBIDDEN, "Owner initialization disabled");
            }
            if (bootstrapToken.isBlank() || !Objects.equals(bootstrapToken, safeHeader(providedBootstrapToken))) {
                throw new ApiException(HttpStatus.FORBIDDEN, "Bootstrap token required");
            }
            validatePasswordStrength(req.password());
            UserAccount u = userRepo.save(UserAccount.builder()
                    .username(req.username().trim())
                    .passwordHash(passwordEncoder.encode(req.password()))
                    .role(Role.OWNER)
                    .active(true)
                    .mustChangePassword(true)
                    .build());
            auditService.log("INIT_OWNER", "/auth/init-owner", "username=" + req.username(), null, null, "owner-created:" + u.getId());
            authRateLimitService.recordSuccess("init-owner", rateKey);
            return createAuthResponse(u);
        } catch (ApiException ex) {
            authRateLimitService.recordFailure("init-owner", rateKey);
            throw ex;
        }
    }

    @Transactional
    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest req, String clientKey) {
        String username = req.username().trim();
        String rateKey = username + '|' + safeRateKey(clientKey);
        authRateLimitService.checkAllowed("login", rateKey);
        try {
            UserAccount u = userRepo.findByUsernameAndActiveTrue(username)
                    .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
            if (!passwordEncoder.matches(req.password(), u.getPasswordHash())) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
            }
            refreshRepo.deleteByUser(u);
            authRateLimitService.recordSuccess("login", rateKey);
            return createAuthResponse(u);
        } catch (ApiException ex) {
            authRateLimitService.recordFailure("login", rateKey);
            throw ex;
        }
    }

    @Transactional
    public AuthDtos.AuthResponse refresh(AuthDtos.RefreshRequest req, String clientKey) {
        String rateKey = hash(req.refreshToken()) + '|' + safeRateKey(clientKey);
        authRateLimitService.checkAllowed("refresh", rateKey);
        try {
            Claims claims = jwtService.parse(req.refreshToken());
            if (!"refresh".equals(claims.get("typ", String.class))) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
            }
            String tokenHash = hash(req.refreshToken());
            RefreshToken stored = refreshRepo.findByTokenHashAndRevokedFalse(tokenHash)
                    .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token revoked or unknown"));
            if (stored.getExpiresAt().isBefore(LocalDateTime.now())) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token expired");
            }

            stored.setRevoked(true);
            stored.setRevokedAt(LocalDateTime.now());

            UserAccount user = stored.getUser();
            if (!user.isActive()) {
                throw new ApiException(HttpStatus.FORBIDDEN, "User disabled");
            }

            AuthDtos.AuthResponse out = createAuthResponse(user);
            stored.setReplacedByHash(hash(out.refreshToken()));
            refreshRepo.save(stored);
            authRateLimitService.recordSuccess("refresh", rateKey);
            return out;
        } catch (ApiException ex) {
            authRateLimitService.recordFailure("refresh", rateKey);
            throw ex;
        }
    }

    @Transactional
    public void changePassword(AuthDtos.ChangePasswordRequest req, String clientKey) {
        UserAccount user = currentUser();
        String rateKey = user.getUsername() + '|' + safeRateKey(clientKey);
        authRateLimitService.checkAllowed("change-password", rateKey);
        try {
            if (!passwordEncoder.matches(req.oldPassword(), user.getPasswordHash())) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "Old password mismatch");
            }
            validatePasswordStrength(req.newPassword());
            user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
            user.setMustChangePassword(false);
            userRepo.save(user);
            refreshRepo.deleteByUser(user);
            auditService.log("CHANGE_PASSWORD", "/auth/change-password", null, null, null, "user:" + user.getId());
            authRateLimitService.recordSuccess("change-password", rateKey);
        } catch (ApiException ex) {
            authRateLimitService.recordFailure("change-password", rateKey);
            throw ex;
        }
    }

    private AuthDtos.AuthResponse createAuthResponse(UserAccount u) {
        String access = jwtService.issueAccessToken(u.getUsername(), u.getRole().name());
        String refresh = jwtService.issueRefreshToken(u.getUsername());
        refreshRepo.save(RefreshToken.builder()
                .user(u)
                .tokenHash(hash(refresh))
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusSeconds(jwtService.getRefreshExpirySeconds()))
                .revoked(false)
                .build());
        return new AuthDtos.AuthResponse(access, refresh, jwtService.getAccessExpirySeconds(), jwtService.getRefreshExpirySeconds(), u.getRole().name(), u.isMustChangePassword());
    }

    private UserAccount currentUser() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        String username = authentication.getName();
        return userRepo.findByUsernameAndActiveTrue(username).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private String hash(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new ApiException("Hashing failed");
        }
    }

    private void validatePasswordStrength(String password) {
        if (password == null
                || password.length() < 12
                || password.chars().noneMatch(Character::isUpperCase)
                || password.chars().noneMatch(Character::isLowerCase)
                || password.chars().noneMatch(Character::isDigit)
                || password.chars().allMatch(Character::isLetterOrDigit)) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Password must be at least 12 characters and include upper, lower, number, and special character");
        }
    }

    private String safeRateKey(String value) {
        return (value == null || value.isBlank()) ? "unknown" : value.trim();
    }

    private String safeHeader(String value) {
        return value == null ? "" : value.trim();
    }
}
