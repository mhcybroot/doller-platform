package com.doller.platform.config;

import com.doller.platform.domain.UserAccount;
import com.doller.platform.domain.enums.Role;
import com.doller.platform.repo.UserAccountRepository;
import com.doller.platform.service.AuditService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class InitialOwnerSeeder implements CommandLineRunner {
    private final UserAccountRepository userRepo;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final boolean initEnabled;

    public InitialOwnerSeeder(
            UserAccountRepository userRepo,
            PasswordEncoder passwordEncoder,
            AuditService auditService,
            @Value("${app.init.enabled:true}") boolean initEnabled
    ) {
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.initEnabled = initEnabled;
    }

    @Override
    public void run(String... args) {
        if (!initEnabled || userRepo.findByUsername("admin").isPresent()) {
            return;
        }
        UserAccount admin = userRepo.save(UserAccount.builder()
                .username("admin")
                .passwordHash(passwordEncoder.encode("admin123"))
                .role(Role.OWNER)
                .active(true)
                .mustChangePassword(true)
                .build());
        auditService.log("SEED_OWNER", "/system/seed-owner", "username=admin", null, null, "owner-seeded:" + admin.getId());
    }
}
