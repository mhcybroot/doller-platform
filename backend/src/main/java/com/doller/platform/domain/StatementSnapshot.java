package com.doller.platform.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "statement_snapshots")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StatementSnapshot {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true)
    private LocalDate businessDate;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal openingCashBdt;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal closingCashBdt;
    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal openingUsd;
    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal closingUsd;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal realizedProfitLossBdt;
}
