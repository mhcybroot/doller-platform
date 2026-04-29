package com.doller.platform;

import com.doller.platform.dto.MasterDataDtos;
import com.doller.platform.repo.AuditLogRepository;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class MasterDataServiceTest {
    @Autowired MasterDataService masterDataService;
    @Autowired PartyRepository partyRepository;
    @Autowired UserAccountRepository userAccountRepository;
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
}
