package com.doller.platform;

import com.doller.platform.dto.MasterDataDtos;
import com.doller.platform.repo.AuditLogRepository;
import com.doller.platform.repo.CurrencyRepository;
import com.doller.platform.repo.DailyCloseRepository;
import com.doller.platform.repo.ExpenseRepository;
import com.doller.platform.repo.LedgerEntryRepository;
import com.doller.platform.repo.PartyRepository;
import com.doller.platform.repo.RefreshTokenRepository;
import com.doller.platform.repo.SettlementRepository;
import com.doller.platform.repo.StatementSnapshotRepository;
import com.doller.platform.repo.TradeDealRepository;
import com.doller.platform.repo.UserAccountRepository;
import com.doller.platform.service.MasterDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class MasterDataServiceTest {
    @Autowired MasterDataService masterDataService;
    @Autowired PartyRepository partyRepository;
    @Autowired UserAccountRepository userAccountRepository;
    @Autowired CurrencyRepository currencyRepository;
    @Autowired TradeDealRepository tradeDealRepository;
    @Autowired SettlementRepository settlementRepository;
    @Autowired ExpenseRepository expenseRepository;
    @Autowired DailyCloseRepository dailyCloseRepository;
    @Autowired StatementSnapshotRepository statementSnapshotRepository;
    @Autowired LedgerEntryRepository ledgerEntryRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired AuditLogRepository auditLogRepository;

    @BeforeEach
    void reset() {
        SecurityContextHolder.clearContext();
        auditLogRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        dailyCloseRepository.deleteAll();
        statementSnapshotRepository.deleteAll();
        expenseRepository.deleteAll();
        settlementRepository.deleteAll();
        tradeDealRepository.deleteAll();
        ledgerEntryRepository.deleteAll();
        partyRepository.deleteAll();
        userAccountRepository.deleteAll();
    }

    @Test
    void createPartyWithReceivableAndPayableOpeningPostsLedger() {
        var created = masterDataService.createParty(new MasterDataDtos.PartyCreateRequest(
                "Opening Party",
                "017",
                "Dhaka",
                "seed",
                new BigDecimal("1500.00"),
                new BigDecimal("700.00")
        ));

        assertEquals("Opening Party", created.getName());
        LocalDateTime from = LocalDateTime.now().minusDays(1);
        LocalDateTime to = LocalDateTime.now().plusDays(1);
        BigDecimal receivable = ledgerEntryRepository.netForAccount(
                "RECEIVABLE_" + created.getId(),
                from,
                to
        );
        BigDecimal payable = ledgerEntryRepository.netForAccount(
                "PAYABLE_" + created.getId(),
                from,
                to
        );
        BigDecimal cash = ledgerEntryRepository.netForAccount(
                "CASH",
                from,
                to
        );
        assertEquals(0, receivable.compareTo(new BigDecimal("1500.00")));
        assertEquals(0, payable.compareTo(new BigDecimal("-700.00")));
        assertEquals(0, cash.compareTo(new BigDecimal("-800.00")));
    }

    @Test
    void deletePartyRemovesOpeningBalanceLedgerEntries() {
        var created = masterDataService.createParty(new MasterDataDtos.PartyCreateRequest(
                "Delete Opening Party",
                "017",
                "Dhaka",
                "seed",
                new BigDecimal("1200.00"),
                new BigDecimal("300.00")
        ));

        assertTrue(ledgerEntryRepository.findByReferenceTypeAndReferenceId("OPENING_BALANCE", created.getId()).size() > 0);

        masterDataService.deleteParty(created.getId());

        assertEquals(0, ledgerEntryRepository.findByReferenceTypeAndReferenceId("OPENING_BALANCE", created.getId()).size());
    }

    @Test
    void currencyCrudSupportsCreateUpdateAndDeleteWhenUnused() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("owner", null)
        );

        var created = masterDataService.createCurrency(
                new MasterDataDtos.CurrencyCreateRequest("jpy", "Japanese Yen", "asia")
        );

        assertEquals("JPY", created.code());
        assertEquals("Japanese Yen", created.displayName());

        var updated = masterDataService.updateCurrency(
                created.id(),
                new MasterDataDtos.CurrencyUpdateRequest("jpy_cash", "Japanese Yen Cash", null)
        );

        assertEquals("JPY_CASH", updated.code());
        assertEquals("Japanese Yen Cash", updated.displayName());

        masterDataService.deleteCurrency(updated.id());

        assertTrue(currencyRepository.findByIdAndDeletedFalse(updated.id()).isEmpty());
    }

    @Test
    void deleteCurrencyRejectsInUseCode() {
        var actor = userAccountRepository.save(com.doller.platform.domain.UserAccount.builder()
                .username("owner")
                .passwordHash("hash")
                .role(com.doller.platform.domain.enums.Role.OWNER)
                .active(true)
                .mustChangePassword(false)
                .build());
        var party = partyRepository.save(com.doller.platform.domain.Party.builder()
                .name("Alpha")
                .deleted(false)
                .build());
        var usd = currencyRepository.findByCodeAndDeletedFalse("USD").orElseThrow();
        tradeDealRepository.save(com.doller.platform.domain.TradeDeal.builder()
                .dealType(com.doller.platform.domain.enums.DealType.BUY)
                .party(party)
                .createdBy(actor)
                .currencyCode("USD")
                .quantity(new BigDecimal("10"))
                .bdtRate(new BigDecimal("120"))
                .bdtGross(new BigDecimal("1200"))
                .dealTime(LocalDateTime.now())
                .lockedByDayClose(false)
                .deleted(false)
                .build());

        var ex = org.junit.jupiter.api.Assertions.assertThrows(
                com.doller.platform.common.ApiException.class,
                () -> masterDataService.deleteCurrency(usd.getId())
        );

        assertEquals("Currency is already used by deals and cannot be deleted", ex.getMessage());
    }
}
