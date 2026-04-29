package com.doller.platform.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "parties")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Party {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String name;
    private String phone;
    private String address;
    private String notes;
    @Column(nullable = false)
    private boolean deleted;
    private LocalDateTime deletedAt;
    private String deletedBy;
}
