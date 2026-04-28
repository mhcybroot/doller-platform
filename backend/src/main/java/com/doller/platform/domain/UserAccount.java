package com.doller.platform.domain;

import com.doller.platform.domain.enums.Role;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "user_accounts")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserAccount {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(unique = true, nullable = false)
    private String username;
    @Column(nullable = false)
    private String passwordHash;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;
    @Column(nullable = false)
    private boolean active;
    @Column(nullable = false)
    private boolean mustChangePassword;
}
