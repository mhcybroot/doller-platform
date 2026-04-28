package com.doller.platform.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String action;
    @Column(nullable = false)
    private String actor;
    @Column(nullable = false)
    private String requestPath;
    @Column(length = 2000)
    private String metadata;
    private String reason;
    @Column(columnDefinition = "text")
    private String beforePayload;
    @Column(columnDefinition = "text")
    private String afterPayload;
    private String beforeHash;
    private String afterHash;
    @Column(nullable = false)
    private LocalDateTime createdAt;
}
