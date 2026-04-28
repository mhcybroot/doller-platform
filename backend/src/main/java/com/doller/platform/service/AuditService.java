package com.doller.platform.service;

import com.doller.platform.domain.AuditLog;
import com.doller.platform.repo.AuditLogRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;

@Service
public class AuditService {
    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public String log(String action, String path, String metadata, String reason, String beforePayload, String afterPayload) {
        AuditLog log = auditLogRepository.save(AuditLog.builder()
                .action(action)
                .actor(currentActor())
                .requestPath(path)
                .metadata(metadata)
                .reason(reason)
                .beforePayload(beforePayload)
                .afterPayload(afterPayload)
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
}
