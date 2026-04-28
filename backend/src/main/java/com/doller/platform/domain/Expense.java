package com.doller.platform.domain;

import com.doller.platform.domain.enums.ExpenseType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "expenses")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Expense {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExpenseType expenseType;
    @ManyToOne
    private TradeDeal tradeDeal;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amountBdt;
    @Column(nullable = false)
    private LocalDateTime expenseTime;
    @Column(nullable = false)
    private String category;
    private String notes;
}
