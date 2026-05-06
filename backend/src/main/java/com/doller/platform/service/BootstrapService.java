package com.doller.platform.service;

import com.doller.platform.domain.UserAccount;
import com.doller.platform.domain.enums.Role;
import com.doller.platform.repo.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class BootstrapService implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(BootstrapService.class);

    private final UserAccountRepository userRepo;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final boolean initEnabled;
    private final String ownerUsername;
    private final String ownerPassword;

    public BootstrapService(
            UserAccountRepository userRepo,
            PasswordEncoder passwordEncoder,
            AuditService auditService,
            @Value("${app.init.enabled:false}") boolean initEnabled,
            @Value("${app.init.owner-username:}") String ownerUsername,
            @Value("${app.init.owner-password:}") String ownerPassword
    ) {
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.initEnabled = initEnabled;
        this.ownerUsername = ownerUsername == null ? "" : ownerUsername.trim();
        this.ownerPassword = ownerPassword == null ? "" : ownerPassword.trim();
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!initEnabled) {
            return;
        }
        if (userRepo.count() > 0) {
            log.info("bootstrap_init_skipped reason=users_exist");
            return;
        }
        if (ownerUsername.isBlank() || ownerPassword.isBlank()) {
            log.warn("bootstrap_init_skipped reason=missing_owner_credentials expected_env=APP_INIT_OWNER_USERNAME,APP_INIT_OWNER_PASSWORD");
            return;
        }
        if (!isStrongPassword(ownerPassword)) {
            log.warn("bootstrap_init_skipped reason=weak_owner_password");
            return;
        }

        UserAccount owner = userRepo.save(UserAccount.builder()
                .username(ownerUsername)
                .passwordHash(passwordEncoder.encode(ownerPassword))
                .role(Role.OWNER)
                .active(true)
                .mustChangePassword(true)
                .build());
        auditService.log("INIT_OWNER_ENV", "startup", "username=" + ownerUsername, null, null, "owner-created:" + owner.getId());
        log.warn("bootstrap_owner_created username={} action_required=set_APP_INIT_ENABLED_false", ownerUsername);
    }

    private boolean isStrongPassword(String password) {
        return password.length() >= 12
                && password.chars().anyMatch(Character::isUpperCase)
                && password.chars().anyMatch(Character::isLowerCase)
                && password.chars().anyMatch(Character::isDigit)
                && password.chars().anyMatch(ch -> !Character.isLetterOrDigit(ch));
    }
}
