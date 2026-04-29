package com.doller.platform;

import com.doller.platform.domain.Party;
import com.doller.platform.domain.UserAccount;
import com.doller.platform.domain.enums.DealType;
import com.doller.platform.domain.enums.InstrumentCode;
import com.doller.platform.domain.enums.SettlementPaymentMethod;
import com.doller.platform.domain.enums.Role;
import com.doller.platform.dto.TradingDtos;
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
import com.doller.platform.service.TradingService;
import com.doller.platform.service.MasterDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class TradingServiceTest {
    @Autowired TradingService tradingService;
    @Autowired MasterDataService masterDataService;
    @Autowired PartyRepository partyRepository;
    @Autowired UserAccountRepository userAccountRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired TradeDealRepository tradeDealRepository;
    @Autowired SettlementRepository settlementRepository;
    @Autowired ExpenseRepository expenseRepository;
    @Autowired DailyCloseRepository dailyCloseRepository;
    @Autowired StatementSnapshotRepository statementSnapshotRepository;
    @Autowired LedgerEntryRepository ledgerEntryRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired AuditLogRepository auditLogRepository;

    @BeforeEach
    void ensureOwner() {
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
        if (userAccountRepository.findByUsernameAndActiveTrue("owner").isEmpty()) {
            userAccountRepository.save(UserAccount.builder()
                    .username("owner")
                    .passwordHash(passwordEncoder.encode("owner123"))
                    .role(Role.OWNER)
                    .active(true)
                    .mustChangePassword(false)
                    .build());
        }
    }

    @Test
    void dayCloseComputesPnL() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("owner", null));
        Party p = partyRepository.save(Party.builder().name("X").build());
        LocalDate businessDate = LocalDate.now().plusDays(30);
        LocalDateTime stamp = businessDate.atTime(10, 0);

        tradingService.createDeal(new TradingDtos.DealCreateRequest(DealType.BUY, p.getId(), InstrumentCode.USD, new BigDecimal("100"), new BigDecimal("120"), stamp, "buy"));
        tradingService.createDeal(new TradingDtos.DealCreateRequest(DealType.SELL, p.getId(), InstrumentCode.USD, new BigDecimal("50"), new BigDecimal("122"), stamp.plusMinutes(30), "sell"));
        tradingService.createExpense(new TradingDtos.ExpenseCreateRequest(com.doller.platform.domain.enums.ExpenseType.OFFICE_MANAGEMENT, null, new BigDecimal("100"), stamp.plusHours(1), "staff", ""));

        var preview = tradingService.previewDayClose(businessDate);
        assertEquals(new BigDecimal("-5900.00"), preview.realizedProfitLossBdt());
    }

    @Test
    void rejectsLegacyExpenseTypesForNewEntries() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("owner", null));
        LocalDateTime stamp = LocalDate.now().plusDays(32).atTime(10, 0);
        assertThrows(RuntimeException.class, () -> tradingService.createExpense(
                new TradingDtos.ExpenseCreateRequest(
                        com.doller.platform.domain.enums.ExpenseType.DAILY_OVERHEAD,
                        null,
                        new BigDecimal("100"),
                        stamp,
                        "legacy",
                        ""
                )
        ));
    }

    @Test
    void dayCloseTracksUsdQuantityNotBdtGross() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("owner", null));
        Party p = partyRepository.save(Party.builder().name("USD Party").build());
        LocalDate businessDate = LocalDate.now().plusDays(31);
        LocalDateTime stamp = businessDate.atTime(9, 0);

        tradingService.createDeal(new TradingDtos.DealCreateRequest(
                DealType.BUY, p.getId(), InstrumentCode.USD, new BigDecimal("10"), new BigDecimal("120"),
                stamp, "buy"
        ));
        tradingService.createDeal(new TradingDtos.DealCreateRequest(
                DealType.SELL, p.getId(), InstrumentCode.USD, new BigDecimal("5"), new BigDecimal("130"),
                stamp.plusMinutes(30), "sell"
        ));

        var result = tradingService.confirmDayClose(businessDate);
        var report = tradingService.balanceSheetReport("DAILY", businessDate, null, null, null, null);
        assertEquals(0, result.closingUsd().compareTo(new BigDecimal("5.000000")));
        assertEquals(0, report.closingUsd().compareTo(new BigDecimal("5.000000")));
        assertEquals(0, report.closingReceivableBdt().compareTo(new BigDecimal("650.00")));
        assertEquals(0, report.closingPayableBdt().compareTo(new BigDecimal("1200.00")));
        assertEquals(0, report.closingAgingBdt().compareTo(new BigDecimal("650.00")));
    }

    @Test
    void incomingSettlementReducesReceivableAndCreatesAdvance() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("owner", null));
        Party p = partyRepository.save(Party.builder().name("Customer A").build());

        tradingService.createDeal(new TradingDtos.DealCreateRequest(
                DealType.SELL, p.getId(), InstrumentCode.USD, new BigDecimal("100"), new BigDecimal("120"),
                LocalDateTime.now(), "sell"
        ));

        var inference = tradingService.settlementInference(p.getId(), null, new BigDecimal("15000"));
        assertEquals("INCOMING", inference.direction().name());
        assertEquals(new BigDecimal("12000.00"), inference.appliedAmount());
        assertEquals(new BigDecimal("3000.00"), inference.advanceAmount());

        tradingService.createSettlement(new TradingDtos.SettlementCreateRequest(
                p.getId(), null, new BigDecimal("15000"), LocalDateTime.now(), SettlementPaymentMethod.CASH, null, "paid", true
        ));

        var ledger = tradingService.partyLedger(p.getId());
        assertEquals(new BigDecimal("0.00"), ledger.balances().receivableBdt());
        assertEquals(new BigDecimal("3000.00"), ledger.balances().advanceFromPartyBdt());
        assertTrue(ledger.lines().stream().anyMatch(line -> line.kind().contains("SETTLEMENT-INCOMING")));
    }

    @Test
    void outgoingSettlementReducesPayable() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("owner", null));
        Party p = partyRepository.save(Party.builder().name("Supplier B").build());

        tradingService.createDeal(new TradingDtos.DealCreateRequest(
                DealType.BUY, p.getId(), InstrumentCode.USD, new BigDecimal("50"), new BigDecimal("130"),
                LocalDateTime.now(), "buy"
        ));

        var inference = tradingService.settlementInference(p.getId(), null, new BigDecimal("2000"));
        assertEquals("OUTGOING", inference.direction().name());
        assertEquals("PAYABLE", inference.basis().name());

        tradingService.createSettlement(new TradingDtos.SettlementCreateRequest(
                p.getId(), null, new BigDecimal("2000"), LocalDateTime.now(), SettlementPaymentMethod.BANK, "Bank transfer", "partial pay", false
        ));

        var ledger = tradingService.partyLedger(p.getId());
        assertEquals(new BigDecimal("4500.00"), ledger.balances().payableBdt());
        assertEquals(0, ledger.balances().advanceToPartyBdt().compareTo(BigDecimal.ZERO));
    }

    @Test
    void duesSnapshotAggregatesTotalsAndLastActivity() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("owner", null));
        Party customer = partyRepository.save(Party.builder().name("Customer R").phone("01700").notes("vip").build());
        Party supplier = partyRepository.save(Party.builder().name("Supplier P").build());

        LocalDateTime t1 = LocalDateTime.now().minusDays(2);
        LocalDateTime t2 = LocalDateTime.now().minusDays(1);
        LocalDateTime t3 = LocalDateTime.now();

        tradingService.createDeal(new TradingDtos.DealCreateRequest(
                DealType.SELL, customer.getId(), InstrumentCode.USD, new BigDecimal("100"), new BigDecimal("120"),
                t1, "sell receivable"
        ));
        tradingService.createDeal(new TradingDtos.DealCreateRequest(
                DealType.BUY, supplier.getId(), InstrumentCode.USD, new BigDecimal("50"), new BigDecimal("130"),
                t2, "buy payable"
        ));
        tradingService.createSettlement(new TradingDtos.SettlementCreateRequest(
                customer.getId(), null, new BigDecimal("2000"), t3, SettlementPaymentMethod.CHECK, "CHK-001", "incoming", false
        ));

        TradingDtos.DuesSnapshotResponse snapshot = tradingService.duesSnapshot();
        assertEquals(0, snapshot.totalReceivableBdt().compareTo(new BigDecimal("10000.00")));
        assertEquals(0, snapshot.totalPayableBdt().compareTo(new BigDecimal("6500.00")));
        assertEquals(0, snapshot.grossBdt().compareTo(new BigDecimal("16500.00")));
        assertEquals(0, snapshot.netBdt().compareTo(new BigDecimal("3500.00")));
        assertEquals(2, snapshot.rows().size());

        TradingDtos.PartyDueRow customerRow = snapshot.rows().stream()
                .filter(row -> row.partyId().equals(customer.getId()))
                .findFirst()
                .orElseThrow();
        assertEquals("Customer R", customerRow.partyName());
        assertEquals("01700", customerRow.phone());
        assertEquals("vip", customerRow.notes());
        assertEquals(0, customerRow.receivableBdt().compareTo(new BigDecimal("10000.00")));
        assertEquals(0, customerRow.payableBdt().compareTo(BigDecimal.ZERO.setScale(2)));
        assertTrue(customerRow.lastActivityAt() != null && !customerRow.lastActivityAt().isBefore(t3.withNano(0)));
    }

    @Test
    void openingBalanceReflectsInPartyLedgerAndTransactionDetailsWithoutDeals() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("owner", null));
        var created = masterDataService.createParty(new com.doller.platform.dto.MasterDataDtos.PartyCreateRequest(
                "Opening Only",
                "019",
                "Gulshan",
                "opening",
                new BigDecimal("1200"),
                new BigDecimal("300")
        ));

        var ledger = tradingService.partyLedger(created.getId());
        assertEquals(0, ledger.balances().receivableBdt().compareTo(new BigDecimal("1200.00")));
        assertEquals(0, ledger.balances().payableBdt().compareTo(new BigDecimal("300.00")));
        assertTrue(ledger.lines().stream().anyMatch(line -> line.kind().startsWith("OPENING_BALANCE-RECEIVABLE")));
        assertTrue(ledger.lines().stream().anyMatch(line -> line.kind().startsWith("OPENING_BALANCE-PAYABLE")));

        var dues = tradingService.duesSnapshot();
        TradingDtos.PartyDueRow row = dues.rows().stream()
                .filter(r -> r.partyId().equals(created.getId()))
                .findFirst()
                .orElseThrow();
        assertEquals(0, row.receivableBdt().compareTo(new BigDecimal("1200.00")));
        assertEquals(0, row.payableBdt().compareTo(new BigDecimal("300.00")));

        var tx = tradingService.transactionDetails(
                LocalDate.now().minusDays(1),
                LocalDate.now().plusDays(1),
                "OPENING_BALANCE",
                created.getId(),
                null,
                "occurredAt",
                "asc"
        );
        assertTrue(tx.rows().stream().allMatch(r -> "OPENING_BALANCE".equals(r.entryType())));
        assertTrue(tx.rows().stream().anyMatch(r -> "OPENING RECEIVABLE".equals(r.directionLabel())));
        assertTrue(tx.rows().stream().anyMatch(r -> "OPENING PAYABLE".equals(r.directionLabel())));
    }
}
