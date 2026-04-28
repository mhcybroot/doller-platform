package com.doller.platform.service;

import com.doller.platform.common.ApiException;
import com.doller.platform.domain.Party;
import com.doller.platform.domain.UserAccount;
import com.doller.platform.domain.enums.Role;
import com.doller.platform.repo.PartyRepository;
import com.doller.platform.repo.RefreshTokenRepository;
import com.doller.platform.repo.UserAccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MasterDataService {
    private final UserAccountRepository userRepo;
    private final PartyRepository partyRepo;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public MasterDataService(UserAccountRepository userRepo, PartyRepository partyRepo, RefreshTokenRepository refreshTokenRepository, PasswordEncoder passwordEncoder, AuditService auditService) {
        this.userRepo = userRepo;
        this.partyRepo = partyRepo;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    public UserAccount createUser(String username, String password, Role role) {
        if (userRepo.findByUsernameAndActiveTrue(username).isPresent()) throw new ApiException("Username exists");
        UserAccount u = userRepo.save(UserAccount.builder()
                .username(username)
                .passwordHash(passwordEncoder.encode(password))
                .role(role)
                .active(true)
                .mustChangePassword(true)
                .build());
        auditService.log("CREATE_USER", "/users", "role=" + role, null, null, "user:" + u.getId());
        return u;
    }

    public List<UserAccount> users() { return userRepo.findAll(); }
    public Party createParty(Party p) {
        Party out = partyRepo.save(p);
        auditService.log("CREATE_PARTY", "/parties", null, null, null, "party:" + out.getId());
        return out;
    }
    public List<Party> parties() { return partyRepo.findAll(); }

    public void deactivateUser(Long userId) {
        UserAccount u = userRepo.findById(userId).orElseThrow(() -> new ApiException("User not found"));
        u.setActive(false);
        userRepo.save(u);
        refreshTokenRepository.deleteByUser(u);
        auditService.log("DEACTIVATE_USER", "/users/" + userId + "/deactivate", null, null, null, "deactivated");
    }
}
