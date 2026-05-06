package com.doller.platform.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "currencies")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Currency {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 32)
    private String code;
    @Column(name = "display_name", nullable = false)
    private String displayName;
    private String notes;
    @Column(nullable = false)
    private boolean deleted;
    private LocalDateTime deletedAt;
    private String deletedBy;
}
