package com.doller.platform.web;

import com.doller.platform.dto.MasterDataDtos;
import com.doller.platform.repo.AuditLogRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/audit")
@PreAuthorize("hasRole('OWNER')")
public class AuditController {
    private final AuditLogRepository auditLogRepository;

    public AuditController(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @GetMapping("/logs")
    public List<MasterDataDtos.AuditLogResponse> logs() {
        return auditLogRepository.findAll().stream()
                .map(log -> new MasterDataDtos.AuditLogResponse(
                        log.getId(),
                        log.getAction(),
                        log.getActor(),
                        log.getRequestPath(),
                        log.getMetadata(),
                        log.getReason(),
                        log.getCreatedAt()
                ))
                .toList();
    }
}
