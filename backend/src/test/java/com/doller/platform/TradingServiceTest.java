package com.doller.platform;

import com.doller.platform.domain.Party;
import com.doller.platform.domain.StatementSnapshot;
import com.doller.platform.domain.TradeDeal;
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
        assertEquals(0, preview.realizedProfitLossBdt().compareTo(new BigDecimal("100.00")));
    }

    @Test
    void fifoUsesExistingInventoryCostForRealizedPnl() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("owner", null));
        Party p = partyRepository.save(Party.builder().name("FIFO Party").build());
        LocalDate baseDate = LocalDate.now().plusDays(40);
        LocalDateTime t1 = baseDate.minusDays(2).atTime(9, 0);
        LocalDateTime t2 = baseDate.minusDays(1).atTime(9, 0);
        LocalDateTime t3 = baseDate.atTime(10, 0);

        tradingService.createDeal(new TradingDtos.DealCreateRequest(
                DealType.BUY, p.getId(), InstrumentCode.USD, new BigDecimal("8"), new BigDecimal("100"),
                t1, "old stock"
        ));
        tradingService.createDeal(new TradingDtos.DealCreateRequest(
                DealType.BUY, p.getId(), InstrumentCode.USD, new BigDecimal("10"), new BigDecimal("100"),
                t2, "new buy"
        ));
        tradingService.createDeal(new TradingDtos.DealCreateRequest(
                DealType.SELL, p.getId(), InstrumentCode.USD, new BigDecimal("2"), new BigDecimal("120"),
                t3, "sell"
        ));

        var explain = tradingService.dashboardPnlExplain("CUSTOM", null, null, null, baseDate, baseDate);
        assertEquals(0, explain.period().grossPnlBdt().compareTo(new BigDecimal("40.00")));
        assertEquals(0, explain.period().longFifoRealizedPnlBdt().compareTo(new BigDecimal("40.00")));
        assertEquals(0, explain.period().shortCoverRealizedPnlBdt().compareTo(BigDecimal.ZERO));
    }

    @Test
    void sellFirstRealizesWhenCovered() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("owner", null));
        Party p = partyRepository.save(Party.builder().name("Short Party").build());
        LocalDate baseDate = LocalDate.now().plusDays(41);
        LocalDateTime sellAt = baseDate.atTime(10, 0);
        LocalDateTime buyAt = baseDate.atTime(11, 0);

        tradingService.createDeal(new TradingDtos.DealCreateRequest(
                DealType.SELL, p.getId(), InstrumentCode.USD, new BigDecimal("5"), new BigDecimal("120"),
                sellAt, "short open"
        ));
        tradingService.createDeal(new TradingDtos.DealCreateRequest(
                DealType.BUY, p.getId(), InstrumentCode.USD, new BigDecimal("3"), new BigDecimal("100"),
                buyAt, "partial cover"
        ));

        var explain = tradingService.dashboardPnlExplain("CUSTOM", null, null, null, baseDate, baseDate);
        assertEquals(0, explain.period().grossPnlBdt().compareTo(new BigDecimal("60.00")));
        assertEquals(0, explain.period().shortCoverRealizedPnlBdt().compareTo(new BigDecimal("60.00")));
        assertEquals(0, explain.period().openShortQty().compareTo(new BigDecimal("2.000000")));
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
        assertEquals(0, report.closingReceivableBdt().compareTo(new BigDecimal("0.00")));
        assertEquals(0, report.closingPayableBdt().compareTo(new BigDecimal("550.00")));
        assertEquals(0, report.closingAgingBdt().compareTo(new BigDecimal("650.00")));
    }

    @Test
    void incomingSettlementReducesReceivableAndCreatesPayableOverflow() {
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
        assertEquals(new BigDecimal("3000.00"), ledger.balances().payableBdt());
        assertEquals(0, ledger.balances().advanceFromPartyBdt().compareTo(BigDecimal.ZERO));
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
    void buyDealImmediatelyAppearsAsPayableInDuesAndLedger() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("owner", null));
        Party party = partyRepository.save(Party.builder().name("Immediate Payable").build());
        LocalDateTime stamp = LocalDateTime.now();

        tradingService.createDeal(new TradingDtos.DealCreateRequest(
                DealType.BUY, party.getId(), InstrumentCode.USD, new BigDecimal("10"), new BigDecimal("100"),
                stamp, "buy only"
        ));

        var dues = tradingService.duesSnapshot();
        TradingDtos.PartyDueRow row = dues.rows().stream()
                .filter(r -> r.partyId().equals(party.getId()))
                .findFirst()
                .orElseThrow();
        assertEquals(0, row.receivableBdt().compareTo(new BigDecimal("0.00")));
        assertEquals(0, row.payableBdt().compareTo(new BigDecimal("1000.00")));
        assertEquals(0, dues.totalPayableBdt().compareTo(new BigDecimal("1000.00")));

        var ledger = tradingService.partyLedger(party.getId());
        assertEquals(0, ledger.balances().payableBdt().compareTo(new BigDecimal("1000.00")));
    }

    @Test
    void sellDealImmediatelyAppearsAsReceivableInDuesAndLedger() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("owner", null));
        Party party = partyRepository.save(Party.builder().name("Immediate Receivable").build());
        LocalDateTime stamp = LocalDateTime.now();

        tradingService.createDeal(new TradingDtos.DealCreateRequest(
                DealType.SELL, party.getId(), InstrumentCode.USD, new BigDecimal("10"), new BigDecimal("100"),
                stamp, "sell only"
        ));

        var dues = tradingService.duesSnapshot();
        TradingDtos.PartyDueRow row = dues.rows().stream()
                .filter(r -> r.partyId().equals(party.getId()))
                .findFirst()
                .orElseThrow();
        assertEquals(0, row.receivableBdt().compareTo(new BigDecimal("1000.00")));
        assertEquals(0, row.payableBdt().compareTo(new BigDecimal("0.00")));
        assertEquals(0, dues.totalReceivableBdt().compareTo(new BigDecimal("1000.00")));

        var ledger = tradingService.partyLedger(party.getId());
        assertEquals(0, ledger.balances().receivableBdt().compareTo(new BigDecimal("1000.00")));
    }

    @Test
    void sellDealAutoConsumesExistingAdvanceInBeforeCreatingReceivable() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("owner", null));
        Party party = partyRepository.save(Party.builder().name("Advance In Consume").build());
        LocalDateTime t0 = LocalDateTime.now().minusMinutes(3);
        LocalDateTime t1 = LocalDateTime.now().minusMinutes(2);
        LocalDateTime t2 = LocalDateTime.now().minusMinutes(1);

        tradingService.createDeal(new TradingDtos.DealCreateRequest(
                DealType.SELL, party.getId(), InstrumentCode.USD, new BigDecimal("1"), new BigDecimal("10"),
                t0, "seed receivable"
        ));
        tradingService.createSettlement(new TradingDtos.SettlementCreateRequest(
                party.getId(), null, new BigDecimal("510"), t1, SettlementPaymentMethod.CASH, null, "incoming advance", true
        ));
        tradingService.createDeal(new TradingDtos.DealCreateRequest(
                DealType.SELL, party.getId(), InstrumentCode.USD, new BigDecimal("10"), new BigDecimal("20"),
                t2, "sell against advance"
        ));

        var ledger = tradingService.partyLedger(party.getId());
        assertEquals(0, ledger.balances().advanceFromPartyBdt().compareTo(new BigDecimal("0.00")));
        assertEquals(0, ledger.balances().receivableBdt().compareTo(new BigDecimal("0.00")));
        assertEquals(0, ledger.balances().payableBdt().compareTo(new BigDecimal("300.00")));
        assertEquals(0, ledger.balances().netBalanceBdt().compareTo(new BigDecimal("-300.00")));

        LocalDateTime cutoff = LocalDateTime.now().plusMinutes(1);
        assertEquals(0, ledgerEntryRepository.netForAccountUntil("PAYABLE_" + party.getId(), cutoff)
                .negate()
                .compareTo(new BigDecimal("300.00")));
        assertEquals(0, ledgerEntryRepository.netForAccountUntil("RECEIVABLE_" + party.getId(), cutoff)
                .compareTo(new BigDecimal("0.00")));
    }

    @Test
    void sellDealCreatesReceivableForAmountBeyondAdvanceIn() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("owner", null));
        Party party = partyRepository.save(Party.builder().name("Advance In Partial").build());
        LocalDateTime t0 = LocalDateTime.now().minusMinutes(3);
        LocalDateTime t1 = LocalDateTime.now().minusMinutes(2);
        LocalDateTime t2 = LocalDateTime.now().minusMinutes(1);

        tradingService.createDeal(new TradingDtos.DealCreateRequest(
                DealType.SELL, party.getId(), InstrumentCode.USD, new BigDecimal("1"), new BigDecimal("10"),
                t0, "seed receivable"
        ));
        tradingService.createSettlement(new TradingDtos.SettlementCreateRequest(
                party.getId(), null, new BigDecimal("510"), t1, SettlementPaymentMethod.CASH, null, "incoming advance", true
        ));
        tradingService.createDeal(new TradingDtos.DealCreateRequest(
                DealType.SELL, party.getId(), InstrumentCode.USD, new BigDecimal("10"), new BigDecimal("80"),
                t2, "sell larger than advance"
        ));

        var ledger = tradingService.partyLedger(party.getId());
        assertEquals(0, ledger.balances().advanceFromPartyBdt().compareTo(new BigDecimal("0.00")));
        assertEquals(0, ledger.balances().receivableBdt().compareTo(new BigDecimal("300.00")));
        assertEquals(0, ledger.balances().netBalanceBdt().compareTo(new BigDecimal("300.00")));
    }

    @Test
    void buyDealAutoConsumesExistingAdvanceOutBeforeCreatingPayable() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("owner", null));
        Party party = partyRepository.save(Party.builder().name("Advance Out Consume").build());
        LocalDateTime t0 = LocalDateTime.now().minusMinutes(3);
        LocalDateTime t1 = LocalDateTime.now().minusMinutes(2);
        LocalDateTime t2 = LocalDateTime.now().minusMinutes(1);

        tradingService.createDeal(new TradingDtos.DealCreateRequest(
                DealType.BUY, party.getId(), InstrumentCode.USD, new BigDecimal("1"), new BigDecimal("10"),
                t0, "seed payable"
        ));
        tradingService.createSettlement(new TradingDtos.SettlementCreateRequest(
                party.getId(), null, new BigDecimal("510"), t1, SettlementPaymentMethod.CASH, null, "outgoing advance", true
        ));
        tradingService.createDeal(new TradingDtos.DealCreateRequest(
                DealType.BUY, party.getId(), InstrumentCode.USD, new BigDecimal("10"), new BigDecimal("20"),
                t2, "buy against advance"
        ));

        var ledger = tradingService.partyLedger(party.getId());
        assertEquals(0, ledger.balances().advanceToPartyBdt().compareTo(new BigDecimal("0.00")));
        assertEquals(0, ledger.balances().payableBdt().compareTo(new BigDecimal("0.00")));
        assertEquals(0, ledger.balances().receivableBdt().compareTo(new BigDecimal("300.00")));
        assertEquals(0, ledger.balances().netBalanceBdt().compareTo(new BigDecimal("300.00")));

        LocalDateTime cutoff = LocalDateTime.now().plusMinutes(1);
        assertEquals(0, ledgerEntryRepository.netForAccountUntil("RECEIVABLE_" + party.getId(), cutoff)
                .compareTo(new BigDecimal("300.00")));
        assertEquals(0, ledgerEntryRepository.netForAccountUntil("PAYABLE_" + party.getId(), cutoff)
                .negate()
                .compareTo(new BigDecimal("0.00")));
    }

    @Test
    void buyDealCreatesPayableForAmountBeyondAdvanceOut() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("owner", null));
        Party party = partyRepository.save(Party.builder().name("Advance Out Partial").build());
        LocalDateTime t0 = LocalDateTime.now().minusMinutes(3);
        LocalDateTime t1 = LocalDateTime.now().minusMinutes(2);
        LocalDateTime t2 = LocalDateTime.now().minusMinutes(1);

        tradingService.createDeal(new TradingDtos.DealCreateRequest(
                DealType.BUY, party.getId(), InstrumentCode.USD, new BigDecimal("1"), new BigDecimal("10"),
                t0, "seed payable"
        ));
        tradingService.createSettlement(new TradingDtos.SettlementCreateRequest(
                party.getId(), null, new BigDecimal("510"), t1, SettlementPaymentMethod.CASH, null, "outgoing advance", true
        ));
        tradingService.createDeal(new TradingDtos.DealCreateRequest(
                DealType.BUY, party.getId(), InstrumentCode.USD, new BigDecimal("10"), new BigDecimal("80"),
                t2, "buy larger than advance"
        ));

        var ledger = tradingService.partyLedger(party.getId());
        assertEquals(0, ledger.balances().advanceToPartyBdt().compareTo(new BigDecimal("0.00")));
        assertEquals(0, ledger.balances().payableBdt().compareTo(new BigDecimal("300.00")));
        assertEquals(0, ledger.balances().netBalanceBdt().compareTo(new BigDecimal("-300.00")));
    }

    @Test
    void sellDealAutoNetsExistingPayableBeforeCreatingReceivable() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("owner", null));
        Party party = partyRepository.save(Party.builder().name("Payable Net By Sell").build());
        LocalDateTime t1 = LocalDateTime.now().minusMinutes(2);
        LocalDateTime t2 = LocalDateTime.now().minusMinutes(1);

        tradingService.createDeal(new TradingDtos.DealCreateRequest(
                DealType.BUY, party.getId(), InstrumentCode.USD, new BigDecimal("20"), new BigDecimal("100"),
                t1, "existing payable 2000"
        ));
        tradingService.createDeal(new TradingDtos.DealCreateRequest(
                DealType.SELL, party.getId(), InstrumentCode.USD, new BigDecimal("15"), new BigDecimal("100"),
                t2, "sell 1500"
        ));

        var ledger = tradingService.partyLedger(party.getId());
        assertEquals(0, ledger.balances().payableBdt().compareTo(new BigDecimal("500.00")));
        assertEquals(0, ledger.balances().receivableBdt().compareTo(new BigDecimal("0.00")));
        assertEquals(0, ledger.balances().netBalanceBdt().compareTo(new BigDecimal("-500.00")));
    }

    @Test
    void sellDealCreatesReceivableForAmountBeyondExistingPayable() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("owner", null));
        Party party = partyRepository.save(Party.builder().name("Payable Partial Net").build());
        LocalDateTime t1 = LocalDateTime.now().minusMinutes(2);
        LocalDateTime t2 = LocalDateTime.now().minusMinutes(1);

        tradingService.createDeal(new TradingDtos.DealCreateRequest(
                DealType.BUY, party.getId(), InstrumentCode.USD, new BigDecimal("20"), new BigDecimal("100"),
                t1, "existing payable 2000"
        ));
        tradingService.createDeal(new TradingDtos.DealCreateRequest(
                DealType.SELL, party.getId(), InstrumentCode.USD, new BigDecimal("25"), new BigDecimal("100"),
                t2, "sell 2500"
        ));

        var ledger = tradingService.partyLedger(party.getId());
        assertEquals(0, ledger.balances().payableBdt().compareTo(new BigDecimal("0.00")));
        assertEquals(0, ledger.balances().receivableBdt().compareTo(new BigDecimal("500.00")));
        assertEquals(0, ledger.balances().netBalanceBdt().compareTo(new BigDecimal("500.00")));

        LocalDateTime cutoff = LocalDateTime.now().plusMinutes(1);
        assertEquals(0, ledgerEntryRepository.netForAccountUntil("PAYABLE_" + party.getId(), cutoff)
                .negate()
                .compareTo(new BigDecimal("0.00")));
        assertEquals(0, ledgerEntryRepository.netForAccountUntil("RECEIVABLE_" + party.getId(), cutoff)
                .compareTo(new BigDecimal("500.00")));
    }

    @Test
    void buyDealAutoNetsExistingReceivableBeforeCreatingPayable() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("owner", null));
        Party party = partyRepository.save(Party.builder().name("Receivable Net By Buy").build());
        LocalDateTime t1 = LocalDateTime.now().minusMinutes(2);
        LocalDateTime t2 = LocalDateTime.now().minusMinutes(1);

        tradingService.createDeal(new TradingDtos.DealCreateRequest(
                DealType.SELL, party.getId(), InstrumentCode.USD, new BigDecimal("20"), new BigDecimal("100"),
                t1, "existing receivable 2000"
        ));
        tradingService.createDeal(new TradingDtos.DealCreateRequest(
                DealType.BUY, party.getId(), InstrumentCode.USD, new BigDecimal("15"), new BigDecimal("100"),
                t2, "buy 1500"
        ));

        var ledger = tradingService.partyLedger(party.getId());
        assertEquals(0, ledger.balances().receivableBdt().compareTo(new BigDecimal("500.00")));
        assertEquals(0, ledger.balances().payableBdt().compareTo(new BigDecimal("0.00")));
        assertEquals(0, ledger.balances().netBalanceBdt().compareTo(new BigDecimal("500.00")));
    }

    @Test
    void buyDealCreatesPayableForAmountBeyondExistingReceivable() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("owner", null));
        Party party = partyRepository.save(Party.builder().name("Receivable Partial Net").build());
        LocalDateTime t1 = LocalDateTime.now().minusMinutes(2);
        LocalDateTime t2 = LocalDateTime.now().minusMinutes(1);

        tradingService.createDeal(new TradingDtos.DealCreateRequest(
                DealType.SELL, party.getId(), InstrumentCode.USD, new BigDecimal("20"), new BigDecimal("100"),
                t1, "existing receivable 2000"
        ));
        tradingService.createDeal(new TradingDtos.DealCreateRequest(
                DealType.BUY, party.getId(), InstrumentCode.USD, new BigDecimal("25"), new BigDecimal("100"),
                t2, "buy 2500"
        ));

        var ledger = tradingService.partyLedger(party.getId());
        assertEquals(0, ledger.balances().receivableBdt().compareTo(new BigDecimal("0.00")));
        assertEquals(0, ledger.balances().payableBdt().compareTo(new BigDecimal("500.00")));
        assertEquals(0, ledger.balances().netBalanceBdt().compareTo(new BigDecimal("-500.00")));

        LocalDateTime cutoff = LocalDateTime.now().plusMinutes(1);
        assertEquals(0, ledgerEntryRepository.netForAccountUntil("RECEIVABLE_" + party.getId(), cutoff)
                .compareTo(new BigDecimal("0.00")));
        assertEquals(0, ledgerEntryRepository.netForAccountUntil("PAYABLE_" + party.getId(), cutoff)
                .negate()
                .compareTo(new BigDecimal("500.00")));
    }

    @Test
    void sellDealConsumesAdvanceInBeforePayableWhenBothExist() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("owner", null));
        Party party = partyRepository.save(Party.builder().name("Sell Priority Advance Then Due").build());
        LocalDateTime t0 = LocalDateTime.now().minusMinutes(4);
        LocalDateTime t1 = LocalDateTime.now().minusMinutes(3);
        LocalDateTime t2 = LocalDateTime.now().minusMinutes(2);
        LocalDateTime t3 = LocalDateTime.now().minusMinutes(1);

        tradingService.createDeal(new TradingDtos.DealCreateRequest(
                DealType.SELL, party.getId(), InstrumentCode.USD, new BigDecimal("1"), new BigDecimal("100"),
                t0, "seed receivable"
        ));
        tradingService.createSettlement(new TradingDtos.SettlementCreateRequest(
                party.getId(), null, new BigDecimal("250"), t1, SettlementPaymentMethod.CASH, null, "advance in 150", true
        ));
        tradingService.createDeal(new TradingDtos.DealCreateRequest(
                DealType.BUY, party.getId(), InstrumentCode.USD, new BigDecimal("3"), new BigDecimal("100"),
                t2, "create payable 300"
        ));
        tradingService.createDeal(new TradingDtos.DealCreateRequest(
                DealType.SELL, party.getId(), InstrumentCode.USD, new BigDecimal("2"), new BigDecimal("100"),
                t3, "sell 200 should consume advance 150 then payable 50"
        ));

        var ledger = tradingService.partyLedger(party.getId());
        assertEquals(0, ledger.balances().advanceFromPartyBdt().compareTo(new BigDecimal("0.00")));
        assertEquals(0, ledger.balances().payableBdt().compareTo(new BigDecimal("250.00")));
        assertEquals(0, ledger.balances().receivableBdt().compareTo(new BigDecimal("0.00")));
    }

    @Test
    void buyDealConsumesAdvanceOutBeforeReceivableWhenBothExist() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("owner", null));
        Party party = partyRepository.save(Party.builder().name("Buy Priority Advance Then Due").build());
        LocalDateTime t0 = LocalDateTime.now().minusMinutes(4);
        LocalDateTime t1 = LocalDateTime.now().minusMinutes(3);
        LocalDateTime t2 = LocalDateTime.now().minusMinutes(2);
        LocalDateTime t3 = LocalDateTime.now().minusMinutes(1);

        tradingService.createDeal(new TradingDtos.DealCreateRequest(
                DealType.BUY, party.getId(), InstrumentCode.USD, new BigDecimal("1"), new BigDecimal("100"),
                t0, "seed payable"
        ));
        tradingService.createSettlement(new TradingDtos.SettlementCreateRequest(
                party.getId(), null, new BigDecimal("250"), t1, SettlementPaymentMethod.CASH, null, "advance out 150", true
        ));
        tradingService.createDeal(new TradingDtos.DealCreateRequest(
                DealType.SELL, party.getId(), InstrumentCode.USD, new BigDecimal("3"), new BigDecimal("100"),
                t2, "create receivable 300"
        ));
        tradingService.createDeal(new TradingDtos.DealCreateRequest(
                DealType.BUY, party.getId(), InstrumentCode.USD, new BigDecimal("2"), new BigDecimal("100"),
                t3, "buy 200 should consume advance 150 then receivable 50"
        ));

        var ledger = tradingService.partyLedger(party.getId());
        assertEquals(0, ledger.balances().advanceToPartyBdt().compareTo(new BigDecimal("0.00")));
        assertEquals(0, ledger.balances().receivableBdt().compareTo(new BigDecimal("250.00")));
        assertEquals(0, ledger.balances().payableBdt().compareTo(new BigDecimal("0.00")));
    }

    @Test
    void deletingDealRemovesDueImmediately() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("owner", null));
        Party party = partyRepository.save(Party.builder().name("Delete Due").build());
        LocalDateTime stamp = LocalDateTime.now();

        TradeDeal deal = tradingService.createDeal(new TradingDtos.DealCreateRequest(
                DealType.BUY, party.getId(), InstrumentCode.USD, new BigDecimal("10"), new BigDecimal("100"),
                stamp, "buy then delete"
        ));
        tradingService.deleteDeal(deal.getId());

        var dues = tradingService.duesSnapshot();
        TradingDtos.PartyDueRow row = dues.rows().stream()
                .filter(r -> r.partyId().equals(party.getId()))
                .findFirst()
                .orElseThrow();
        assertEquals(0, row.receivableBdt().compareTo(new BigDecimal("0.00")));
        assertEquals(0, row.payableBdt().compareTo(new BigDecimal("0.00")));
    }

    @Test
    void partialSettlementReducesDueImmediately() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("owner", null));
        Party party = partyRepository.save(Party.builder().name("Partial Settlement").build());
        LocalDateTime stamp = LocalDateTime.now();

        tradingService.createDeal(new TradingDtos.DealCreateRequest(
                DealType.BUY, party.getId(), InstrumentCode.USD, new BigDecimal("10"), new BigDecimal("100"),
                stamp, "buy first"
        ));
        tradingService.createSettlement(new TradingDtos.SettlementCreateRequest(
                party.getId(), null, new BigDecimal("400"), stamp, SettlementPaymentMethod.CASH, null, "partial pay", false
        ));

        var dues = tradingService.duesSnapshot();
        TradingDtos.PartyDueRow row = dues.rows().stream()
                .filter(r -> r.partyId().equals(party.getId()))
                .findFirst()
                .orElseThrow();
        assertEquals(0, row.payableBdt().compareTo(new BigDecimal("600.00")));
        assertEquals(0, row.receivableBdt().compareTo(new BigDecimal("0.00")));
    }

    @Test
    void duesProjectionStaysConsistentWithLedgerForSimpleDealFlows() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("owner", null));
        Party buyParty = partyRepository.save(Party.builder().name("Ledger Buy").build());
        Party sellParty = partyRepository.save(Party.builder().name("Ledger Sell").build());
        LocalDateTime stamp = LocalDateTime.now();

        tradingService.createDeal(new TradingDtos.DealCreateRequest(
                DealType.BUY, buyParty.getId(), InstrumentCode.USD, new BigDecimal("10"), new BigDecimal("100"),
                stamp, "buy"
        ));
        tradingService.createDeal(new TradingDtos.DealCreateRequest(
                DealType.SELL, sellParty.getId(), InstrumentCode.USD, new BigDecimal("8"), new BigDecimal("125"),
                stamp, "sell"
        ));

        var dues = tradingService.duesSnapshot();
        TradingDtos.PartyDueRow buyRow = dues.rows().stream()
                .filter(r -> r.partyId().equals(buyParty.getId()))
                .findFirst()
                .orElseThrow();
        TradingDtos.PartyDueRow sellRow = dues.rows().stream()
                .filter(r -> r.partyId().equals(sellParty.getId()))
                .findFirst()
                .orElseThrow();

        LocalDateTime cutoff = stamp.plusMinutes(2);
        assertEquals(0, buyRow.payableBdt().compareTo(new BigDecimal("1000.00")));
        assertEquals(0, ledgerEntryRepository.netForAccountUntil("PAYABLE_" + buyParty.getId(), cutoff)
                .negate()
                .compareTo(buyRow.payableBdt()));
        assertEquals(0, sellRow.receivableBdt().compareTo(new BigDecimal("1000.00")));
        assertEquals(0, ledgerEntryRepository.netForAccountUntil("RECEIVABLE_" + sellParty.getId(), cutoff)
                .compareTo(sellRow.receivableBdt()));
    }

    @Test
    void balanceReadersShareSingleProjectionAcrossAllSurfaces() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("owner", null));
        Party buyParty = partyRepository.save(Party.builder().name("Projection Buy").build());
        Party sellParty = partyRepository.save(Party.builder().name("Projection Sell").build());
        LocalDate day = LocalDate.now();

        tradingService.createDeal(new TradingDtos.DealCreateRequest(
                DealType.BUY, buyParty.getId(), InstrumentCode.USD, new BigDecimal("10"), new BigDecimal("100"),
                day.atTime(10, 0), "buy"
        ));
        tradingService.createDeal(new TradingDtos.DealCreateRequest(
                DealType.SELL, sellParty.getId(), InstrumentCode.USD, new BigDecimal("8"), new BigDecimal("125"),
                day.atTime(11, 0), "sell"
        ));

        var dues = tradingService.duesSnapshot();
        assertEquals(0, dues.totalReceivableBdt().compareTo(new BigDecimal("1000.00")));
        assertEquals(0, dues.totalPayableBdt().compareTo(new BigDecimal("1000.00")));

        var buyLedger = tradingService.partyLedger(buyParty.getId());
        var sellLedger = tradingService.partyLedger(sellParty.getId());
        assertEquals(0, buyLedger.balances().payableBdt().compareTo(new BigDecimal("1000.00")));
        assertEquals(0, sellLedger.balances().receivableBdt().compareTo(new BigDecimal("1000.00")));

        var buyInference = tradingService.settlementInference(buyParty.getId(), null, new BigDecimal("100"));
        var sellInference = tradingService.settlementInference(sellParty.getId(), null, new BigDecimal("100"));
        assertEquals(0, buyInference.current().payableBdt().compareTo(new BigDecimal("1000.00")));
        assertEquals(0, sellInference.current().receivableBdt().compareTo(new BigDecimal("1000.00")));

        var report = tradingService.balanceSheetReport("DAILY", day, null, null, null, null);
        assertEquals(0, report.closingReceivableBdt().compareTo(new BigDecimal("1000.00")));
        assertEquals(0, report.closingPayableBdt().compareTo(new BigDecimal("1000.00")));

        var dashboard = tradingService.dashboard(day, day);
        assertEquals(0, dashboard.receivableBdt().compareTo(new BigDecimal("1000.00")));
        assertEquals(0, dashboard.payableBdt().compareTo(new BigDecimal("1000.00")));

        TradingDtos.TransactionExportReport export = tradingService.transactionExportReport(day, day, null, null, null, "occurredAt", "asc");
        TradingDtos.TransactionPartyExportSection buySection = export.partySections().stream()
                .filter(section -> section.party().partyId().equals(buyParty.getId()))
                .findFirst()
                .orElseThrow();
        TradingDtos.TransactionPartyExportSection sellSection = export.partySections().stream()
                .filter(section -> section.party().partyId().equals(sellParty.getId()))
                .findFirst()
                .orElseThrow();
        assertEquals(0, buySection.exposureSummary().payableBdt().compareTo(new BigDecimal("1000.00")));
        assertEquals(0, sellSection.exposureSummary().receivableBdt().compareTo(new BigDecimal("1000.00")));
        assertEquals(0, export.grandExposureSummary().receivableBdt().compareTo(new BigDecimal("1000.00")));
        assertEquals(0, export.grandExposureSummary().payableBdt().compareTo(new BigDecimal("1000.00")));
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

    @Test
    void balanceSheetReportUsesLiveRecomputeWithoutDayClose() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("owner", null));
        Party party = partyRepository.save(Party.builder().name("Live Party").build());
        LocalDate day = LocalDate.now().minusDays(1);

        tradingService.createDeal(new TradingDtos.DealCreateRequest(
                DealType.SELL, party.getId(), InstrumentCode.USD, new BigDecimal("10"), new BigDecimal("110"),
                day.atTime(10, 0), "sell"
        ));
        tradingService.createSettlement(new TradingDtos.SettlementCreateRequest(
                party.getId(), null, new BigDecimal("500"), day.atTime(12, 0), SettlementPaymentMethod.CASH, null, "incoming", false
        ));
        tradingService.createExpense(new TradingDtos.ExpenseCreateRequest(
                com.doller.platform.domain.enums.ExpenseType.OFFICE_MANAGEMENT,
                null,
                new BigDecimal("100"),
                day.atTime(13, 0),
                "cost",
                ""
        ));

        var report = tradingService.balanceSheetReport("DAILY", day, null, null, null, null);
        var explain = tradingService.dashboardPnlExplain("CUSTOM", null, null, null, day, day);
        assertEquals(0, report.closingReceivableBdt().compareTo(new BigDecimal("600.00")));
        assertEquals(0, report.totalPnl().compareTo(explain.period().netPnlBdt()));
    }

    @Test
    void balanceSheetClosingMatchesDuesAndTotalPnlMatchesDashboardPeriod() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("owner", null));
        Party customer = partyRepository.save(Party.builder().name("Customer Z").build());
        Party supplier = partyRepository.save(Party.builder().name("Supplier Z").build());
        LocalDate from = LocalDate.now().minusDays(3);
        LocalDate to = from.plusDays(1);

        tradingService.createDeal(new TradingDtos.DealCreateRequest(
                DealType.BUY, supplier.getId(), InstrumentCode.USD, new BigDecimal("8"), new BigDecimal("120"),
                from.atTime(9, 0), "buy"
        ));
        tradingService.createDeal(new TradingDtos.DealCreateRequest(
                DealType.SELL, customer.getId(), InstrumentCode.USD, new BigDecimal("8"), new BigDecimal("130"),
                to.atTime(11, 0), "sell"
        ));
        tradingService.createSettlement(new TradingDtos.SettlementCreateRequest(
                customer.getId(), null, new BigDecimal("200"), to.atTime(14, 0), SettlementPaymentMethod.BANK, "ref", "incoming", false
        ));
        tradingService.createExpense(new TradingDtos.ExpenseCreateRequest(
                com.doller.platform.domain.enums.ExpenseType.OFFICE_MANAGEMENT,
                null,
                new BigDecimal("50"),
                to.atTime(15, 0),
                "owner",
                ""
        ));

        var balance = tradingService.balanceSheetReport("CUSTOM", null, null, null, from, to);
        var dues = tradingService.duesSnapshot();
        var explain = tradingService.dashboardPnlExplain("CUSTOM", null, null, null, from, to);

        assertEquals(0, balance.closingReceivableBdt().compareTo(dues.totalReceivableBdt()));
        assertEquals(0, balance.closingPayableBdt().compareTo(dues.totalPayableBdt()));
        assertEquals(0, balance.totalPnl().compareTo(explain.period().netPnlBdt()));
    }

    @Test
    void dailyOpeningIgnoresSnapshotBaselineWhenLedgerHistoryMissing() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("owner", null));
        LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDate today = LocalDate.now();

        statementSnapshotRepository.save(StatementSnapshot.builder()
                .businessDate(yesterday)
                .openingCashBdt(BigDecimal.ZERO)
                .closingCashBdt(new BigDecimal("5000.00"))
                .openingUsd(BigDecimal.ZERO)
                .closingUsd(new BigDecimal("120.000000"))
                .openingReceivableBdt(BigDecimal.ZERO)
                .closingReceivableBdt(BigDecimal.ZERO)
                .openingPayableBdt(BigDecimal.ZERO)
                .closingPayableBdt(BigDecimal.ZERO)
                .openingAdvanceFromPartyBdt(BigDecimal.ZERO)
                .closingAdvanceFromPartyBdt(BigDecimal.ZERO)
                .openingAdvanceToPartyBdt(BigDecimal.ZERO)
                .closingAdvanceToPartyBdt(BigDecimal.ZERO)
                .openingAgingBdt(BigDecimal.ZERO)
                .closingAgingBdt(BigDecimal.ZERO)
                .realizedProfitLossBdt(BigDecimal.ZERO)
                .build());

        var report = tradingService.balanceSheetReport("DAILY", today, null, null, null, null);
        assertEquals(0, report.openingCash().compareTo(BigDecimal.ZERO.setScale(2)));
        assertEquals(0, report.openingUsd().compareTo(BigDecimal.ZERO));
    }

    @Test
    void transactionExportReportGroupsRowsPerPartyAndBuildsSummaries() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("owner", null));
        Party party = partyRepository.save(Party.builder().name("Hasan").phone("01711").address("Dhaka").build());
        LocalDate day = LocalDate.now();

        TradeDeal buyDeal = tradingService.createDeal(new TradingDtos.DealCreateRequest(
                DealType.BUY, party.getId(), InstrumentCode.USD, new BigDecimal("2"), new BigDecimal("120"),
                day.atTime(10, 0), "buy"
        ));
        TradeDeal sellDeal = tradingService.createDeal(new TradingDtos.DealCreateRequest(
                DealType.SELL, party.getId(), InstrumentCode.USD, new BigDecimal("1"), new BigDecimal("130"),
                day.atTime(11, 0), "sell"
        ));
        tradingService.createSettlement(new TradingDtos.SettlementCreateRequest(
                party.getId(), sellDeal.getId(), new BigDecimal("50"), day.atTime(12, 0), SettlementPaymentMethod.CASH, null, "incoming", true
        ));

        TradingDtos.TransactionExportReport report = tradingService.transactionExportReport(day, day, null, null, null, "occurredAt", "asc");
        assertEquals(1, report.partySections().size());
        TradingDtos.TransactionPartyExportSection section = report.partySections().getFirst();
        assertEquals("Hasan", section.party().partyName());
        assertEquals("01711", section.party().phone());
        assertEquals("Dhaka", section.party().address());
        assertEquals(2, section.deals().size());
        assertEquals(1, section.settlements().size());
        assertEquals(0, section.dealSummary().totalBuyBdt().compareTo(new BigDecimal("240.00")));
        assertEquals(0, section.dealSummary().totalSellBdt().compareTo(new BigDecimal("130.00")));
        assertEquals(0, section.settlementSummary().totalIncomingBdt().compareTo(new BigDecimal("50.00")));
    }

    @Test
    void transactionExportReportComputesGrandTotalsAcrossParties() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("owner", null));
        Party p1 = partyRepository.save(Party.builder().name("P1").build());
        Party p2 = partyRepository.save(Party.builder().name("P2").build());
        LocalDate day = LocalDate.now();

        tradingService.createDeal(new TradingDtos.DealCreateRequest(
                DealType.SELL, p1.getId(), InstrumentCode.USD, new BigDecimal("1"), new BigDecimal("100"),
                day.atTime(9, 0), "sell"
        ));
        tradingService.createDeal(new TradingDtos.DealCreateRequest(
                DealType.BUY, p2.getId(), InstrumentCode.USD, new BigDecimal("1"), new BigDecimal("80"),
                day.atTime(10, 0), "buy"
        ));
        tradingService.createSettlement(new TradingDtos.SettlementCreateRequest(
                p1.getId(), null, new BigDecimal("40"), day.atTime(11, 0), SettlementPaymentMethod.BANK, "ref", "incoming", false
        ));
        tradingService.createSettlement(new TradingDtos.SettlementCreateRequest(
                p2.getId(), null, new BigDecimal("20"), day.atTime(12, 0), SettlementPaymentMethod.CASH, null, "outgoing", false
        ));

        TradingDtos.TransactionExportReport report = tradingService.transactionExportReport(day, day, null, null, null, "occurredAt", "asc");
        assertEquals(2, report.partySections().size());
        assertEquals(0, report.grandDealSummary().totalBuyBdt().compareTo(new BigDecimal("80.00")));
        assertEquals(0, report.grandDealSummary().totalSellBdt().compareTo(new BigDecimal("100.00")));
        assertEquals(0, report.grandSettlementSummary().totalIncomingBdt().compareTo(new BigDecimal("40.00")));
        assertEquals(0, report.grandSettlementSummary().totalOutgoingBdt().compareTo(new BigDecimal("20.00")));
    }
}
