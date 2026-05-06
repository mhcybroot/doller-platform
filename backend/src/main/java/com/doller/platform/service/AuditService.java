package com.doller.platform.service;

import com.doller.platform.domain.AuditLog;
import com.doller.platform.repo.AuditLogRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;

@Service
public class AuditService {
    private final AuditLogRepository auditLogRepository;
    private final boolean storePayloads;

    public AuditService(
            AuditLogRepository auditLogRepository,
            @Value("${app.audit.store-payloads:false}") boolean storePayloads
    ) {
        this.auditLogRepository = auditLogRepository;
        this.storePayloads = storePayloads;
    }

    public String log(String action, String path, String metadata, String reason, String beforePayload, String afterPayload) {
        AuditLog log = auditLogRepository.save(AuditLog.builder()
                .action(action)
                .actor(currentActor())
                .requestPath(path)
                .metadata(sanitizeText(metadata, 512))
                .reason(sanitizeText(reason, 255))
                .beforePayload(storePayloads ? sanitizeText(beforePayload, 1024) : null)
                .afterPayload(storePayloads ? sanitizeText(afterPayload, 1024) : null)
                .beforeHash(hash(beforePayload))
                .afterHash(hash(afterPayload))
                .createdAt(LocalDateTime.now())
                .build());
        return "AUD-" + log.getId();
    }

    private String currentActor() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? "system" : auth.getName();
    }

    private String hash(String payload) {
        if (payload == null) return null;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return null;
        }
    }

    private String sanitizeText(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String sanitized = value
                .replaceAll("(?i)(password|token|authorization)=([^,\\s]+)", "$1=***")
                .replaceAll("(?i)(password|token|authorization)\"?\s*[:=]\s*\"[^\"]+\"", "$1=\"***\"")
                .trim();
        if (sanitized.length() > maxLength) {
            return sanitized.substring(0, maxLength);
        }
        return sanitized;
    }
}
