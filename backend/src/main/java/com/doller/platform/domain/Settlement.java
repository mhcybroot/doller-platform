package com.doller.platform.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "settlements")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Settlement {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    private Party party;
    @ManyToOne
    private TradeDeal tradeDeal;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal bdtAmount;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal appliedAmount;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal advanceAmount;
    @Column(nullable = false)
    private LocalDateTime settlementTime;
    private String notes;
}
