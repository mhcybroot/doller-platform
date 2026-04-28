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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;

@Service
public class AuthService {
    private final UserAccountRepository userRepo;
    private final RefreshTokenRepository refreshRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;
    private final boolean initEnabled;

    public AuthService(UserAccountRepository userRepo, RefreshTokenRepository refreshRepo, PasswordEncoder passwordEncoder,
                       JwtService jwtService, AuditService auditService, @Value("${app.init.enabled:true}") boolean initEnabled) {
        this.userRepo = userRepo;
        this.refreshRepo = refreshRepo;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditService = auditService;
        this.initEnabled = initEnabled;
    }

    @Transactional
    public AuthDtos.AuthResponse initOwner(AuthDtos.InitOwnerRequest req) {
        if (!initEnabled || userRepo.count() > 0) throw new ApiException("Owner initialization disabled");
        UserAccount u = userRepo.save(UserAccount.builder()
                .username(req.username())
                .passwordHash(passwordEncoder.encode(req.password()))
                .role(Role.OWNER)
                .active(true)
                .mustChangePassword(true)
                .build());
        auditService.log("INIT_OWNER", "/auth/init-owner", "username=" + req.username(), null, null, "owner-created:" + u.getId());
        return createAuthResponse(u);
    }

    @Transactional
    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest req) {
        UserAccount u = userRepo.findByUsernameAndActiveTrue(req.username())
                .orElseThrow(() -> new ApiException("Invalid credentials"));
        if (!passwordEncoder.matches(req.password(), u.getPasswordHash())) throw new ApiException("Invalid credentials");
        refreshRepo.deleteByUser(u);
        return createAuthResponse(u);
    }

    @Transactional
    public AuthDtos.AuthResponse refresh(AuthDtos.RefreshRequest req) {
        Claims claims = jwtService.parse(req.refreshToken());
        if (!"refresh".equals(claims.get("typ", String.class))) throw new ApiException("Invalid refresh token type");
        String tokenHash = hash(req.refreshToken());
        RefreshToken stored = refreshRepo.findByTokenHashAndRevokedFalse(tokenHash)
                .orElseThrow(() -> new ApiException("Refresh token revoked or unknown"));
        if (stored.getExpiresAt().isBefore(LocalDateTime.now())) throw new ApiException("Refresh token expired");

        stored.setRevoked(true);
        stored.setRevokedAt(LocalDateTime.now());

        UserAccount user = stored.getUser();
        if (!user.isActive()) throw new ApiException("User disabled");

        AuthDtos.AuthResponse out = createAuthResponse(user);
        stored.setReplacedByHash(hash(out.refreshToken()));
        refreshRepo.save(stored);
        return out;
    }

    @Transactional
    public void changePassword(AuthDtos.ChangePasswordRequest req) {
        UserAccount user = currentUser();
        if (!passwordEncoder.matches(req.oldPassword(), user.getPasswordHash())) throw new ApiException("Old password mismatch");
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        user.setMustChangePassword(false);
        userRepo.save(user);
        refreshRepo.deleteByUser(user);
        auditService.log("CHANGE_PASSWORD", "/auth/change-password", null, null, null, "user:" + user.getId());
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
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepo.findByUsernameAndActiveTrue(username).orElseThrow(() -> new ApiException("User not found"));
    }

    private String hash(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new ApiException("Hashing failed");
        }
    }
}
