package com.doller.platform.domain;

import com.doller.platform.domain.enums.SettlementBasis;
import com.doller.platform.domain.enums.SettlementDirection;
import com.doller.platform.domain.enums.SettlementPaymentMethod;
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
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SettlementDirection direction;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SettlementBasis basis;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal bdtAmount;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal appliedAmount;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal advanceAmount;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SettlementPaymentMethod paymentMethod;
    @Column(length = 255)
    private String paymentReference;
    @Column(nullable = false)
    private LocalDateTime settlementTime;
    private String notes;
    @Column(nullable = false)
    private boolean deleted;
    private LocalDateTime deletedAt;
    private String deletedBy;
}
