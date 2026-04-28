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
    private BigDecimal openingReceivableBdt;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal closingReceivableBdt;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal openingPayableBdt;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal closingPayableBdt;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal openingAdvanceFromPartyBdt;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal closingAdvanceFromPartyBdt;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal openingAdvanceToPartyBdt;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal closingAdvanceToPartyBdt;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal openingAgingBdt;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal closingAgingBdt;
    @Column(name = "opening_aging0_to3_bdt", nullable = false, precision = 19, scale = 2)
    private BigDecimal openingAging0To3Bdt;
    @Column(name = "closing_aging0_to3_bdt", nullable = false, precision = 19, scale = 2)
    private BigDecimal closingAging0To3Bdt;
    @Column(name = "opening_aging4_to7_bdt", nullable = false, precision = 19, scale = 2)
    private BigDecimal openingAging4To7Bdt;
    @Column(name = "closing_aging4_to7_bdt", nullable = false, precision = 19, scale = 2)
    private BigDecimal closingAging4To7Bdt;
    @Column(name = "opening_aging8_to15_bdt", nullable = false, precision = 19, scale = 2)
    private BigDecimal openingAging8To15Bdt;
    @Column(name = "closing_aging8_to15_bdt", nullable = false, precision = 19, scale = 2)
    private BigDecimal closingAging8To15Bdt;
    @Column(name = "opening_aging15_to30_bdt", nullable = false, precision = 19, scale = 2)
    private BigDecimal openingAging15To30Bdt;
    @Column(name = "closing_aging15_to30_bdt", nullable = false, precision = 19, scale = 2)
    private BigDecimal closingAging15To30Bdt;
    @Column(name = "opening_aging30_plus_bdt", nullable = false, precision = 19, scale = 2)
    private BigDecimal openingAging30PlusBdt;
    @Column(name = "closing_aging30_plus_bdt", nullable = false, precision = 19, scale = 2)
    private BigDecimal closingAging30PlusBdt;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal realizedProfitLossBdt;
}
