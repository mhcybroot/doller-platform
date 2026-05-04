package com.doller.platform.domain;

import com.doller.platform.domain.enums.CustomEntryType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "owner_custom_entries")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OwnerCustomEntry {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    private Company company;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CustomEntryType entryType;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amountBdt;
    @Column(nullable = false)
    private LocalDateTime entryTime;
    @Column(nullable = false)
    private String itemPurpose;
    private String notes;
    @Column(nullable = false)
    private boolean deleted;
    private LocalDateTime deletedAt;
    private String deletedBy;
}

