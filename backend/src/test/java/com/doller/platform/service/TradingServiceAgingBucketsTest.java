package com.doller.platform.service;

import com.doller.platform.domain.Party;
import com.doller.platform.domain.Settlement;
import com.doller.platform.domain.TradeDeal;
import com.doller.platform.domain.UserAccount;
import com.doller.platform.domain.enums.DealType;
import com.doller.platform.domain.enums.InstrumentCode;
import com.doller.platform.domain.enums.Role;
import com.doller.platform.domain.enums.SettlementBasis;
import com.doller.platform.domain.enums.SettlementDirection;
import com.doller.platform.domain.enums.SettlementPaymentMethod;
import com.doller.platform.dto.TradingDtos;
import com.doller.platform.repo.DailyCloseRepository;
import com.doller.platform.repo.PartyRepository;
import com.doller.platform.repo.SettlementRepository;
import com.doller.platform.repo.StatementSnapshotRepository;
import com.doller.platform.repo.TradeDealRepository;
import com.doller.platform.repo.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class TradingServiceAgingBucketsTest {

    @Autowired
    private TradingService tradingService;
    @Autowired
    private PartyRepository partyRepository;
    @Autowired
    private TradeDealRepository tradeDealRepository;
    @Autowired
    private SettlementRepository settlementRepository;
    @Autowired
    private UserAccountRepository userAccountRepository;
    @Autowired
    private DailyCloseRepository dailyCloseRepository;
    @Autowired
    private StatementSnapshotRepository statementSnapshotRepository;

    private UserAccount actor;

    @BeforeEach
    void setUp() {
        dailyCloseRepository.deleteAll();
        statementSnapshotRepository.deleteAll();
        settlementRepository.deleteAll();
        tradeDealRepository.deleteAll();
        partyRepository.deleteAll();
        userAccountRepository.deleteAll();
        actor = userAccountRepository.save(UserAccount.builder()
                .username("tester")
                .passwordHash("hash")
                .role(Role.OWNER)
                .active(true)
                .mustChangePassword(false)
                .build());
    }

    @Test
    void agingDueTotalsOpenReceivable() {
        Party party = partyRepository.save(Party.builder().name("Aging Party").build());
        saveSellDeal(party, 1000, 2);
        saveSellDeal(party, 2000, 6);
        saveSellDeal(party, 3000, 10);

        TradingDtos.PartyLedgerResponse ledger = tradingService.partyLedger(party.getId());
        assertThat(ledger.balances().agingDueBdt()).isEqualByComparingTo("6000.00");
    }

    @Test
    void incomingSettlementReducesAgingDue() {
        Party party = partyRepository.save(Party.builder().name("Settlement Party").build());
        saveSellDeal(party, 1200, 12);
        saveSellDeal(party, 800, 2);
        saveIncomingReceivableSettlement(party, 700, 1);

        TradingDtos.PartyLedgerResponse ledger = tradingService.partyLedger(party.getId());
        TradingDtos.SettlementInferenceResponse inference = tradingService.settlementInference(
                party.getId(),
                null,
                BigDecimal.valueOf(400)
        );

        assertThat(ledger.balances().agingDueBdt()).isEqualByComparingTo("1300.00");
        assertThat(inference.current().agingDueBdt()).isEqualByComparingTo("1300.00");
        assertThat(inference.projected().agingDueBdt()).isEqualByComparingTo("1300.00");
    }

    private void saveSellDeal(Party party, double amount, int daysAgo) {
        tradeDealRepository.save(TradeDeal.builder()
                .dealType(DealType.SELL)
                .party(party)
                .createdBy(actor)
                .instrumentCode(InstrumentCode.USD)
                .quantity(BigDecimal.ONE)
                .bdtRate(BigDecimal.valueOf(amount))
                .bdtGross(BigDecimal.valueOf(amount).setScale(2))
                .dealTime(LocalDateTime.now().minusDays(daysAgo))
                .lockedByDayClose(false)
                .build());
    }

    private void saveIncomingReceivableSettlement(Party party, double amount, int daysAgo) {
        settlementRepository.save(Settlement.builder()
                .party(party)
                .direction(SettlementDirection.INCOMING)
                .basis(SettlementBasis.RECEIVABLE)
                .bdtAmount(BigDecimal.valueOf(amount).setScale(2))
                .appliedAmount(BigDecimal.valueOf(amount).setScale(2))
                .advanceAmount(BigDecimal.ZERO.setScale(2))
                .paymentMethod(SettlementPaymentMethod.CASH)
                .settlementTime(LocalDateTime.now().minusDays(daysAgo))
                .build());
    }
}
