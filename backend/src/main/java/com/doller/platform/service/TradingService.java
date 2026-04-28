package com.doller.platform.service;

import com.doller.platform.common.ApiException;
import com.doller.platform.domain.*;
import com.doller.platform.domain.enums.DealType;
import com.doller.platform.dto.TradingDtos;
import com.doller.platform.repo.*;
import jakarta.transaction.Transactional;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class TradingService {
    private final TradeDealRepository dealRepo;
    private final PartyRepository partyRepo;
    private final UserAccountRepository userRepo;
    private final SettlementRepository settlementRepo;
    private final ExpenseRepository expenseRepo;
    private final DailyCloseRepository dailyCloseRepo;
    private final StatementSnapshotRepository snapshotRepo;
    private final LedgerEntryRepository ledgerRepo;
    private final LedgerService ledgerService;
    private final AuditService auditService;

    public TradingService(TradeDealRepository dealRepo, PartyRepository partyRepo, UserAccountRepository userRepo,
                          SettlementRepository settlementRepo, ExpenseRepository expenseRepo,
                          DailyCloseRepository dailyCloseRepo, StatementSnapshotRepository snapshotRepo,
                          LedgerEntryRepository ledgerRepo, LedgerService ledgerService, AuditService auditService) {
        this.dealRepo = dealRepo;
        this.partyRepo = partyRepo;
        this.userRepo = userRepo;
        this.settlementRepo = settlementRepo;
        this.expenseRepo = expenseRepo;
        this.dailyCloseRepo = dailyCloseRepo;
        this.snapshotRepo = snapshotRepo;
        this.ledgerRepo = ledgerRepo;
        this.ledgerService = ledgerService;
        this.auditService = auditService;
    }

    @Transactional
    public TradeDeal createDeal(TradingDtos.DealCreateRequest req) {
        requireOpenDay(req.dealTime().toLocalDate());
        Party party = partyRepo.findById(req.partyId()).orElseThrow(() -> new ApiException("Party not found"));
        UserAccount by = getCurrentUser();
        BigDecimal gross = req.usdAmount().multiply(req.bdtRate());
        TradeDeal deal = dealRepo.save(TradeDeal.builder()
                .dealType(req.dealType())
                .party(party)
                .createdBy(by)
                .usdAmount(req.usdAmount())
                .bdtRate(req.bdtRate())
                .bdtGross(gross)
                .dealTime(req.dealTime())
                .notes(req.notes())
                .lockedByDayClose(false)
                .build());

        if (req.dealType() == DealType.BUY) {
            ledgerService.post(req.dealTime(), "USD_INVENTORY", gross, BigDecimal.ZERO, "DEAL", deal.getId(), "Buy USD");
            ledgerService.post(req.dealTime(), "PAYABLE_" + party.getId(), BigDecimal.ZERO, gross, "DEAL", deal.getId(), "Payable to party");
        } else {
            ledgerService.post(req.dealTime(), "RECEIVABLE_" + party.getId(), gross, BigDecimal.ZERO, "DEAL", deal.getId(), "Receivable from party");
            ledgerService.post(req.dealTime(), "USD_INVENTORY", BigDecimal.ZERO, gross, "DEAL", deal.getId(), "Sell USD");
        }
        auditService.log("CREATE_DEAL", "/deals", "partyId=" + party.getId(), null, null, "deal:" + deal.getId());
        return deal;
    }

    @Transactional
    public Settlement createSettlement(TradingDtos.SettlementCreateRequest req) {
        requireOpenDay(req.settlementTime().toLocalDate());
        Party party = partyRepo.findById(req.partyId()).orElseThrow(() -> new ApiException("Party not found"));
        TradeDeal deal = req.tradeDealId() == null ? null : dealRepo.findById(req.tradeDealId()).orElseThrow(() -> new ApiException("Deal not found"));

        BigDecimal outstanding = partyOutstanding(party.getId());
        BigDecimal applied = req.bdtAmount().min(outstanding.max(BigDecimal.ZERO));
        BigDecimal advance = req.bdtAmount().subtract(applied);

        if (advance.compareTo(BigDecimal.ZERO) > 0 && !req.allowAdvance()) {
            throw new ApiException("Over settlement requires allowAdvance=true");
        }

        Settlement st = settlementRepo.save(Settlement.builder()
                .party(party)
                .tradeDeal(deal)
                .bdtAmount(req.bdtAmount())
                .appliedAmount(applied)
                .advanceAmount(advance.max(BigDecimal.ZERO))
                .settlementTime(req.settlementTime())
                .notes(req.notes())
                .build());

        ledgerService.post(req.settlementTime(), "CASH", req.bdtAmount(), BigDecimal.ZERO, "SETTLEMENT", st.getId(), "Cash received");
        if (applied.compareTo(BigDecimal.ZERO) > 0) {
            ledgerService.post(req.settlementTime(), "RECEIVABLE_" + party.getId(), BigDecimal.ZERO, applied, "SETTLEMENT", st.getId(), "Settlement against due");
        }
        if (advance.compareTo(BigDecimal.ZERO) > 0) {
            ledgerService.post(req.settlementTime(), "ADVANCE_" + party.getId(), BigDecimal.ZERO, advance, "SETTLEMENT", st.getId(), "Advance by party");
        }
        auditService.log("CREATE_SETTLEMENT", "/settlements", "partyId=" + party.getId(), null, null, "settlement:" + st.getId());
        return st;
    }

    @Transactional
    public Expense createExpense(TradingDtos.ExpenseCreateRequest req) {
        requireOpenDay(req.expenseTime().toLocalDate());
        TradeDeal deal = req.tradeDealId() == null ? null : dealRepo.findById(req.tradeDealId()).orElseThrow(() -> new ApiException("Deal not found"));
        Expense ex = expenseRepo.save(Expense.builder()
                .expenseType(req.expenseType())
                .tradeDeal(deal)
                .amountBdt(req.amountBdt())
                .expenseTime(req.expenseTime())
                .category(req.category())
                .notes(req.notes())
                .build());
        ledgerService.post(req.expenseTime(), "EXPENSE", req.amountBdt(), BigDecimal.ZERO, "EXPENSE", ex.getId(), ex.getCategory());
        ledgerService.post(req.expenseTime(), "CASH", BigDecimal.ZERO, req.amountBdt(), "EXPENSE", ex.getId(), ex.getCategory());
        auditService.log("CREATE_EXPENSE", "/expenses", "category=" + req.category(), null, null, "expense:" + ex.getId());
        return ex;
    }

    public TradingDtos.DayClosePreview previewDayClose(LocalDate date) {
        var range = dayRange(date);
        BigDecimal buy = dealRepo.findByDealTimeBetween(range[0], range[1]).stream()
                .filter(d -> d.getDealType() == DealType.BUY)
                .map(TradeDeal::getBdtGross).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal sell = dealRepo.findByDealTimeBetween(range[0], range[1]).stream()
                .filter(d -> d.getDealType() == DealType.SELL)
                .map(TradeDeal::getBdtGross).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal cost = expenseRepo.findByExpenseTimeBetween(range[0], range[1]).stream()
                .map(Expense::getAmountBdt).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal pnl = sell.subtract(buy).subtract(cost);
        return new TradingDtos.DayClosePreview(date, buy, sell, cost, pnl);
    }

    @Transactional
    public TradingDtos.DayCloseResponse confirmDayClose(LocalDate date) {
        if (dailyCloseRepo.findByBusinessDate(date).isPresent()) throw new ApiException("Day already closed");
        TradingDtos.DayClosePreview p = previewDayClose(date);
        StatementSnapshot prev = snapshotRepo.findByBusinessDate(date.minusDays(1)).orElse(null);
        BigDecimal openingCash = prev == null ? BigDecimal.ZERO : prev.getClosingCashBdt();
        BigDecimal openingUsd = prev == null ? BigDecimal.ZERO : prev.getClosingUsd();

        var range = dayRange(date);
        BigDecimal cashNet = ledgerRepo.netForAccount("CASH", range[0], range[1]);
        BigDecimal usdNet = ledgerRepo.netForAccount("USD_INVENTORY", range[0], range[1]);
        BigDecimal closingCash = openingCash.add(cashNet);
        BigDecimal closingUsd = openingUsd.add(usdNet);

        StatementSnapshot snap = snapshotRepo.save(StatementSnapshot.builder()
                .businessDate(date)
                .openingCashBdt(openingCash)
                .closingCashBdt(closingCash)
                .openingUsd(openingUsd)
                .closingUsd(closingUsd)
                .realizedProfitLossBdt(p.realizedProfitLossBdt())
                .build());

        dealRepo.findByDealTimeBetween(range[0], range[1]).forEach(d -> { d.setLockedByDayClose(true); dealRepo.save(d); });
        dailyCloseRepo.save(DailyClose.builder()
                .businessDate(date)
                .closedBy(getCurrentUser())
                .closedAt(LocalDateTime.now())
                .reopened(false)
                .build());
        String auditRef = auditService.log("DAY_CLOSE", "/day-close/" + date, null, null, null, "snapshot:" + snap.getId());
        return new TradingDtos.DayCloseResponse(date, true, auditRef, openingCash, closingCash, openingUsd, closingUsd, p.realizedProfitLossBdt());
    }

    @Transactional
    public TradingDtos.DayCloseResponse reopenDay(LocalDate date, String reason) {
        DailyClose close = dailyCloseRepo.findByBusinessDate(date).orElseThrow(() -> new ApiException("Day not closed"));
        close.setReopened(true);
        close.setReopenReason(reason);
        snapshotRepo.findByBusinessDate(date).ifPresent(snapshotRepo::delete);
        var range = dayRange(date);
        dealRepo.findByDealTimeBetween(range[0], range[1]).forEach(d -> { d.setLockedByDayClose(false); dealRepo.save(d); });
        dailyCloseRepo.save(close);
        String auditRef = auditService.log("DAY_REOPEN", "/day-close/" + date + "/reopen", null, reason, "closed", "reopened");
        return new TradingDtos.DayCloseResponse(date, false, auditRef, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    public TradingDtos.DashboardResponse dashboard(LocalDate from, LocalDate to) {
        var range = new LocalDateTime[]{from.atStartOfDay(), to.plusDays(1).atStartOfDay().minusNanos(1)};
        BigDecimal receivable = dealRepo.findByDealTimeBetween(range[0], range[1]).stream()
                .filter(d -> d.getDealType() == DealType.SELL)
                .map(TradeDeal::getBdtGross).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal payable = dealRepo.findByDealTimeBetween(range[0], range[1]).stream()
                .filter(d -> d.getDealType() == DealType.BUY)
                .map(TradeDeal::getBdtGross).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal usd = dealRepo.findByDealTimeBetween(range[0], range[1]).stream()
                .map(d -> d.getDealType() == DealType.BUY ? d.getUsdAmount() : d.getUsdAmount().negate())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal periodPnl = statementRange(from, to).stream().map(TradingDtos.StatementLine::pnl).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal todayPnl = snapshotRepo.findByBusinessDate(LocalDate.now()).map(StatementSnapshot::getRealizedProfitLossBdt).orElse(BigDecimal.ZERO);
        return new TradingDtos.DashboardResponse(receivable, payable, usd, todayPnl, periodPnl);
    }

    public List<TradingDtos.StatementLine> statementRange(LocalDate from, LocalDate to) {
        return snapshotRepo.findByBusinessDateBetweenOrderByBusinessDateAsc(from, to).stream()
                .map(s -> new TradingDtos.StatementLine(
                        s.getBusinessDate(), s.getOpeningCashBdt(), s.getClosingCashBdt(),
                        s.getOpeningUsd(), s.getClosingUsd(), s.getRealizedProfitLossBdt()
                )).toList();
    }

    public TradingDtos.PartyLedgerResponse partyLedger(Long partyId) {
        Party p = partyRepo.findById(partyId).orElseThrow(() -> new ApiException("Party not found"));
        List<TradingDtos.PartyLedgerLine> lines = new ArrayList<>();

        var deals = dealRepo.findAll().stream().filter(d -> d.getParty().getId().equals(partyId)).toList();
        for (TradeDeal d : deals) {
            BigDecimal signed = d.getDealType() == DealType.SELL ? d.getBdtGross() : d.getBdtGross().negate();
            lines.add(new TradingDtos.PartyLedgerLine("DEAL-" + d.getDealType(), d.getDealTime(), signed, d.getNotes()));
        }
        for (Settlement s : settlementRepo.findByPartyOrderBySettlementTimeAsc(p)) {
            lines.add(new TradingDtos.PartyLedgerLine("SETTLEMENT", s.getSettlementTime(), s.getBdtAmount().negate(), s.getNotes()));
        }
        lines.sort(Comparator.comparing(TradingDtos.PartyLedgerLine::time));
        BigDecimal running = partyOutstanding(partyId);
        BigDecimal aging = computeAgingDue(partyId, 7);
        return new TradingDtos.PartyLedgerResponse(p.getId(), p.getName(), running, aging, lines);
    }

    public BigDecimal partyOutstanding(Long partyId) {
        Party p = partyRepo.findById(partyId).orElseThrow(() -> new ApiException("Party not found"));
        BigDecimal deals = dealRepo.findAll().stream().filter(d -> d.getParty().getId().equals(partyId) && d.getDealType() == DealType.SELL)
                .map(TradeDeal::getBdtGross).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal settlements = settlementRepo.findByPartyOrderBySettlementTimeAsc(p).stream()
                .map(Settlement::getAppliedAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return deals.subtract(settlements);
    }

    public BigDecimal computeAgingDue(Long partyId, int olderThanDays) {
        Party p = partyRepo.findById(partyId).orElseThrow(() -> new ApiException("Party not found"));
        LocalDateTime cutoff = LocalDate.now().minusDays(olderThanDays).atStartOfDay();
        BigDecimal oldDeals = dealRepo.findAll().stream()
                .filter(d -> d.getParty().getId().equals(partyId) && d.getDealType() == DealType.SELL && d.getDealTime().isBefore(cutoff))
                .map(TradeDeal::getBdtGross).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal oldSettle = settlementRepo.findByPartyOrderBySettlementTimeAsc(p).stream()
                .filter(s -> s.getSettlementTime().isBefore(cutoff))
                .map(Settlement::getAppliedAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return oldDeals.subtract(oldSettle).max(BigDecimal.ZERO);
    }

    private LocalDateTime[] dayRange(LocalDate date) {
        return new LocalDateTime[]{date.atStartOfDay(), date.plusDays(1).atStartOfDay().minusNanos(1)};
    }

    private UserAccount getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepo.findByUsernameAndActiveTrue(username).orElseThrow(() -> new ApiException("User not found"));
    }

    private void requireOpenDay(LocalDate date) {
        dailyCloseRepo.findByBusinessDate(date).ifPresent(c -> {
            if (!c.isReopened()) throw new ApiException("Date is closed. Reopen required.");
        });
    }
}
