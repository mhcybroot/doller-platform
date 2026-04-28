package com.doller.platform.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "ledger_entries")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LedgerEntry {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private LocalDateTime entryTime;
    @Column(nullable = false)
    private String accountCode;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal debit;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal credit;
    @Column(nullable = false)
    private String referenceType;
    @Column(nullable = false)
    private Long referenceId;
    @Column(nullable = false)
    private String narration;
}
