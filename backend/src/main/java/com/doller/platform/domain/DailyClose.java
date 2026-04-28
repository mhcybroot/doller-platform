package com.doller.platform.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "daily_closes")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DailyClose {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true)
    private LocalDate businessDate;
    @ManyToOne(optional = false)
    private UserAccount closedBy;
    @Column(nullable = false)
    private LocalDateTime closedAt;
    @Column(nullable = false)
    private boolean reopened;
    private String reopenReason;
}
