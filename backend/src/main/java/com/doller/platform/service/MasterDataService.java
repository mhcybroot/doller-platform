package com.doller.platform.service;

import com.doller.platform.common.ApiException;
import com.doller.platform.domain.Party;
import com.doller.platform.domain.UserAccount;
import com.doller.platform.domain.enums.Role;
import com.doller.platform.dto.MasterDataDtos;
import com.doller.platform.repo.PartyRepository;
import com.doller.platform.repo.RefreshTokenRepository;
import com.doller.platform.repo.UserAccountRepository;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class MasterDataService {
    private final UserAccountRepository userRepo;
    private final PartyRepository partyRepo;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final LedgerService ledgerService;

    public MasterDataService(UserAccountRepository userRepo, PartyRepository partyRepo, RefreshTokenRepository refreshTokenRepository, PasswordEncoder passwordEncoder, AuditService auditService, LedgerService ledgerService) {
        this.userRepo = userRepo;
        this.partyRepo = partyRepo;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.ledgerService = ledgerService;
    }

    public MasterDataDtos.UserResponse createUser(String username, String password, Role role) {
        String normalizedUsername = username == null ? "" : username.trim();
        validateUsername(normalizedUsername);
        validatePasswordStrength(password);
        if (userRepo.findByUsernameAndActiveTrue(normalizedUsername).isPresent()) throw new ApiException("Username exists");
        UserAccount u = userRepo.save(UserAccount.builder()
                .username(normalizedUsername)
                .passwordHash(passwordEncoder.encode(password))
                .role(role)
                .active(true)
                .mustChangePassword(true)
                .build());
        auditService.log("CREATE_USER", "/users", "role=" + role, null, null, "user:" + u.getId());
        return toUserResponse(u);
    }

    public List<MasterDataDtos.UserResponse> users() {
        return userRepo.findAll().stream().map(this::toUserResponse).toList();
    }

    public Party createParty(MasterDataDtos.PartyCreateRequest req) {
        BigDecimal openingReceivable = req.openingReceivableBdt() == null ? BigDecimal.ZERO : req.openingReceivableBdt();
        BigDecimal openingPayable = req.openingPayableBdt() == null ? BigDecimal.ZERO : req.openingPayableBdt();

        Party out = partyRepo.save(Party.builder()
                .name(req.name().trim())
                .phone(req.phone())
                .address(req.address())
                .notes(req.notes())
                .deleted(false)
                .build());

        LocalDateTime now = LocalDateTime.now();
        if (openingReceivable.compareTo(BigDecimal.ZERO) > 0) {
            ledgerService.post(now, "RECEIVABLE_" + out.getId(), openingReceivable, BigDecimal.ZERO,
                    "OPENING_BALANCE", out.getId(), "Opening receivable");
            ledgerService.post(now, "CASH", BigDecimal.ZERO, openingReceivable,
                    "OPENING_BALANCE", out.getId(), "Opening receivable offset");
        }
        if (openingPayable.compareTo(BigDecimal.ZERO) > 0) {
            ledgerService.post(now, "PAYABLE_" + out.getId(), BigDecimal.ZERO, openingPayable,
                    "OPENING_BALANCE", out.getId(), "Opening payable");
            ledgerService.post(now, "CASH", openingPayable, BigDecimal.ZERO,
                    "OPENING_BALANCE", out.getId(), "Opening payable offset");
        }

        String metadata = "openingReceivableBdt=" + openingReceivable + ", openingPayableBdt=" + openingPayable;
        auditService.log("CREATE_PARTY", "/parties", metadata, null, null, "party:" + out.getId());
        return out;
    }

    public List<Party> parties() { return partyRepo.findByDeletedFalse(); }

    public Party updateParty(Long id, MasterDataDtos.PartyUpdateRequest req) {
        Party party = partyRepo.findByIdAndDeletedFalse(id).orElseThrow(() -> new ApiException("Party not found"));
        String before = "party:" + party.getId() + ':' + party.getName() + '|' + party.getPhone() + '|' + party.getAddress() + '|' + party.getNotes();
        party.setName(req.name().trim());
        party.setPhone(req.phone());
        party.setAddress(req.address());
        party.setNotes(req.notes());
        Party saved = partyRepo.save(party);
        String after = "party:" + saved.getId() + ':' + saved.getName() + '|' + saved.getPhone() + '|' + saved.getAddress() + '|' + saved.getNotes();
        auditService.log("UPDATE_PARTY", "/parties/" + id, null, null, before, after);
        return saved;
    }

    @Transactional
    public void deleteParty(Long id) {
        Party party = partyRepo.findByIdAndDeletedFalse(id).orElseThrow(() -> new ApiException("Party not found"));
        String before = "party:" + party.getId() + ':' + party.getName() + '|' + party.getPhone() + '|' + party.getAddress() + '|' + party.getNotes();
        ledgerService.deleteOpeningBalanceEntries(party.getId());
        party.setDeleted(true);
        party.setDeletedAt(LocalDateTime.now());
        party.setDeletedBy(currentActor());
        partyRepo.save(party);
        auditService.log("DELETE_PARTY", "/parties/" + id, null, null, before, null);
    }

    public void deactivateUser(Long userId) {
        UserAccount u = userRepo.findById(userId).orElseThrow(() -> new ApiException("User not found"));
        u.setActive(false);
        userRepo.save(u);
        refreshTokenRepository.deleteByUser(u);
        auditService.log("DEACTIVATE_USER", "/users/" + userId + "/deactivate", null, null, null, "deactivated");
    }

    private String currentActor() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? "system" : auth.getName();
    }

    private MasterDataDtos.UserResponse toUserResponse(UserAccount user) {
        return new MasterDataDtos.UserResponse(
                user.getId(),
                user.getUsername(),
                user.getRole().name(),
                user.isActive(),
                user.isMustChangePassword()
        );
    }

    private void validateUsername(String username) {
        if (username.isBlank() || username.length() < 3 || username.length() > 64 || !username.matches("^[A-Za-z0-9._-]+$")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Username contains invalid characters");
        }
    }

    private void validatePasswordStrength(String password) {
        if (password == null
                || password.length() < 12
                || password.chars().noneMatch(Character::isUpperCase)
                || password.chars().noneMatch(Character::isLowerCase)
                || password.chars().noneMatch(Character::isDigit)
                || password.chars().allMatch(Character::isLetterOrDigit)) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Password must be at least 12 characters and include upper, lower, number, and special character");
        }
    }
}
