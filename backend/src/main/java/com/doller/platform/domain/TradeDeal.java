package com.doller.platform.domain;

import com.doller.platform.domain.enums.DealType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "trade_deals")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TradeDeal {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DealType dealType;
    @ManyToOne(optional = false)
    private Party party;
    @ManyToOne(optional = false)
    private UserAccount createdBy;
    @Column(name = "currency_code", nullable = false, length = 32)
    private String currencyCode;
    @Column(name = "usd_amount", nullable = false, precision = 19, scale = 6)
    private BigDecimal quantity;
    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal bdtRate;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal bdtGross;
    @Column(nullable = false)
    private LocalDateTime dealTime;
    private String notes;
    @Column(nullable = false)
    private boolean lockedByDayClose;
    @Column(nullable = false)
    private boolean deleted;
    private LocalDateTime deletedAt;
    private String deletedBy;
}
