package com.doller.platform.service;

import com.doller.platform.common.ApiException;
import com.doller.platform.domain.*;
import com.doller.platform.domain.enums.DealType;
import com.doller.platform.domain.enums.ExpenseType;
import com.doller.platform.domain.enums.SettlementBasis;
import com.doller.platform.domain.enums.SettlementDirection;
import com.doller.platform.domain.enums.SettlementPaymentMethod;
import com.doller.platform.dto.TradingDtos;
import com.doller.platform.repo.*;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Stream;

@Service
public class TradingService {
    private static final Logger log = LoggerFactory.getLogger(TradingService.class);

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
    private final boolean tradingDebug;
    private final ZoneId businessZone;

    public TradingService(TradeDealRepository dealRepo, PartyRepository partyRepo, UserAccountRepository userRepo,
                          SettlementRepository settlementRepo, ExpenseRepository expenseRepo,
                          DailyCloseRepository dailyCloseRepo, StatementSnapshotRepository snapshotRepo,
                          LedgerEntryRepository ledgerRepo, LedgerService ledgerService, AuditService auditService,
                          @Value("${app.logging.trading-debug:false}") boolean tradingDebug,
                          @Value("${app.timezone:Asia/Dhaka}") String appTimeZone) {
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
        this.tradingDebug = tradingDebug;
        this.businessZone = ZoneId.of(appTimeZone);
    }

    @Transactional
    public TradeDeal createDeal(TradingDtos.DealCreateRequest req) {
        Party party = partyRepo.findByIdAndDeletedFalse(req.partyId()).orElseThrow(() -> new ApiException("Party not found"));
        UserAccount by = getCurrentUser();
        BigDecimal gross = req.quantity().multiply(req.bdtRate());
        TradeDeal deal = dealRepo.save(TradeDeal.builder()
                .dealType(req.dealType())
                .party(party)
                .createdBy(by)
                .instrumentCode(req.instrumentCode())
                .quantity(req.quantity())
                .bdtRate(req.bdtRate())
                .bdtGross(gross)
                .dealTime(req.dealTime())
                .notes(req.notes())
                .lockedByDayClose(false)
                .deleted(false)
                .build());

        postDealLedger(deal, req.dealTime());
        if (tradingDebug) {
            log.info("deal_created dealId={} partyId={} type={} instrument={} quantity={} rate={} gross={} dealTime={} notes={}",
                    deal.getId(),
                    party.getId(),
                    req.dealType(),
                    req.instrumentCode(),
                    req.quantity(),
                    req.bdtRate(),
                    gross,
                    req.dealTime(),
                    maskFreeText(req.notes()));
        }
        auditService.log("CREATE_DEAL", "/deals", "partyId=" + party.getId(), null, null, "deal:" + deal.getId());
        return deal;
    }

    public List<TradingDtos.DealSummary> listDeals(Long partyId) {
        return dealRepo.findByDeletedFalse().stream()
                .filter(d -> partyId == null || d.getParty().getId().equals(partyId))
                .sorted(Comparator.comparing(TradeDeal::getDealTime).reversed())
                .map(d -> new TradingDtos.DealSummary(
                        d.getId(),
                        d.getParty().getName(),
                        d.getDealType(),
                        d.getInstrumentCode(),
                        d.getQuantity(),
                        d.getBdtGross(),
                        d.getDealTime(),
                        d.isLockedByDayClose()
                ))
                .toList();
    }

    @Transactional
    public Settlement createSettlement(TradingDtos.SettlementCreateRequest req) {
        Party party = partyRepo.findByIdAndDeletedFalse(req.partyId()).orElseThrow(() -> new ApiException("Party not found"));
        TradeDeal deal = resolveDeal(req.tradeDealId(), party.getId());
        SettlementPlan plan = inferSettlementPlan(party, deal, req.bdtAmount());

        if (plan.advanceAmount().compareTo(BigDecimal.ZERO) > 0 && !req.allowAdvance()) {
            throw new ApiException("Over settlement requires allowAdvance=true");
        }

        Settlement st = settlementRepo.save(Settlement.builder()
                .party(party)
                .tradeDeal(deal)
                .direction(plan.direction())
                .basis(plan.basis())
                .bdtAmount(req.bdtAmount())
                .appliedAmount(plan.appliedAmount())
                .advanceAmount(plan.advanceAmount())
                .paymentMethod(req.paymentMethod())
                .paymentReference(req.paymentReference())
                .settlementTime(req.settlementTime())
                .notes(req.notes())
                .deleted(false)
                .build());

        postSettlementLedger(st, req.settlementTime());
        auditService.log("CREATE_SETTLEMENT", "/settlements", "partyId=" + party.getId(), null, null, "settlement:" + st.getId());
        return st;
    }

    public TradingDtos.SettlementInferenceResponse settlementInference(Long partyId, Long tradeDealId, BigDecimal amount) {
        Party party = partyRepo.findByIdAndDeletedFalse(partyId).orElseThrow(() -> new ApiException("Party not found"));
        TradeDeal deal = resolveDeal(tradeDealId, partyId);
        return toInferenceResponse(party, deal, amount == null ? BigDecimal.ZERO : amount);
    }

    @Transactional
    public Expense createExpense(TradingDtos.ExpenseCreateRequest req) {
        if (req.expenseType() == ExpenseType.TRANSACTION || req.expenseType() == ExpenseType.DAILY_OVERHEAD) {
            throw new ApiException("Unsupported expenseType for new entries");
        }
        Expense ex = expenseRepo.save(Expense.builder()
                .expenseType(req.expenseType())
                .tradeDeal(null)
                .amountBdt(req.amountBdt())
                .expenseTime(req.expenseTime())
                .category(req.category())
                .notes(req.notes())
                .deleted(false)
                .build());
        postExpenseLedger(ex, req.expenseTime());
        auditService.log("CREATE_EXPENSE", "/expenses", "category=" + req.category(), null, null, "expense:" + ex.getId());
        return ex;
    }

    @Transactional
    public TradeDeal updateDeal(Long id, TradingDtos.DealUpdateRequest req) {
        TradeDeal deal = dealRepo.findByIdAndDeletedFalse(id).orElseThrow(() -> new ApiException("Deal not found"));
        Party party = partyRepo.findByIdAndDeletedFalse(req.partyId()).orElseThrow(() -> new ApiException("Party not found"));
        String before = serializeDeal(deal);
        reverseDealLedger(deal, businessNow());
        BigDecimal gross = req.quantity().multiply(req.bdtRate());
        deal.setDealType(req.dealType());
        deal.setParty(party);
        deal.setInstrumentCode(req.instrumentCode());
        deal.setQuantity(req.quantity());
        deal.setBdtRate(req.bdtRate());
        deal.setBdtGross(gross);
        deal.setDealTime(req.dealTime());
        deal.setNotes(req.notes());
        TradeDeal saved = dealRepo.save(deal);
        postDealLedger(saved, req.dealTime());
        auditService.log("UPDATE_DEAL", "/deals/" + id, "partyId=" + party.getId(), null, before, serializeDeal(saved));
        return saved;
    }

    @Transactional
    public void deleteDeal(Long id) {
        TradeDeal deal = dealRepo.findByIdAndDeletedFalse(id).orElseThrow(() -> new ApiException("Deal not found"));
        String before = serializeDeal(deal);
        reverseDealLedger(deal, businessNow());
        deal.setDeleted(true);
        deal.setDeletedAt(businessNow());
        deal.setDeletedBy(currentActor());
        dealRepo.save(deal);
        auditService.log("DELETE_DEAL", "/deals/" + id, null, null, before, null);
    }

    @Transactional
    public Settlement updateSettlement(Long id, TradingDtos.SettlementUpdateRequest req) {
        Settlement settlement = settlementRepo.findByIdAndDeletedFalse(id).orElseThrow(() -> new ApiException("Settlement not found"));
        Party party = partyRepo.findByIdAndDeletedFalse(req.partyId()).orElseThrow(() -> new ApiException("Party not found"));
        TradeDeal deal = resolveDeal(req.tradeDealId(), party.getId());
        SettlementPlan plan = inferSettlementPlan(party, deal, req.bdtAmount());
        if (plan.advanceAmount().compareTo(BigDecimal.ZERO) > 0 && !req.allowAdvance()) {
            throw new ApiException("Over settlement requires allowAdvance=true");
        }
        String before = serializeSettlement(settlement);
        reverseSettlementLedger(settlement, businessNow());
        settlement.setParty(party);
        settlement.setTradeDeal(deal);
        settlement.setDirection(plan.direction());
        settlement.setBasis(plan.basis());
        settlement.setBdtAmount(req.bdtAmount());
        settlement.setAppliedAmount(plan.appliedAmount());
        settlement.setAdvanceAmount(plan.advanceAmount());
        settlement.setPaymentMethod(req.paymentMethod());
        settlement.setPaymentReference(req.paymentReference());
        settlement.setSettlementTime(req.settlementTime());
        settlement.setNotes(req.notes());
        Settlement saved = settlementRepo.save(settlement);
        postSettlementLedger(saved, req.settlementTime());
        auditService.log("UPDATE_SETTLEMENT", "/settlements/" + id, "partyId=" + party.getId(), null, before, serializeSettlement(saved));
        return saved;
    }

    @Transactional
    public void deleteSettlement(Long id) {
        Settlement settlement = settlementRepo.findByIdAndDeletedFalse(id).orElseThrow(() -> new ApiException("Settlement not found"));
        String before = serializeSettlement(settlement);
        reverseSettlementLedger(settlement, businessNow());
        settlement.setDeleted(true);
        settlement.setDeletedAt(businessNow());
        settlement.setDeletedBy(currentActor());
        settlementRepo.save(settlement);
        auditService.log("DELETE_SETTLEMENT", "/settlements/" + id, null, null, before, null);
    }

    @Transactional
    public Expense updateExpense(Long id, TradingDtos.ExpenseUpdateRequest req) {
        Expense expense = expenseRepo.findByIdAndDeletedFalse(id).orElseThrow(() -> new ApiException("Expense not found"));
        if (req.expenseType() == ExpenseType.TRANSACTION || req.expenseType() == ExpenseType.DAILY_OVERHEAD) {
            throw new ApiException("Unsupported expenseType for new entries");
        }
        String before = serializeExpense(expense);
        reverseExpenseLedger(expense, businessNow());
        expense.setExpenseType(req.expenseType());
        expense.setAmountBdt(req.amountBdt());
        expense.setExpenseTime(req.expenseTime());
        expense.setCategory(req.category());
        expense.setNotes(req.notes());
        Expense saved = expenseRepo.save(expense);
        postExpenseLedger(saved, req.expenseTime());
        auditService.log("UPDATE_EXPENSE", "/expenses/" + id, "category=" + req.category(), null, before, serializeExpense(saved));
        return saved;
    }

    @Transactional
    public void deleteExpense(Long id) {
        Expense expense = expenseRepo.findByIdAndDeletedFalse(id).orElseThrow(() -> new ApiException("Expense not found"));
        String before = serializeExpense(expense);
        reverseExpenseLedger(expense, businessNow());
        expense.setDeleted(true);
        expense.setDeletedAt(businessNow());
        expense.setDeletedBy(currentActor());
        expenseRepo.save(expense);
        auditService.log("DELETE_EXPENSE", "/expenses/" + id, null, null, before, null);
    }

    public TradingDtos.DayClosePreview previewDayClose(LocalDate date) {
        var range = dayRange(date);
        PnlMetrics metrics = pnlMetrics(date, date);
        BigDecimal cost = expenseRepo.findByExpenseTimeBetweenAndDeletedFalse(range[0], range[1]).stream()
                .map(Expense::getAmountBdt).reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean closed = dailyCloseRepo.findByBusinessDate(date).filter(c -> !c.isReopened()).isPresent();
        return new TradingDtos.DayClosePreview(
                date,
                metrics.buyBdt(),
                metrics.sellBdt(),
                cost,
                metrics.grossPnlBdt(),
                closed
        );
    }

    @Transactional
    public TradingDtos.DayCloseResponse confirmDayClose(LocalDate date) {
        DailyClose existingClose = dailyCloseRepo.findByBusinessDate(date).orElse(null);
        TradingDtos.DayClosePreview p = previewDayClose(date);
        StatementSnapshot prev = snapshotRepo.findByBusinessDate(date.minusDays(1)).orElse(null);
        BigDecimal openingCash = prev == null ? BigDecimal.ZERO : prev.getClosingCashBdt();
        BigDecimal openingUsd = prev == null ? BigDecimal.ZERO : prev.getClosingUsd();
        BalancePosition openingPosition = prev == null ? zeroBalancePosition() : closingPositionFromSnapshot(prev);

        var range = dayRange(date);
        BigDecimal cashNet = ledgerRepo.netForAccount("CASH", range[0], range[1]);
        BigDecimal usdNet = ledgerRepo.netForAccountPrefix("FX_INVENTORY_", range[0], range[1]);
        BigDecimal closingCash = openingCash.add(cashNet);
        BigDecimal closingUsd = openingUsd.add(usdNet);
        BalancePosition closingPosition = aggregateBusinessPositionAt(date);

        StatementSnapshot snap = snapshotRepo.save(StatementSnapshot.builder()
                .businessDate(date)
                .openingCashBdt(openingCash)
                .closingCashBdt(closingCash)
                .openingUsd(openingUsd)
                .closingUsd(closingUsd)
                .openingReceivableBdt(openingPosition.receivableBdt())
                .closingReceivableBdt(closingPosition.receivableBdt())
                .openingPayableBdt(openingPosition.payableBdt())
                .closingPayableBdt(closingPosition.payableBdt())
                .openingAdvanceFromPartyBdt(openingPosition.advanceFromPartyBdt())
                .closingAdvanceFromPartyBdt(closingPosition.advanceFromPartyBdt())
                .openingAdvanceToPartyBdt(openingPosition.advanceToPartyBdt())
                .closingAdvanceToPartyBdt(closingPosition.advanceToPartyBdt())
                .openingAgingBdt(openingPosition.agingDueBdt())
                .closingAgingBdt(closingPosition.agingDueBdt())
                .realizedProfitLossBdt(p.realizedProfitLossBdt())
                .build());

        DailyClose closeRecord = existingClose == null
                ? DailyClose.builder().businessDate(date).build()
                : existingClose;
        closeRecord.setClosedBy(getCurrentUser());
        closeRecord.setClosedAt(businessNow());
        closeRecord.setReopened(false);
        closeRecord.setReopenReason(null);
        dailyCloseRepo.save(closeRecord);
        String auditRef = auditService.log("DAY_CLOSE", "/day-close/" + date, null, null, null, "snapshot:" + snap.getId());
        return new TradingDtos.DayCloseResponse(date, true, auditRef, openingCash, closingCash, openingUsd, closingUsd, p.realizedProfitLossBdt());
    }

    @Transactional
    public TradingDtos.DayCloseResponse reopenDay(LocalDate date, String reason) {
        DailyClose close = dailyCloseRepo.findByBusinessDate(date).orElseThrow(() -> new ApiException("Day not closed"));
        close.setReopened(true);
        close.setReopenReason(reason);
        snapshotRepo.findByBusinessDate(date).ifPresent(snapshotRepo::delete);
        dailyCloseRepo.save(close);
        String auditRef = auditService.log("DAY_REOPEN", "/day-close/" + date + "/reopen", null, reason, "closed", "reopened");
        return new TradingDtos.DayCloseResponse(date, false, auditRef, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    public TradingDtos.DashboardResponse dashboard(LocalDate from, LocalDate to) {
        BalancePosition balances = projectBusinessBalance(null);
        var range = new LocalDateTime[]{from.atStartOfDay(), to.plusDays(1).atStartOfDay().minusNanos(1)};
        Map<String, BigDecimal> positionByInstrument = new HashMap<>();
        for (TradeDeal deal : dealRepo.findByDeletedFalse()) {
            String code = deal.getInstrumentCode().name();
            BigDecimal signedQty = deal.getDealType() == DealType.BUY ? deal.getQuantity() : deal.getQuantity().negate();
            positionByInstrument.merge(code, signedQty, BigDecimal::add);
        }
        List<TradingDtos.InstrumentPosition> positions = positionByInstrument.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    BigDecimal latestRate = latestRateForInstrument(entry.getKey());
                    BigDecimal valuation = entry.getValue().multiply(latestRate);
                    return new TradingDtos.InstrumentPosition(entry.getKey(), entry.getValue(), valuation);
                })
                .toList();
        BigDecimal totalPositionValuation = positions.stream()
                .map(TradingDtos.InstrumentPosition::valuationBdt)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        LocalDate today = businessToday();
        PnlMetrics todayMetrics = pnlMetrics(today, today);
        PnlMetrics periodMetrics = pnlMetrics(from, to);

        TradingDtos.DashboardResponse response = new TradingDtos.DashboardResponse(
                bdt(balances.receivableBdt()),
                bdt(balances.payableBdt()),
                totalPositionValuation,
                todayMetrics.netPnlBdt(),
                periodMetrics.netPnlBdt(),
                todayMetrics.buyBdt(),
                todayMetrics.sellBdt(),
                todayMetrics.grossPnlBdt(),
                todayMetrics.expenseBdt(),
                todayMetrics.netPnlBdt(),
                periodMetrics.buyBdt(),
                periodMetrics.sellBdt(),
                periodMetrics.grossPnlBdt(),
                periodMetrics.expenseBdt(),
                periodMetrics.netPnlBdt(),
                positions
        );
        if (tradingDebug) {
            log.info("dashboard_response from={} to={} receivable={} payable={} totalPosition={} positions={}",
                    from, to, response.receivableBdt(), response.payableBdt(),
                    response.totalPositionValuationBdt(), response.positions().size());
        }
        return response;
    }

    public TradingDtos.DashboardPnlExplainResponse dashboardPnlExplain(
            String mode,
            LocalDate date,
            Integer month,
            Integer year,
            LocalDate from,
            LocalDate to
    ) {
        LocalDate[] resolved = resolveReportRange(mode, date, month, year, from, to);
        LocalDate safeFrom = resolved[0];
        LocalDate safeTo = resolved[1];
        LocalDate today = businessToday();
        return new TradingDtos.DashboardPnlExplainResponse(
                normalizeMode(mode),
                safeFrom,
                safeTo,
                buildExplainSection("Today", today, today),
                buildExplainSection("Period", safeFrom, safeTo)
        );
    }

    public TradingDtos.DuesSnapshotResponse duesSnapshot() {
        BalancePosition totals = projectBusinessBalance(null);
        List<TradingDtos.PartyDueRow> rows = new ArrayList<>();

        for (Party party : partyRepo.findByDeletedFalse()) {
            BalancePosition balances = projectPartyBalance(party, null);
            rows.add(new TradingDtos.PartyDueRow(
                    party.getId(),
                    party.getName(),
                    party.getPhone(),
                    party.getNotes(),
                    bdt(balances.receivableBdt()),
                    bdt(balances.payableBdt()),
                    bdt(balances.netBalanceBdt()),
                    latestActivityAtForParty(party.getId())
            ));
        }

        BigDecimal totalReceivable = bdt(totals.receivableBdt());
        BigDecimal totalPayable = bdt(totals.payableBdt());
        BigDecimal gross = bdt(totalReceivable.add(totalPayable));
        BigDecimal net = bdt(totalReceivable.subtract(totalPayable));
        TradingDtos.DuesSnapshotResponse response =
                new TradingDtos.DuesSnapshotResponse(totalReceivable, totalPayable, gross, net, rows);
        if (tradingDebug) {
            log.info("dues_snapshot_response rows={} totalReceivable={} totalPayable={} gross={} net={}",
                    rows.size(), totalReceivable, totalPayable, gross, net);
        }
        return response;
    }

    public List<TradingDtos.StatementLine> statementRange(LocalDate from, LocalDate to) {
        return snapshotRepo.findByBusinessDateBetweenOrderByBusinessDateAsc(from, to).stream()
                .map(this::toStatementLine)
                .toList();
    }

    public TradingDtos.BalanceSheetResponse balanceSheetReport(String mode, LocalDate date, Integer month, Integer year, LocalDate from, LocalDate to) {
        LocalDate[] range = resolveReportRange(mode, date, month, year, from, to);
        List<TradingDtos.StatementLine> lines = liveStatementRange(range[0], range[1]);
        TradingDtos.StatementLine first = lines.getFirst();
        TradingDtos.StatementLine last = lines.getLast();
        BigDecimal openingCash = bdt(first.openingCash());
        BigDecimal closingCash = bdt(last.closingCash());
        BigDecimal openingUsd = first.openingUsd();
        BigDecimal closingUsd = last.closingUsd();
        BigDecimal openingReceivable = bdt(first.openingReceivableBdt());
        BigDecimal closingReceivable = bdt(last.closingReceivableBdt());
        BigDecimal openingPayable = bdt(first.openingPayableBdt());
        BigDecimal closingPayable = bdt(last.closingPayableBdt());
        BigDecimal openingAdvanceFromParty = bdt(first.openingAdvanceFromPartyBdt());
        BigDecimal closingAdvanceFromParty = bdt(last.closingAdvanceFromPartyBdt());
        BigDecimal openingAdvanceToParty = bdt(first.openingAdvanceToPartyBdt());
        BigDecimal closingAdvanceToParty = bdt(last.closingAdvanceToPartyBdt());
        BigDecimal openingAging = bdt(first.openingAgingBdt());
        BigDecimal closingAging = bdt(last.closingAgingBdt());
        BigDecimal totalPnl = bdt(pnlMetrics(range[0], range[1]).netPnlBdt());
        Map<SettlementPaymentMethod, BigDecimal> paymentNet = settlementPaymentNet(range[0], range[1]);
        Map<SettlementPaymentMethod, BigDecimal> paymentClosing = settlementPaymentClosing(range[1]);
        List<TradingDtos.InstrumentBalanceRow> instrumentBalances = instrumentBalances(range[0], range[1]);
        return new TradingDtos.BalanceSheetResponse(
                normalizeMode(mode),
                range[0],
                range[1],
                openingCash,
                closingCash,
                openingUsd,
                closingUsd,
                bdt(paymentNet.getOrDefault(SettlementPaymentMethod.CASH, BigDecimal.ZERO)),
                bdt(paymentNet.getOrDefault(SettlementPaymentMethod.BANK, BigDecimal.ZERO)),
                bdt(paymentNet.getOrDefault(SettlementPaymentMethod.CHECK, BigDecimal.ZERO)),
                bdt(paymentClosing.getOrDefault(SettlementPaymentMethod.CASH, BigDecimal.ZERO)),
                bdt(paymentClosing.getOrDefault(SettlementPaymentMethod.BANK, BigDecimal.ZERO)),
                bdt(paymentClosing.getOrDefault(SettlementPaymentMethod.CHECK, BigDecimal.ZERO)),
                openingReceivable,
                closingReceivable,
                openingPayable,
                closingPayable,
                openingAdvanceFromParty,
                closingAdvanceFromParty,
                openingAdvanceToParty,
                closingAdvanceToParty,
                openingAging,
                closingAging,
                totalPnl,
                instrumentBalances,
                lines
        );
    }

    public TradingDtos.TransactionDetailsResponse transactionDetails(
            LocalDate from,
            LocalDate to,
            String type,
            Long partyId,
            String search,
            String sortField,
            String sortDirection
    ) {
        LocalDate safeFrom = from == null ? businessToday() : from;
        LocalDate safeTo = to == null ? safeFrom : to;
        LocalDateTime[] range = dayRange(safeFrom, safeTo);
        String normalizedType = normalizeFilter(type);
        String normalizedSearch = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        String normalizedSortField = normalizeSortField(sortField);
        String normalizedSortDirection = "asc".equalsIgnoreCase(sortDirection) ? "asc" : "desc";

        List<TradingDtos.TransactionDetailRow> rows = new ArrayList<>();

        dealRepo.findByDealTimeBetweenAndDeletedFalse(range[0], range[1]).stream()
                .filter(deal -> partyId == null || deal.getParty().getId().equals(partyId))
                .filter(deal -> normalizedType.isEmpty() || normalizedType.equals("DEAL"))
                .map(deal -> new TradingDtos.TransactionDetailRow(
                        "DEAL",
                        deal.getId(),
                        deal.getDealTime(),
                        deal.getParty().getId(),
                        deal.getParty().getName(),
                        deal.getId(),
                        deal.getInstrumentCode().name(),
                        deal.getQuantity(),
                        deal.getBdtGross(),
                        deal.getBdtRate(),
                        deal.getDealType().name(),
                        "Deal #" + deal.getId(),
                        null,
                        null,
                        deal.getNotes(),
                        null,
                        null
                ))
                .forEach(rows::add);

        settlementRepo.findBySettlementTimeBetweenAndDeletedFalse(range[0], range[1]).stream()
                .filter(settlement -> partyId == null || settlement.getParty().getId().equals(partyId))
                .filter(settlement -> normalizedType.isEmpty() || normalizedType.equals("SETTLEMENT"))
                .map(settlement -> new TradingDtos.TransactionDetailRow(
                        "SETTLEMENT",
                        settlement.getId(),
                        settlement.getSettlementTime(),
                        settlement.getParty().getId(),
                        settlement.getParty().getName(),
                        settlement.getTradeDeal() == null ? null : settlement.getTradeDeal().getId(),
                        settlement.getTradeDeal() == null ? null : settlement.getTradeDeal().getInstrumentCode().name(),
                        settlement.getTradeDeal() == null ? null : settlement.getTradeDeal().getQuantity(),
                        settlement.getBdtAmount(),
                        null,
                        settlement.getDirection().name() + " / " + settlement.getBasis().name(),
                        "Settlement #" + settlement.getId(),
                        settlement.getPaymentMethod() == null ? null : settlement.getPaymentMethod().name(),
                        settlement.getPaymentReference(),
                        settlement.getNotes(),
                        null,
                        null
                ))
                .forEach(rows::add);

        expenseRepo.findByExpenseTimeBetweenAndDeletedFalse(range[0], range[1]).stream()
                .filter(expense -> partyId == null || Objects.equals(expensePartyId(expense), partyId))
                .filter(expense -> normalizedType.isEmpty() || normalizedType.equals("EXPENSE"))
                .map(expense -> new TradingDtos.TransactionDetailRow(
                        "EXPENSE",
                        expense.getId(),
                        expense.getExpenseTime(),
                        expensePartyId(expense),
                        expensePartyName(expense),
                        expense.getTradeDeal() == null ? null : expense.getTradeDeal().getId(),
                        expense.getTradeDeal() == null ? null : expense.getTradeDeal().getInstrumentCode().name(),
                        expense.getTradeDeal() == null ? null : expense.getTradeDeal().getQuantity(),
                        expense.getAmountBdt(),
                        null,
                        expense.getExpenseType().name(),
                        "Expense #" + expense.getId(),
                        null,
                        null,
                        expense.getNotes(),
                        expense.getExpenseType().name(),
                        expense.getCategory()
                ))
                .forEach(rows::add);

        ledgerRepo.findByReferenceTypeAndEntryTimeBetween("OPENING_BALANCE", range[0], range[1]).stream()
                .filter(entry -> entry.getAccountCode().startsWith("RECEIVABLE_") || entry.getAccountCode().startsWith("PAYABLE_"))
                .filter(entry -> partyId == null || Objects.equals(entry.getReferenceId(), partyId))
                .filter(entry -> normalizedType.isEmpty() || normalizedType.equals("OPENING_BALANCE"))
                .map(entry -> {
                    Party party = partyRepo.findByIdAndDeletedFalse(entry.getReferenceId()).orElse(null);
                    return new TradingDtos.TransactionDetailRow(
                            "OPENING_BALANCE",
                            entry.getId(),
                            entry.getEntryTime(),
                            party == null ? null : party.getId(),
                            party == null ? null : party.getName(),
                            null,
                            null,
                            null,
                            entry.getDebit().subtract(entry.getCredit()).abs(),
                            null,
                            entry.getAccountCode().startsWith("RECEIVABLE_") ? "OPENING RECEIVABLE" : "OPENING PAYABLE",
                            "Opening Balance • " + entry.getAccountCode(),
                            null,
                            null,
                            entry.getNarration(),
                            null,
                            null
                    );
                })
                .forEach(rows::add);

        rows = rows.stream()
                .filter(row -> matchesSearch(row, normalizedSearch))
                .sorted(transactionComparator(normalizedSortField, normalizedSortDirection))
                .toList();

        return new TradingDtos.TransactionDetailsResponse(
                safeFrom,
                safeTo,
                normalizedType,
                partyId,
                search,
                normalizedSortField,
                normalizedSortDirection,
                rows
        );
    }

    public TradingDtos.TransactionExportReport transactionExportReport(
            LocalDate from,
            LocalDate to,
            String type,
            Long partyId,
            String search,
            String sortField,
            String sortDirection
    ) {
        TradingDtos.TransactionDetailsResponse details = transactionDetails(from, to, type, partyId, search, sortField, sortDirection);
        Map<Long, List<TradingDtos.TransactionDetailRow>> byParty = new LinkedHashMap<>();
        for (TradingDtos.TransactionDetailRow row : details.rows()) {
            if (!"DEAL".equals(row.entryType()) && !"SETTLEMENT".equals(row.entryType()) && !"OPENING_BALANCE".equals(row.entryType())) {
                continue;
            }
            if (row.partyId() == null) {
                continue;
            }
            byParty.computeIfAbsent(row.partyId(), ignored -> new ArrayList<>()).add(row);
        }

        List<TradingDtos.TransactionPartyExportSection> sections = new ArrayList<>();
        for (Map.Entry<Long, List<TradingDtos.TransactionDetailRow>> entry : byParty.entrySet()) {
            Long pid = entry.getKey();
            List<TradingDtos.TransactionDetailRow> rows = entry.getValue();
            Party party = partyRepo.findById(pid).orElse(null);
            TradingDtos.PartyIdentity partyIdentity = new TradingDtos.PartyIdentity(
                    pid,
                    party == null ? rows.stream().map(TradingDtos.TransactionDetailRow::partyName).filter(Objects::nonNull).findFirst().orElse("Party #" + pid) : party.getName(),
                    party == null ? null : party.getPhone(),
                    party == null ? null : party.getAddress()
            );

            List<TradingDtos.TransactionDealExportRow> deals = rows.stream()
                    .filter(r -> "DEAL".equals(r.entryType()))
                    .map(r -> new TradingDtos.TransactionDealExportRow(
                            r.entryId(),
                            r.occurredAt().toLocalDate(),
                            r.occurredAt().toLocalTime().withNano(0).toString(),
                            r.directionLabel(),
                            r.instrumentCode(),
                            r.quantity() == null ? BigDecimal.ZERO : r.quantity(),
                            r.bdtRate() == null ? BigDecimal.ZERO : r.bdtRate(),
                            r.amountBdt() == null ? BigDecimal.ZERO : r.amountBdt()
                    ))
                    .toList();

            List<TradingDtos.TransactionSettlementExportRow> settlements = rows.stream()
                    .filter(r -> "SETTLEMENT".equals(r.entryType()))
                    .map(r -> new TradingDtos.TransactionSettlementExportRow(
                            r.entryId(),
                            r.occurredAt().toLocalDate(),
                            r.occurredAt().toLocalTime().withNano(0).toString(),
                            r.directionLabel(),
                            r.paymentMethod(),
                            r.tradeDealId(),
                            r.amountBdt() == null ? BigDecimal.ZERO : r.amountBdt()
                    ))
                    .toList();

            TradingDtos.TransactionDealSummary dealSummary = summarizeDeals(deals);
            TradingDtos.TransactionSettlementSummary settlementSummary = summarizeSettlements(settlements);
            TradingDtos.PartyBalanceSummary exposure = party == null
                    ? new TradingDtos.PartyBalanceSummary(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)
                    : partyBalanceSummary(party, details.to());

            sections.add(new TradingDtos.TransactionPartyExportSection(
                    partyIdentity,
                    deals,
                    settlements,
                    dealSummary,
                    settlementSummary,
                    new TradingDtos.PartyBalanceSummary(
                            bdt(exposure.receivableBdt()),
                            bdt(exposure.payableBdt()),
                            bdt(exposure.advanceFromPartyBdt()),
                            bdt(exposure.advanceToPartyBdt()),
                            bdt(exposure.netBalanceBdt()),
                            bdt(exposure.agingDueBdt())
                    )
            ));
        }

        TradingDtos.TransactionDealSummary grandDeals = summarizeDeals(
                sections.stream().flatMap(s -> s.deals().stream()).toList()
        );
        TradingDtos.TransactionSettlementSummary grandSettlements = summarizeSettlements(
                sections.stream().flatMap(s -> s.settlements().stream()).toList()
        );
        TradingDtos.PartyBalanceSummary grandExposure = new TradingDtos.PartyBalanceSummary(
                bdt(sections.stream().map(s -> s.exposureSummary().receivableBdt()).reduce(BigDecimal.ZERO, BigDecimal::add)),
                bdt(sections.stream().map(s -> s.exposureSummary().payableBdt()).reduce(BigDecimal.ZERO, BigDecimal::add)),
                bdt(sections.stream().map(s -> s.exposureSummary().advanceFromPartyBdt()).reduce(BigDecimal.ZERO, BigDecimal::add)),
                bdt(sections.stream().map(s -> s.exposureSummary().advanceToPartyBdt()).reduce(BigDecimal.ZERO, BigDecimal::add)),
                bdt(sections.stream().map(s -> s.exposureSummary().netBalanceBdt()).reduce(BigDecimal.ZERO, BigDecimal::add)),
                bdt(sections.stream().map(s -> s.exposureSummary().agingDueBdt()).reduce(BigDecimal.ZERO, BigDecimal::add))
        );

        return new TradingDtos.TransactionExportReport(
                details.from(),
                details.to(),
                details.typeFilter(),
                details.partyId(),
                details.search(),
                details.sortField(),
                details.sortDirection(),
                sections,
                grandDeals,
                grandSettlements,
                grandExposure
        );
    }

    public TradingDtos.PartyLedgerResponse partyLedger(Long partyId) {
        Party p = partyRepo.findByIdAndDeletedFalse(partyId).orElseThrow(() -> new ApiException("Party not found"));
        List<TradingDtos.PartyLedgerLine> lines = new ArrayList<>();

        var deals = dealRepo.findByDeletedFalse().stream().filter(d -> d.getParty().getId().equals(partyId)).toList();
        for (TradeDeal d : deals) {
            BigDecimal signed = d.getDealType() == DealType.SELL ? d.getBdtGross() : d.getBdtGross().negate();
            lines.add(new TradingDtos.PartyLedgerLine("DEAL-" + d.getDealType() + "-" + d.getInstrumentCode(), d.getDealTime(), signed, d.getNotes()));
        }
        for (Settlement s : settlementRepo.findByPartyAndDeletedFalseOrderBySettlementTimeAsc(p)) {
            lines.add(new TradingDtos.PartyLedgerLine(
                    "SETTLEMENT-" + s.getDirection() + "-" + s.getBasis(),
                    s.getSettlementTime(),
                    settlementNetEffect(s),
                    s.getNotes()
            ));
        }
        ledgerRepo.findByReferenceTypeAndReferenceId("OPENING_BALANCE", partyId).stream()
                .filter(entry -> entry.getAccountCode().startsWith("RECEIVABLE_") || entry.getAccountCode().startsWith("PAYABLE_"))
                .forEach(entry -> lines.add(new TradingDtos.PartyLedgerLine(
                        "OPENING_BALANCE-" + (entry.getAccountCode().startsWith("RECEIVABLE_") ? "RECEIVABLE" : "PAYABLE"),
                        entry.getEntryTime(),
                        entry.getDebit().subtract(entry.getCredit()),
                        entry.getNarration()
                )));
        lines.sort(Comparator.comparing(TradingDtos.PartyLedgerLine::time));
        TradingDtos.PartyLedgerResponse response =
                new TradingDtos.PartyLedgerResponse(p.getId(), p.getName(), partyBalanceSummary(p), lines);
        if (tradingDebug) {
            log.info("party_ledger_response partyId={} receivable={} payable={} advanceIn={} advanceOut={} net={} lineCount={}",
                    partyId,
                    response.balances().receivableBdt(),
                    response.balances().payableBdt(),
                    response.balances().advanceFromPartyBdt(),
                    response.balances().advanceToPartyBdt(),
                    response.balances().netBalanceBdt(),
                    response.lines().size());
        }
        return response;
    }

    public BigDecimal partyOutstanding(Long partyId) {
        TradingDtos.PartyBalanceSummary balances = partyBalanceSummary(
                partyRepo.findByIdAndDeletedFalse(partyId).orElseThrow(() -> new ApiException("Party not found"))
        );
        return balances.netBalanceBdt();
    }

    public BigDecimal computeAgingDue(Long partyId, int olderThanDays) {
        Party party = partyRepo.findByIdAndDeletedFalse(partyId).orElseThrow(() -> new ApiException("Party not found"));
        return computeAgingDue(party, businessToday());
    }

    private BigDecimal computeAgingDue(Party party, LocalDate asOfDate) {
        BigDecimal aging = BigDecimal.ZERO;

        List<TradeDeal> sellDeals = dealRepo.findByDeletedFalse().stream()
                .filter(d -> d.getParty().getId().equals(party.getId())
                        && d.getDealType() == DealType.SELL
                        && !d.getDealTime().toLocalDate().isAfter(asOfDate))
                .sorted(Comparator.comparing(TradeDeal::getDealTime))
                .toList();
        BigDecimal remainingSettlements = settlementRepo.findByPartyAndDeletedFalseOrderBySettlementTimeAsc(party).stream()
                .filter(s -> !s.getSettlementTime().toLocalDate().isAfter(asOfDate))
                .filter(s -> s.getDirection() == SettlementDirection.INCOMING && s.getBasis() == SettlementBasis.RECEIVABLE)
                .map(Settlement::getAppliedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        for (TradeDeal deal : sellDeals) {
            BigDecimal covered = remainingSettlements.min(deal.getBdtGross());
            remainingSettlements = remainingSettlements.subtract(covered);
            BigDecimal outstanding = deal.getBdtGross().subtract(covered).max(BigDecimal.ZERO);
            if (outstanding.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }

            aging = aging.add(outstanding);
        }
        return aging;
    }

    private TradingDtos.PartyBalanceSummary partyBalanceSummary(Party party) {
        return partyBalanceSummary(party, null);
    }

    private TradingDtos.PartyBalanceSummary partyBalanceSummary(Party party, LocalDate asOfDate) {
        return toPartyBalanceSummary(projectPartyBalance(party, asOfDate));
    }

    private TradingDtos.PartyBalanceSummary toPartyBalanceSummary(BalancePosition balance) {
        return new TradingDtos.PartyBalanceSummary(
                bdt(balance.receivableBdt()),
                bdt(balance.payableBdt()),
                bdt(balance.advanceFromPartyBdt()),
                bdt(balance.advanceToPartyBdt()),
                bdt(balance.netBalanceBdt()),
                bdt(balance.agingDueBdt())
        );
    }

    private BalancePosition projectPartyBalance(Party party, LocalDate asOfDate) {
        LocalDateTime cutoff = asOfDate == null
                ? businessNow()
                : asOfDate.plusDays(1).atStartOfDay().minusNanos(1);
        BigDecimal openingReceivable = openingBalanceForPartyAccount(party.getId(), "RECEIVABLE_", cutoff);
        BigDecimal openingPayable = openingBalanceForPartyAccount(party.getId(), "PAYABLE_", cutoff).negate();
        BigDecimal receivable = openingReceivable;
        BigDecimal payable = openingPayable;
        BigDecimal advanceFromParty = BigDecimal.ZERO;
        BigDecimal advanceToParty = BigDecimal.ZERO;
        BigDecimal buyDealPayable = BigDecimal.ZERO;
        BigDecimal sellDealReceivable = BigDecimal.ZERO;
        BigDecimal incomingReceivableApplied = BigDecimal.ZERO;
        BigDecimal outgoingPayableApplied = BigDecimal.ZERO;
        BigDecimal advanceFromPartyAdded = BigDecimal.ZERO;
        BigDecimal advanceToPartyAdded = BigDecimal.ZERO;
        BigDecimal advanceFromPartyCleared = BigDecimal.ZERO;
        BigDecimal advanceToPartyCleared = BigDecimal.ZERO;

        for (TradeDeal deal : dealRepo.findByDeletedFalse()) {
            if (!deal.getParty().getId().equals(party.getId()) || deal.getDealTime().isAfter(cutoff)) {
                continue;
            }
            if (deal.getDealType() == DealType.BUY) {
                payable = payable.add(deal.getBdtGross());
                buyDealPayable = buyDealPayable.add(deal.getBdtGross());
            } else {
                receivable = receivable.add(deal.getBdtGross());
                sellDealReceivable = sellDealReceivable.add(deal.getBdtGross());
            }
        }

        for (Settlement settlement : settlementRepo.findByPartyAndDeletedFalseOrderBySettlementTimeAsc(party)) {
            if (settlement.getSettlementTime().isAfter(cutoff)) {
                continue;
            }
            if (settlement.getDirection() == SettlementDirection.INCOMING) {
                if (settlement.getBasis() == SettlementBasis.RECEIVABLE) {
                    receivable = receivable.subtract(settlement.getAppliedAmount());
                    incomingReceivableApplied = incomingReceivableApplied.add(settlement.getAppliedAmount());
                } else if (settlement.getBasis() == SettlementBasis.ADVANCE_TO_PARTY) {
                    advanceToParty = advanceToParty.subtract(settlement.getAppliedAmount());
                    advanceToPartyCleared = advanceToPartyCleared.add(settlement.getAppliedAmount());
                }
                advanceFromParty = advanceFromParty.add(settlement.getAdvanceAmount());
                advanceFromPartyAdded = advanceFromPartyAdded.add(settlement.getAdvanceAmount());
            } else {
                if (settlement.getBasis() == SettlementBasis.PAYABLE) {
                    payable = payable.subtract(settlement.getAppliedAmount());
                    outgoingPayableApplied = outgoingPayableApplied.add(settlement.getAppliedAmount());
                } else if (settlement.getBasis() == SettlementBasis.ADVANCE_FROM_PARTY) {
                    advanceFromParty = advanceFromParty.subtract(settlement.getAppliedAmount());
                    advanceFromPartyCleared = advanceFromPartyCleared.add(settlement.getAppliedAmount());
                }
                advanceToParty = advanceToParty.add(settlement.getAdvanceAmount());
                advanceToPartyAdded = advanceToPartyAdded.add(settlement.getAdvanceAmount());
            }
        }

        receivable = receivable.max(BigDecimal.ZERO);
        payable = payable.max(BigDecimal.ZERO);
        BigDecimal aging = computeAgingDue(party, asOfDate == null ? businessToday() : asOfDate);
        advanceFromParty = advanceFromParty.max(BigDecimal.ZERO);
        advanceToParty = advanceToParty.max(BigDecimal.ZERO);
        BigDecimal net = receivable.add(advanceToParty).subtract(payable).subtract(advanceFromParty);
        BalancePosition position = new BalancePosition(receivable, payable, advanceFromParty, advanceToParty, net, aging);
        if (tradingDebug) {
            log.info("party_balance_projection partyId={} asOfDate={} openingReceivable={} openingPayable={} buyDealAddedPayable={} sellDealAddedReceivable={} incomingSettlementApplied={} outgoingSettlementApplied={} advanceInAdded={} advanceOutAdded={} advanceInCleared={} advanceOutCleared={} finalReceivable={} finalPayable={} finalAdvanceIn={} finalAdvanceOut={} finalNet={} finalAging={}",
                    party.getId(),
                    asOfDate,
                    openingReceivable,
                    openingPayable,
                    buyDealPayable,
                    sellDealReceivable,
                    incomingReceivableApplied,
                    outgoingPayableApplied,
                    advanceFromPartyAdded,
                    advanceToPartyAdded,
                    advanceFromPartyCleared,
                    advanceToPartyCleared,
                    position.receivableBdt(),
                    position.payableBdt(),
                    position.advanceFromPartyBdt(),
                    position.advanceToPartyBdt(),
                    position.netBalanceBdt(),
                    position.agingDueBdt());
        }
        return position;
    }

    private BigDecimal openingBalanceForPartyAccount(Long partyId, String accountPrefix, LocalDateTime cutoff) {
        return ledgerRepo.findByReferenceTypeAndReferenceId("OPENING_BALANCE", partyId).stream()
                .filter(entry -> entry.getAccountCode().startsWith(accountPrefix))
                .filter(entry -> !entry.getEntryTime().isAfter(cutoff))
                .map(entry -> entry.getDebit().subtract(entry.getCredit()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private TradingDtos.SettlementInferenceResponse toInferenceResponse(Party party, TradeDeal deal, BigDecimal amount) {
        BalancePosition current = projectPartyBalance(party, null);
        SettlementPlan plan = inferSettlementPlan(party, deal, amount);
        BalancePosition projected = applyPlan(current, plan);
        TradingDtos.SettlementInferenceResponse response = new TradingDtos.SettlementInferenceResponse(
                party.getId(),
                deal == null ? null : deal.getId(),
                toPartyBalanceSummary(current),
                toPartyBalanceSummary(projected),
                plan.direction(),
                plan.basis(),
                plan.appliedAmount(),
                plan.advanceAmount(),
                plan.amountLabel(),
                plan.summary()
        );
        if (tradingDebug) {
            log.info("settlement_inference_response partyId={} dealId={} amount={} direction={} basis={} applied={} advance={} currentReceivable={} currentPayable={} projectedReceivable={} projectedPayable={} projectedNet={}",
                    party.getId(),
                    deal == null ? null : deal.getId(),
                    amount,
                    response.direction(),
                    response.basis(),
                    response.appliedAmount(),
                    response.advanceAmount(),
                    response.current().receivableBdt(),
                    response.current().payableBdt(),
                    response.projected().receivableBdt(),
                    response.projected().payableBdt(),
                    response.projected().netBalanceBdt());
        }
        return response;
    }

    private SettlementPlan inferSettlementPlan(Party party, TradeDeal deal, BigDecimal amount) {
        BalancePosition current = projectPartyBalance(party, null);
        BigDecimal safeAmount = amount == null ? BigDecimal.ZERO : amount.max(BigDecimal.ZERO);
        SettlementDirection direction;
        SettlementBasis basis;

        if (deal != null) {
            if (deal.getDealType() == DealType.SELL) {
                direction = SettlementDirection.INCOMING;
                basis = SettlementBasis.RECEIVABLE;
            } else {
                direction = SettlementDirection.OUTGOING;
                basis = SettlementBasis.PAYABLE;
            }
        } else if (current.receivableBdt().compareTo(BigDecimal.ZERO) > 0 && current.payableBdt().compareTo(BigDecimal.ZERO) > 0) {
            throw new ApiException("Party has both receivable and payable. Select a related deal.");
        } else if (current.receivableBdt().compareTo(BigDecimal.ZERO) > 0) {
            direction = SettlementDirection.INCOMING;
            basis = SettlementBasis.RECEIVABLE;
        } else if (current.payableBdt().compareTo(BigDecimal.ZERO) > 0) {
            direction = SettlementDirection.OUTGOING;
            basis = SettlementBasis.PAYABLE;
        } else if (current.advanceToPartyBdt().compareTo(BigDecimal.ZERO) > 0 && current.advanceFromPartyBdt().compareTo(BigDecimal.ZERO) > 0) {
            throw new ApiException("Party has advance on both sides. Select a related deal.");
        } else if (current.advanceToPartyBdt().compareTo(BigDecimal.ZERO) > 0) {
            direction = SettlementDirection.INCOMING;
            basis = SettlementBasis.ADVANCE_TO_PARTY;
        } else if (current.advanceFromPartyBdt().compareTo(BigDecimal.ZERO) > 0) {
            direction = SettlementDirection.OUTGOING;
            basis = SettlementBasis.ADVANCE_FROM_PARTY;
        } else {
            TradeDeal latestDeal = latestDealForParty(party.getId());
            if (latestDeal == null) {
                throw new ApiException("No due or history for this party. Select a related deal to record an advance.");
            }
            if (latestDeal.getDealType() == DealType.SELL) {
                direction = SettlementDirection.INCOMING;
                basis = SettlementBasis.NONE;
            } else {
                direction = SettlementDirection.OUTGOING;
                basis = SettlementBasis.NONE;
            }
        }

        BigDecimal baseOutstanding = switch (basis) {
            case RECEIVABLE -> current.receivableBdt();
            case PAYABLE -> current.payableBdt();
            case ADVANCE_FROM_PARTY -> current.advanceFromPartyBdt();
            case ADVANCE_TO_PARTY -> current.advanceToPartyBdt();
            case NONE -> BigDecimal.ZERO;
        };
        BigDecimal applied = safeAmount.min(baseOutstanding);
        BigDecimal advance = safeAmount.subtract(applied).max(BigDecimal.ZERO);
        String amountLabel = direction == SettlementDirection.INCOMING ? "Amount to Receive" : "Amount to Pay";
        if (basis == SettlementBasis.NONE && advance.compareTo(BigDecimal.ZERO) > 0) {
            amountLabel = "Advance to Record";
        }
        String summary = buildSummary(party.getName(), direction, basis, safeAmount, applied, advance);
        return new SettlementPlan(direction, basis, applied, advance, amountLabel, summary);
    }

    private String buildSummary(String partyName, SettlementDirection direction, SettlementBasis basis,
                                BigDecimal amount, BigDecimal applied, BigDecimal advance) {
        String action = direction == SettlementDirection.INCOMING ? "receiving" : "paying";
        if (basis == SettlementBasis.ADVANCE_TO_PARTY && direction == SettlementDirection.INCOMING) {
            return "You are receiving " + amount + " BDT back from " + partyName + " against your advance.";
        }
        if (basis == SettlementBasis.ADVANCE_FROM_PARTY && direction == SettlementDirection.OUTGOING) {
            return "You are paying " + amount + " BDT back to " + partyName + " against their advance.";
        }
        if (advance.compareTo(BigDecimal.ZERO) > 0) {
            return "You are " + action + " " + amount + " BDT. " + applied + " BDT will clear due and " + advance + " BDT will become advance.";
        }
        if (basis == SettlementBasis.NONE) {
            return "You are " + action + " " + amount + " BDT as an advance with " + partyName + ".";
        }
        String target = switch (basis) {
            case RECEIVABLE -> "receivable";
            case PAYABLE -> "payable";
            case ADVANCE_FROM_PARTY -> "party advance";
            case ADVANCE_TO_PARTY -> "supplier advance";
            case NONE -> "advance";
        };
        return "You are " + action + " " + amount + " BDT to reduce " + target + " with " + partyName + ".";
    }

    private BalancePosition applyPlan(BalancePosition current, SettlementPlan plan) {
        BigDecimal receivable = current.receivableBdt();
        BigDecimal payable = current.payableBdt();
        BigDecimal advanceFromParty = current.advanceFromPartyBdt();
        BigDecimal advanceToParty = current.advanceToPartyBdt();

        if (plan.direction() == SettlementDirection.INCOMING) {
            if (plan.basis() == SettlementBasis.RECEIVABLE) {
                receivable = receivable.subtract(plan.appliedAmount());
            } else if (plan.basis() == SettlementBasis.ADVANCE_TO_PARTY) {
                advanceToParty = advanceToParty.subtract(plan.appliedAmount());
            }
            advanceFromParty = advanceFromParty.add(plan.advanceAmount());
        } else {
            if (plan.basis() == SettlementBasis.PAYABLE) {
                payable = payable.subtract(plan.appliedAmount());
            } else if (plan.basis() == SettlementBasis.ADVANCE_FROM_PARTY) {
                advanceFromParty = advanceFromParty.subtract(plan.appliedAmount());
            }
            advanceToParty = advanceToParty.add(plan.advanceAmount());
        }

        receivable = receivable.max(BigDecimal.ZERO);
        payable = payable.max(BigDecimal.ZERO);
        advanceFromParty = advanceFromParty.max(BigDecimal.ZERO);
        advanceToParty = advanceToParty.max(BigDecimal.ZERO);
        BigDecimal agingDue = current.agingDueBdt();
        if (plan.direction() == SettlementDirection.INCOMING && plan.basis() == SettlementBasis.RECEIVABLE) {
            agingDue = agingDue.subtract(plan.appliedAmount()).max(BigDecimal.ZERO);
        }
        BigDecimal net = receivable.add(advanceToParty).subtract(payable).subtract(advanceFromParty);
        return new BalancePosition(receivable, payable, advanceFromParty, advanceToParty, net, agingDue);
    }

    private TradingDtos.StatementLine toStatementLine(StatementSnapshot snapshot) {
        return new TradingDtos.StatementLine(
                snapshot.getBusinessDate(),
                snapshot.getOpeningCashBdt(),
                snapshot.getClosingCashBdt(),
                snapshot.getOpeningUsd(),
                snapshot.getClosingUsd(),
                snapshot.getOpeningReceivableBdt(),
                snapshot.getClosingReceivableBdt(),
                snapshot.getOpeningPayableBdt(),
                snapshot.getClosingPayableBdt(),
                snapshot.getOpeningAdvanceFromPartyBdt(),
                snapshot.getClosingAdvanceFromPartyBdt(),
                snapshot.getOpeningAdvanceToPartyBdt(),
                snapshot.getClosingAdvanceToPartyBdt(),
                snapshot.getOpeningAgingBdt(),
                snapshot.getClosingAgingBdt(),
                snapshot.getRealizedProfitLossBdt()
        );
    }

    private BalancePosition aggregateBusinessPositionAt(LocalDate asOfDate) {
        return projectBusinessBalance(asOfDate);
    }

    private BalancePosition businessPositionAt(LocalDate asOfDate) {
        return projectBusinessBalance(asOfDate);
    }

    private BalancePosition projectBusinessBalance(LocalDate asOfDate) {
        BigDecimal receivable = BigDecimal.ZERO;
        BigDecimal payable = BigDecimal.ZERO;
        BigDecimal advanceFromParty = BigDecimal.ZERO;
        BigDecimal advanceToParty = BigDecimal.ZERO;
        BigDecimal agingDue = BigDecimal.ZERO;

        for (Party party : partyRepo.findByDeletedFalse()) {
            BalancePosition summary = projectPartyBalance(party, asOfDate);
            receivable = receivable.add(summary.receivableBdt());
            payable = payable.add(summary.payableBdt());
            advanceFromParty = advanceFromParty.add(summary.advanceFromPartyBdt());
            advanceToParty = advanceToParty.add(summary.advanceToPartyBdt());
            agingDue = agingDue.add(summary.agingDueBdt());
        }

        BigDecimal net = receivable.add(advanceToParty).subtract(payable).subtract(advanceFromParty);
        BalancePosition position = new BalancePosition(receivable, payable, advanceFromParty, advanceToParty, net, agingDue);
        if (tradingDebug) {
            log.info("business_balance_projection asOfDate={} receivable={} payable={} advanceIn={} advanceOut={} net={} aging={}",
                    asOfDate,
                    position.receivableBdt(),
                    position.payableBdt(),
                    position.advanceFromPartyBdt(),
                    position.advanceToPartyBdt(),
                    position.netBalanceBdt(),
                    position.agingDueBdt());
        }
        return position;
    }

    private String maskFreeText(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 48 ? normalized : normalized.substring(0, 48) + "...";
    }

    private List<TradingDtos.StatementLine> liveStatementRange(LocalDate from, LocalDate to) {
        List<TradingDtos.StatementLine> lines = new ArrayList<>();
        LocalDate cursor = from;
        while (!cursor.isAfter(to)) {
            lines.add(liveStatementLine(cursor));
            cursor = cursor.plusDays(1);
        }
        return lines;
    }

    private TradingDtos.StatementLine liveStatementLine(LocalDate businessDate) {
        LocalDate priorDate = businessDate.minusDays(1);
        BalancePosition openingPosition = businessPositionAt(priorDate);
        BalancePosition closingPosition = businessPositionAt(businessDate);
        BigDecimal openingCash = cashAt(priorDate);
        BigDecimal closingCash = cashAt(businessDate);
        BigDecimal openingUsd = usdAt(priorDate);
        BigDecimal closingUsd = usdAt(businessDate);
        BigDecimal dayPnl = bdt(pnlMetrics(businessDate, businessDate).netPnlBdt());

        return new TradingDtos.StatementLine(
                businessDate,
                bdt(openingCash),
                bdt(closingCash),
                openingUsd,
                closingUsd,
                bdt(openingPosition.receivableBdt()),
                bdt(closingPosition.receivableBdt()),
                bdt(openingPosition.payableBdt()),
                bdt(closingPosition.payableBdt()),
                bdt(openingPosition.advanceFromPartyBdt()),
                bdt(closingPosition.advanceFromPartyBdt()),
                bdt(openingPosition.advanceToPartyBdt()),
                bdt(closingPosition.advanceToPartyBdt()),
                bdt(openingPosition.agingDueBdt()),
                bdt(closingPosition.agingDueBdt()),
                dayPnl
        );
    }

    private BigDecimal cashAt(LocalDate asOfDate) {
        LocalDateTime cutoff = asOfDate.plusDays(1).atStartOfDay().minusNanos(1);
        return netForAccountUntilActive("CASH", cutoff);
    }

    private BigDecimal usdAt(LocalDate asOfDate) {
        LocalDateTime cutoff = asOfDate.plusDays(1).atStartOfDay().minusNanos(1);
        return netForPrefixUntilActive("FX_INVENTORY_", cutoff);
    }

    private BalancePosition closingPositionFromSnapshot(StatementSnapshot snapshot) {
        return new BalancePosition(
                snapshot.getClosingReceivableBdt(),
                snapshot.getClosingPayableBdt(),
                snapshot.getClosingAdvanceFromPartyBdt(),
                snapshot.getClosingAdvanceToPartyBdt(),
                snapshot.getClosingReceivableBdt()
                        .add(snapshot.getClosingAdvanceToPartyBdt())
                        .subtract(snapshot.getClosingPayableBdt())
                        .subtract(snapshot.getClosingAdvanceFromPartyBdt()),
                snapshot.getClosingAgingBdt()
        );
    }

    private BalancePosition zeroBalancePosition() {
        return new BalancePosition(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );
    }

    private void postSettlementLedger(Settlement st, LocalDateTime at) {
        String referenceType = "SETTLEMENT";
        Long referenceId = st.getId();
        Long partyId = st.getParty().getId();
        if (st.getDirection() == SettlementDirection.INCOMING) {
            ledgerService.post(at, "CASH", st.getBdtAmount(), BigDecimal.ZERO, referenceType, referenceId, "Cash received");
            if (st.getBasis() == SettlementBasis.RECEIVABLE && st.getAppliedAmount().compareTo(BigDecimal.ZERO) > 0) {
                ledgerService.post(at, "RECEIVABLE_" + partyId, BigDecimal.ZERO, st.getAppliedAmount(), referenceType, referenceId, "Settlement against receivable");
            } else if (st.getBasis() == SettlementBasis.ADVANCE_TO_PARTY && st.getAppliedAmount().compareTo(BigDecimal.ZERO) > 0) {
                ledgerService.post(at, "ADVANCE_TO_" + partyId, BigDecimal.ZERO, st.getAppliedAmount(), referenceType, referenceId, "Advance returned by party");
            }
            if (st.getAdvanceAmount().compareTo(BigDecimal.ZERO) > 0) {
                ledgerService.post(at, "ADVANCE_FROM_" + partyId, BigDecimal.ZERO, st.getAdvanceAmount(), referenceType, referenceId, "Advance received from party");
            }
        } else {
            ledgerService.post(at, "CASH", BigDecimal.ZERO, st.getBdtAmount(), referenceType, referenceId, "Cash paid out");
            if (st.getBasis() == SettlementBasis.PAYABLE && st.getAppliedAmount().compareTo(BigDecimal.ZERO) > 0) {
                ledgerService.post(at, "PAYABLE_" + partyId, st.getAppliedAmount(), BigDecimal.ZERO, referenceType, referenceId, "Settlement against payable");
            } else if (st.getBasis() == SettlementBasis.ADVANCE_FROM_PARTY && st.getAppliedAmount().compareTo(BigDecimal.ZERO) > 0) {
                ledgerService.post(at, "ADVANCE_FROM_" + partyId, st.getAppliedAmount(), BigDecimal.ZERO, referenceType, referenceId, "Advance refunded to party");
            }
            if (st.getAdvanceAmount().compareTo(BigDecimal.ZERO) > 0) {
                ledgerService.post(at, "ADVANCE_TO_" + partyId, st.getAdvanceAmount(), BigDecimal.ZERO, referenceType, referenceId, "Advance paid to party");
            }
        }
    }

    private void reverseSettlementLedger(Settlement st, LocalDateTime at) {
        String referenceType = "SETTLEMENT_REVERSAL";
        Long referenceId = st.getId();
        Long partyId = st.getParty().getId();
        if (st.getDirection() == SettlementDirection.INCOMING) {
            ledgerService.post(at, "CASH", BigDecimal.ZERO, st.getBdtAmount(), referenceType, referenceId, "Reversal cash received");
            if (st.getBasis() == SettlementBasis.RECEIVABLE && st.getAppliedAmount().compareTo(BigDecimal.ZERO) > 0) {
                ledgerService.post(at, "RECEIVABLE_" + partyId, st.getAppliedAmount(), BigDecimal.ZERO, referenceType, referenceId, "Reversal receivable settlement");
            } else if (st.getBasis() == SettlementBasis.ADVANCE_TO_PARTY && st.getAppliedAmount().compareTo(BigDecimal.ZERO) > 0) {
                ledgerService.post(at, "ADVANCE_TO_" + partyId, st.getAppliedAmount(), BigDecimal.ZERO, referenceType, referenceId, "Reversal advance returned");
            }
            if (st.getAdvanceAmount().compareTo(BigDecimal.ZERO) > 0) {
                ledgerService.post(at, "ADVANCE_FROM_" + partyId, st.getAdvanceAmount(), BigDecimal.ZERO, referenceType, referenceId, "Reversal advance received");
            }
        } else {
            ledgerService.post(at, "CASH", st.getBdtAmount(), BigDecimal.ZERO, referenceType, referenceId, "Reversal cash paid");
            if (st.getBasis() == SettlementBasis.PAYABLE && st.getAppliedAmount().compareTo(BigDecimal.ZERO) > 0) {
                ledgerService.post(at, "PAYABLE_" + partyId, BigDecimal.ZERO, st.getAppliedAmount(), referenceType, referenceId, "Reversal payable settlement");
            } else if (st.getBasis() == SettlementBasis.ADVANCE_FROM_PARTY && st.getAppliedAmount().compareTo(BigDecimal.ZERO) > 0) {
                ledgerService.post(at, "ADVANCE_FROM_" + partyId, BigDecimal.ZERO, st.getAppliedAmount(), referenceType, referenceId, "Reversal advance refund");
            }
            if (st.getAdvanceAmount().compareTo(BigDecimal.ZERO) > 0) {
                ledgerService.post(at, "ADVANCE_TO_" + partyId, BigDecimal.ZERO, st.getAdvanceAmount(), referenceType, referenceId, "Reversal advance paid");
            }
        }
    }

    private void postDealLedger(TradeDeal deal, LocalDateTime at) {
        String referenceType = "DEAL";
        Long referenceId = deal.getId();
        String inventoryAccount = fxInventoryAccount(deal.getInstrumentCode().name());
        if (deal.getDealType() == DealType.BUY) {
            ledgerService.post(at, inventoryAccount, deal.getQuantity(), BigDecimal.ZERO, referenceType, referenceId, "Buy " + deal.getInstrumentCode().name());
            ledgerService.post(at, "PAYABLE_" + deal.getParty().getId(), BigDecimal.ZERO, deal.getBdtGross(), referenceType, referenceId, "Payable to party");
        } else {
            ledgerService.post(at, "RECEIVABLE_" + deal.getParty().getId(), deal.getBdtGross(), BigDecimal.ZERO, referenceType, referenceId, "Receivable from party");
            ledgerService.post(at, inventoryAccount, BigDecimal.ZERO, deal.getQuantity(), referenceType, referenceId, "Sell " + deal.getInstrumentCode().name());
        }
    }

    private void reverseDealLedger(TradeDeal deal, LocalDateTime at) {
        String referenceType = "DEAL_REVERSAL";
        Long referenceId = deal.getId();
        String inventoryAccount = fxInventoryAccount(deal.getInstrumentCode().name());
        if (deal.getDealType() == DealType.BUY) {
            ledgerService.post(at, inventoryAccount, BigDecimal.ZERO, deal.getQuantity(), referenceType, referenceId, "Reversal buy " + deal.getInstrumentCode().name());
            ledgerService.post(at, "PAYABLE_" + deal.getParty().getId(), deal.getBdtGross(), BigDecimal.ZERO, referenceType, referenceId, "Reversal payable");
        } else {
            ledgerService.post(at, "RECEIVABLE_" + deal.getParty().getId(), BigDecimal.ZERO, deal.getBdtGross(), referenceType, referenceId, "Reversal receivable");
            ledgerService.post(at, inventoryAccount, deal.getQuantity(), BigDecimal.ZERO, referenceType, referenceId, "Reversal sell " + deal.getInstrumentCode().name());
        }
    }

    private void postExpenseLedger(Expense expense, LocalDateTime at) {
        ledgerService.post(at, "EXPENSE", expense.getAmountBdt(), BigDecimal.ZERO, "EXPENSE", expense.getId(), expense.getCategory());
        ledgerService.post(at, "CASH", BigDecimal.ZERO, expense.getAmountBdt(), "EXPENSE", expense.getId(), expense.getCategory());
    }

    private void reverseExpenseLedger(Expense expense, LocalDateTime at) {
        ledgerService.post(at, "EXPENSE", BigDecimal.ZERO, expense.getAmountBdt(), "EXPENSE_REVERSAL", expense.getId(), "Reversal " + expense.getCategory());
        ledgerService.post(at, "CASH", expense.getAmountBdt(), BigDecimal.ZERO, "EXPENSE_REVERSAL", expense.getId(), "Reversal " + expense.getCategory());
    }

    private BigDecimal settlementNetEffect(Settlement settlement) {
        if (settlement.getDirection() == SettlementDirection.INCOMING) {
            if (settlement.getBasis() == SettlementBasis.RECEIVABLE) {
                return settlement.getAppliedAmount().negate().subtract(settlement.getAdvanceAmount());
            }
            if (settlement.getBasis() == SettlementBasis.ADVANCE_TO_PARTY) {
                return settlement.getAppliedAmount().negate();
            }
            return settlement.getAdvanceAmount().negate();
        }
        if (settlement.getBasis() == SettlementBasis.PAYABLE) {
            return settlement.getAppliedAmount().add(settlement.getAdvanceAmount());
        }
        if (settlement.getBasis() == SettlementBasis.ADVANCE_FROM_PARTY) {
            return settlement.getAppliedAmount();
        }
        return settlement.getAdvanceAmount();
    }

    private TradeDeal resolveDeal(Long tradeDealId, Long partyId) {
        if (tradeDealId == null) {
            return null;
        }
        TradeDeal deal = dealRepo.findByIdAndDeletedFalse(tradeDealId).orElseThrow(() -> new ApiException("Deal not found"));
        if (!deal.getParty().getId().equals(partyId)) {
            throw new ApiException("Selected deal does not belong to this party");
        }
        return deal;
    }

    private TradeDeal latestDealForParty(Long partyId) {
        return dealRepo.findByDeletedFalse().stream()
                .filter(d -> d.getParty().getId().equals(partyId))
                .max(Comparator.comparing(TradeDeal::getDealTime))
                .orElse(null);
    }

    private LocalDateTime latestActivityAtForParty(Long partyId) {
        Optional<LocalDateTime> latestDealTime = dealRepo.findByDeletedFalse().stream()
                .filter(d -> d.getParty().getId().equals(partyId))
                .map(TradeDeal::getDealTime)
                .max(LocalDateTime::compareTo);
        Optional<LocalDateTime> latestSettlementTime = settlementRepo.findByDeletedFalse().stream()
                .filter(s -> s.getParty().getId().equals(partyId))
                .map(Settlement::getSettlementTime)
                .max(LocalDateTime::compareTo);
        Optional<LocalDateTime> latestOpeningTime = ledgerRepo.findByReferenceTypeAndReferenceId("OPENING_BALANCE", partyId).stream()
                .filter(entry -> entry.getAccountCode().startsWith("RECEIVABLE_") || entry.getAccountCode().startsWith("PAYABLE_"))
                .map(LedgerEntry::getEntryTime)
                .max(LocalDateTime::compareTo);

        return Stream.of(latestDealTime, latestSettlementTime, latestOpeningTime)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .max(LocalDateTime::compareTo)
                .orElse(null);
    }

    private record BalancePosition(
            BigDecimal receivableBdt,
            BigDecimal payableBdt,
            BigDecimal advanceFromPartyBdt,
            BigDecimal advanceToPartyBdt,
            BigDecimal netBalanceBdt,
            BigDecimal agingDueBdt
    ) {}

    private record SettlementPlan(
            SettlementDirection direction,
            SettlementBasis basis,
            BigDecimal appliedAmount,
            BigDecimal advanceAmount,
            String amountLabel,
            String summary
    ) {}

    private LocalDateTime[] dayRange(LocalDate date) {
        return new LocalDateTime[]{date.atStartOfDay(), date.plusDays(1).atStartOfDay().minusNanos(1)};
    }

    private LocalDateTime[] dayRange(LocalDate from, LocalDate to) {
        return new LocalDateTime[]{from.atStartOfDay(), to.plusDays(1).atStartOfDay().minusNanos(1)};
    }

    private LocalDate[] resolveReportRange(String mode, LocalDate date, Integer month, Integer year, LocalDate from, LocalDate to) {
        return switch (normalizeMode(mode)) {
            case "MONTHLY" -> {
                int safeYear = year == null ? businessToday().getYear() : year;
                int safeMonth = month == null ? businessToday().getMonthValue() : month;
                LocalDate start = LocalDate.of(safeYear, safeMonth, 1);
                yield new LocalDate[]{start, start.withDayOfMonth(start.lengthOfMonth())};
            }
            case "YEARLY" -> {
                int safeYear = year == null ? businessToday().getYear() : year;
                yield new LocalDate[]{LocalDate.of(safeYear, 1, 1), LocalDate.of(safeYear, 12, 31)};
            }
            case "CUSTOM" -> {
                LocalDate safeFrom = from == null ? businessToday() : from;
                LocalDate safeTo = to == null ? safeFrom : to;
                yield new LocalDate[]{safeFrom, safeTo};
            }
            default -> {
                LocalDate safeDate = date == null ? businessToday() : date;
                yield new LocalDate[]{safeDate, safeDate};
            }
        };
    }

    private LocalDate businessToday() {
        return LocalDate.now(businessZone);
    }

    private LocalDateTime businessNow() {
        return LocalDateTime.now(businessZone);
    }

    private String normalizeMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return "DAILY";
        }
        return switch (mode.trim().toUpperCase(Locale.ROOT)) {
            case "MONTHLY" -> "MONTHLY";
            case "YEARLY" -> "YEARLY";
            case "CUSTOM", "RANGE" -> "CUSTOM";
            default -> "DAILY";
        };
    }

    private String normalizeFilter(String type) {
        if (type == null || type.isBlank() || "ALL".equalsIgnoreCase(type)) {
            return "";
        }
        String value = type.trim().toUpperCase(Locale.ROOT);
        return switch (value) {
            case "DEAL", "SETTLEMENT", "EXPENSE", "OPENING_BALANCE" -> value;
            default -> "";
        };
    }

    private String normalizeSortField(String sortField) {
        if (sortField == null || sortField.isBlank()) {
            return "occurredAt";
        }
        return switch (sortField) {
            case "amountBdt", "entryType", "partyName" -> sortField;
            default -> "occurredAt";
        };
    }

    private TradingDtos.TransactionDealSummary summarizeDeals(List<TradingDtos.TransactionDealExportRow> rows) {
        BigDecimal buy = rows.stream()
                .filter(r -> r.direction() != null && r.direction().toUpperCase(Locale.ROOT).contains("BUY"))
                .map(TradingDtos.TransactionDealExportRow::amountBdt)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal sell = rows.stream()
                .filter(r -> r.direction() != null && r.direction().toUpperCase(Locale.ROOT).contains("SELL"))
                .map(TradingDtos.TransactionDealExportRow::amountBdt)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new TradingDtos.TransactionDealSummary(
                rows.size(),
                bdt(buy),
                bdt(sell),
                bdt(sell.subtract(buy))
        );
    }

    private TradingDtos.TransactionSettlementSummary summarizeSettlements(List<TradingDtos.TransactionSettlementExportRow> rows) {
        BigDecimal incoming = rows.stream()
                .filter(r -> r.direction() != null && r.direction().toUpperCase(Locale.ROOT).contains("INCOMING"))
                .map(TradingDtos.TransactionSettlementExportRow::amountBdt)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal outgoing = rows.stream()
                .filter(r -> r.direction() != null && r.direction().toUpperCase(Locale.ROOT).contains("OUTGOING"))
                .map(TradingDtos.TransactionSettlementExportRow::amountBdt)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long linked = rows.stream().filter(r -> r.relatedDealId() != null).count();
        return new TradingDtos.TransactionSettlementSummary(
                rows.size(),
                bdt(incoming),
                bdt(outgoing),
                linked,
                rows.size() - linked
        );
    }

    private Long expensePartyId(Expense expense) {
        return null;
    }

    private String expensePartyName(Expense expense) {
        return null;
    }

    private String fxInventoryAccount(String instrumentCode) {
        return "FX_INVENTORY_" + instrumentCode;
    }

    private BigDecimal latestRateForInstrument(String instrumentCode) {
        return dealRepo.findByDeletedFalse().stream()
                .filter(deal -> deal.getInstrumentCode().name().equals(instrumentCode))
                .max(Comparator.comparing(TradeDeal::getDealTime))
                .map(TradeDeal::getBdtRate)
                .orElse(BigDecimal.ZERO);
    }

    private boolean matchesSearch(TradingDtos.TransactionDetailRow row, String search) {
        if (search.isEmpty()) {
            return true;
        }
        return containsIgnoreCase(row.partyName(), search)
                || containsIgnoreCase(row.notes(), search)
                || containsIgnoreCase(row.category(), search)
                || containsIgnoreCase(row.referenceLabel(), search)
                || containsIgnoreCase(row.directionLabel(), search);
    }

    private TradingDtos.PnlExplainSection buildExplainSection(String label, LocalDate from, LocalDate to) {
        PnlMetrics metrics = pnlMetrics(from, to);
        LocalDateTime[] range = dayRange(from, to);
        List<TradeDeal> deals = dealRepo.findByDealTimeBetweenAndDeletedFalse(range[0], range[1]);
        Map<String, List<Expense>> grouped = expenseRepo.findByExpenseTimeBetweenAndDeletedFalse(range[0], range[1]).stream()
                .collect(java.util.stream.Collectors.groupingBy(expense -> expense.getExpenseType().name()));

        List<TradingDtos.PnlExpenseGroup> groups = grouped.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    BigDecimal total = entry.getValue().stream()
                            .map(Expense::getAmountBdt)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    List<TradingDtos.PnlExpenseRow> rows = entry.getValue().stream()
                            .sorted(Comparator.comparing(Expense::getExpenseTime).reversed())
                            .map(expense -> new TradingDtos.PnlExpenseRow(
                                    expense.getId(),
                                    expense.getExpenseType().name(),
                                    expense.getExpenseTime(),
                                    expense.getAmountBdt(),
                                    expense.getCategory(),
                                    expense.getNotes(),
                                    "Expense #" + expense.getId()
                            ))
                            .toList();
                    return new TradingDtos.PnlExpenseGroup(entry.getKey(), total, rows);
                })
                .toList();

        return new TradingDtos.PnlExplainSection(
                label,
                metrics.buyBdt(),
                metrics.sellBdt(),
                metrics.grossPnlBdt(),
                "FIFO",
                metrics.longFifoRealizedPnlBdt(),
                metrics.shortCoverRealizedPnlBdt(),
                metrics.longMatchedQty(),
                metrics.longSellProceedsBdt(),
                metrics.longBuyCostBdt(),
                metrics.shortCoverQty(),
                metrics.shortSellProceedsBdt(),
                metrics.shortCoverBuyCostBdt(),
                metrics.openLongQty(),
                metrics.openLongValueBdt(),
                metrics.openShortQty(),
                metrics.openShortProceedsBdt(),
                metrics.openInstruments(),
                metrics.expenseBdt(),
                metrics.netPnlBdt(),
                groups,
                deals.stream()
                        .filter(deal -> deal.getDealType() == DealType.BUY)
                        .sorted(Comparator.comparing(TradeDeal::getDealTime).reversed())
                        .map(this::toPnlDealRow)
                        .toList(),
                deals.stream()
                        .filter(deal -> deal.getDealType() == DealType.SELL)
                        .sorted(Comparator.comparing(TradeDeal::getDealTime).reversed())
                        .map(this::toPnlDealRow)
                        .toList()
        );
    }

    private TradingDtos.PnlDealRow toPnlDealRow(TradeDeal deal) {
        return new TradingDtos.PnlDealRow(
                deal.getId(),
                deal.getDealTime(),
                deal.getDealType().name(),
                deal.getInstrumentCode().name(),
                deal.getQuantity(),
                deal.getBdtRate(),
                deal.getBdtGross(),
                deal.getParty().getName(),
                deal.getNotes(),
                "Deal #" + deal.getId()
        );
    }

    private PnlMetrics pnlMetrics(LocalDate from, LocalDate to) {
        LocalDateTime[] range = dayRange(from, to);
        LocalDateTime periodFrom = range[0];
        LocalDateTime periodTo = range[1];
        List<TradeDeal> allDealsUntilTo = dealRepo.findByDeletedFalse().stream()
                .filter(d -> !d.getDealTime().isAfter(periodTo))
                .sorted(Comparator
                        .comparing(TradeDeal::getDealTime)
                        .thenComparing(TradeDeal::getId))
                .toList();

        BigDecimal buy = BigDecimal.ZERO;
        BigDecimal sell = BigDecimal.ZERO;
        BigDecimal longRealized = BigDecimal.ZERO;
        BigDecimal shortCoverRealized = BigDecimal.ZERO;
        BigDecimal longMatchedQty = BigDecimal.ZERO;
        BigDecimal longSellProceeds = BigDecimal.ZERO;
        BigDecimal longBuyCost = BigDecimal.ZERO;
        BigDecimal shortCoverQty = BigDecimal.ZERO;
        BigDecimal shortSellProceeds = BigDecimal.ZERO;
        BigDecimal shortCoverBuyCost = BigDecimal.ZERO;

        Map<String, InstrumentState> stateByInstrument = new HashMap<>();
        for (TradeDeal deal : allDealsUntilTo) {
            String instrument = deal.getInstrumentCode().name();
            InstrumentState state = stateByInstrument.computeIfAbsent(instrument, ignored -> new InstrumentState());
            BigDecimal qty = deal.getQuantity();
            BigDecimal rate = deal.getBdtRate();

            boolean inPeriod = !deal.getDealTime().isBefore(periodFrom) && !deal.getDealTime().isAfter(periodTo);
            if (inPeriod) {
                if (deal.getDealType() == DealType.BUY) {
                    buy = buy.add(deal.getBdtGross());
                } else {
                    sell = sell.add(deal.getBdtGross());
                }
            }

            if (deal.getDealType() == DealType.BUY) {
                BigDecimal remaining = qty;
                while (remaining.compareTo(BigDecimal.ZERO) > 0 && !state.shortLots.isEmpty()) {
                    Lot shortLot = state.shortLots.peekFirst();
                    BigDecimal covered = remaining.min(shortLot.quantity());
                    BigDecimal realized = shortLot.unitRate().subtract(rate).multiply(covered);
                    if (inPeriod) {
                        shortCoverRealized = shortCoverRealized.add(realized);
                        shortCoverQty = shortCoverQty.add(covered);
                        shortSellProceeds = shortSellProceeds.add(shortLot.unitRate().multiply(covered));
                        shortCoverBuyCost = shortCoverBuyCost.add(rate.multiply(covered));
                    }
                    remaining = remaining.subtract(covered);
                    BigDecimal shortLeft = shortLot.quantity().subtract(covered);
                    state.shortLots.removeFirst();
                    if (shortLeft.compareTo(BigDecimal.ZERO) > 0) {
                        state.shortLots.addFirst(new Lot(shortLeft, shortLot.unitRate()));
                    }
                }
                if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                    state.longLots.addLast(new Lot(remaining, rate));
                }
            } else {
                BigDecimal remaining = qty;
                while (remaining.compareTo(BigDecimal.ZERO) > 0 && !state.longLots.isEmpty()) {
                    Lot longLot = state.longLots.peekFirst();
                    BigDecimal matched = remaining.min(longLot.quantity());
                    BigDecimal realized = rate.subtract(longLot.unitRate()).multiply(matched);
                    if (inPeriod) {
                        longRealized = longRealized.add(realized);
                        longMatchedQty = longMatchedQty.add(matched);
                        longSellProceeds = longSellProceeds.add(rate.multiply(matched));
                        longBuyCost = longBuyCost.add(longLot.unitRate().multiply(matched));
                    }
                    remaining = remaining.subtract(matched);
                    BigDecimal longLeft = longLot.quantity().subtract(matched);
                    state.longLots.removeFirst();
                    if (longLeft.compareTo(BigDecimal.ZERO) > 0) {
                        state.longLots.addFirst(new Lot(longLeft, longLot.unitRate()));
                    }
                }
                if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                    state.shortLots.addLast(new Lot(remaining, rate));
                }
            }
        }

        BigDecimal openLongQty = BigDecimal.ZERO;
        BigDecimal openLongValue = BigDecimal.ZERO;
        BigDecimal openShortQty = BigDecimal.ZERO;
        BigDecimal openShortProceeds = BigDecimal.ZERO;
        List<TradingDtos.PnlOpenInstrumentRow> openInstruments = new ArrayList<>();
        for (Map.Entry<String, InstrumentState> instrumentEntry : stateByInstrument.entrySet()) {
            InstrumentState state = instrumentEntry.getValue();
            BigDecimal instrumentLongQty = BigDecimal.ZERO;
            BigDecimal instrumentLongValue = BigDecimal.ZERO;
            BigDecimal instrumentShortQty = BigDecimal.ZERO;
            BigDecimal instrumentShortProceeds = BigDecimal.ZERO;
            for (Lot lot : state.longLots) {
                openLongQty = openLongQty.add(lot.quantity());
                openLongValue = openLongValue.add(lot.quantity().multiply(lot.unitRate()));
                instrumentLongQty = instrumentLongQty.add(lot.quantity());
                instrumentLongValue = instrumentLongValue.add(lot.quantity().multiply(lot.unitRate()));
            }
            for (Lot lot : state.shortLots) {
                openShortQty = openShortQty.add(lot.quantity());
                openShortProceeds = openShortProceeds.add(lot.quantity().multiply(lot.unitRate()));
                instrumentShortQty = instrumentShortQty.add(lot.quantity());
                instrumentShortProceeds = instrumentShortProceeds.add(lot.quantity().multiply(lot.unitRate()));
            }
            if (instrumentLongQty.compareTo(BigDecimal.ZERO) > 0 || instrumentShortQty.compareTo(BigDecimal.ZERO) > 0) {
                openInstruments.add(new TradingDtos.PnlOpenInstrumentRow(
                        instrumentEntry.getKey(),
                        instrumentLongQty,
                        instrumentLongValue,
                        instrumentShortQty,
                        instrumentShortProceeds
                ));
            }
        }
        openInstruments.sort(Comparator.comparing(TradingDtos.PnlOpenInstrumentRow::instrumentCode));

        BigDecimal expense = expenseRepo.findByExpenseTimeBetweenAndDeletedFalse(range[0], range[1]).stream()
                .map(Expense::getAmountBdt)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal gross = longRealized.add(shortCoverRealized);
        BigDecimal net = gross.subtract(expense);
        return new PnlMetrics(
                buy,
                sell,
                gross,
                longRealized,
                shortCoverRealized,
                longMatchedQty,
                longSellProceeds,
                longBuyCost,
                shortCoverQty,
                shortSellProceeds,
                shortCoverBuyCost,
                openLongQty,
                openLongValue,
                openShortQty,
                openShortProceeds,
                openInstruments,
                expense,
                net
        );
    }

    private BigDecimal bdt(BigDecimal value) {
        return value.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private Map<SettlementPaymentMethod, BigDecimal> settlementPaymentNet(LocalDate from, LocalDate to) {
        LocalDateTime[] range = dayRange(from, to);
        Map<SettlementPaymentMethod, BigDecimal> map = new EnumMap<>(SettlementPaymentMethod.class);
        for (Settlement settlement : settlementRepo.findBySettlementTimeBetweenAndDeletedFalse(range[0], range[1])) {
            BigDecimal signed = settlement.getDirection() == SettlementDirection.INCOMING
                    ? settlement.getBdtAmount()
                    : settlement.getBdtAmount().negate();
            map.merge(settlement.getPaymentMethod(), signed, BigDecimal::add);
        }
        return map;
    }

    private Map<SettlementPaymentMethod, BigDecimal> settlementPaymentClosing(LocalDate asOfDate) {
        Map<SettlementPaymentMethod, BigDecimal> map = new EnumMap<>(SettlementPaymentMethod.class);
        LocalDateTime cutoff = asOfDate.plusDays(1).atStartOfDay().minusNanos(1);
        map.put(SettlementPaymentMethod.CASH, netForAccountUntilActive("CASH", cutoff));
        map.put(SettlementPaymentMethod.BANK, netForAccountUntilActive("BANK", cutoff));
        map.put(SettlementPaymentMethod.CHECK, netForAccountUntilActive("CHEQUE", cutoff));
        return map;
    }

    private List<TradingDtos.InstrumentBalanceRow> instrumentBalances(LocalDate from, LocalDate to) {
        Map<String, BigDecimal> opening = instrumentQtyAt(from.minusDays(1));
        Map<String, BigDecimal> closing = instrumentQtyAt(to);
        Set<String> codes = new TreeSet<>();
        codes.addAll(opening.keySet());
        codes.addAll(closing.keySet());
        List<TradingDtos.InstrumentBalanceRow> rows = new ArrayList<>();
        for (String code : codes) {
            BigDecimal openingQty = opening.getOrDefault(code, BigDecimal.ZERO);
            BigDecimal closingQty = closing.getOrDefault(code, BigDecimal.ZERO);
            if (openingQty.compareTo(BigDecimal.ZERO) == 0 && closingQty.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            rows.add(new TradingDtos.InstrumentBalanceRow(code, openingQty, closingQty));
        }
        return rows;
    }

    private Map<String, BigDecimal> instrumentQtyAt(LocalDate asOfDate) {
        Map<String, BigDecimal> qtyByCode = new HashMap<>();
        for (TradeDeal deal : dealRepo.findByDeletedFalse()) {
            if (deal.getDealTime().toLocalDate().isAfter(asOfDate)) {
                continue;
            }
            String code = deal.getInstrumentCode().name();
            BigDecimal signedQty = deal.getDealType() == DealType.BUY
                    ? deal.getQuantity()
                    : deal.getQuantity().negate();
            qtyByCode.merge(code, signedQty, BigDecimal::add);
        }
        return qtyByCode;
    }

    private BigDecimal netForAccountUntilActive(String accountCode, LocalDateTime cutoff) {
        return ledgerRepo.findByAccountCodeAndEntryTimeLessThanEqual(accountCode, cutoff).stream()
                .filter(this::isActiveLedgerReference)
                .map(entry -> entry.getDebit().subtract(entry.getCredit()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal netForAccountBetweenActive(String accountCode, LocalDateTime from, LocalDateTime to) {
        return ledgerRepo.findByAccountCodeAndEntryTimeBetween(accountCode, from, to).stream()
                .filter(this::isActiveLedgerReference)
                .map(entry -> entry.getDebit().subtract(entry.getCredit()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal netForPrefixUntilActive(String prefix, LocalDateTime cutoff) {
        return ledgerRepo.findByAccountCodeStartingWithAndEntryTimeLessThanEqual(prefix, cutoff).stream()
                .filter(this::isActiveLedgerReference)
                .map(entry -> entry.getDebit().subtract(entry.getCredit()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal netForPrefixBetweenActive(String prefix, LocalDateTime from, LocalDateTime to) {
        return ledgerRepo.findByAccountCodeStartingWithAndEntryTimeBetween(prefix, from, to).stream()
                .filter(this::isActiveLedgerReference)
                .map(entry -> entry.getDebit().subtract(entry.getCredit()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private boolean isActiveLedgerReference(LedgerEntry entry) {
        String referenceType = entry.getReferenceType();
        Long referenceId = entry.getReferenceId();
        if ("DEAL".equals(referenceType) || "DEAL_REVERSAL".equals(referenceType)) {
            return dealRepo.findByIdAndDeletedFalse(referenceId).isPresent();
        }
        if ("SETTLEMENT".equals(referenceType) || "SETTLEMENT_REVERSAL".equals(referenceType)) {
            return settlementRepo.findByIdAndDeletedFalse(referenceId).isPresent();
        }
        if ("EXPENSE".equals(referenceType) || "EXPENSE_REVERSAL".equals(referenceType)) {
            return expenseRepo.findByIdAndDeletedFalse(referenceId).isPresent();
        }
        if ("OPENING_BALANCE".equals(referenceType)) {
            return partyRepo.findByIdAndDeletedFalse(referenceId).isPresent();
        }
        return true;
    }

    private record PnlMetrics(
            BigDecimal buyBdt,
            BigDecimal sellBdt,
            BigDecimal grossPnlBdt,
            BigDecimal longFifoRealizedPnlBdt,
            BigDecimal shortCoverRealizedPnlBdt,
            BigDecimal longMatchedQty,
            BigDecimal longSellProceedsBdt,
            BigDecimal longBuyCostBdt,
            BigDecimal shortCoverQty,
            BigDecimal shortSellProceedsBdt,
            BigDecimal shortCoverBuyCostBdt,
            BigDecimal openLongQty,
            BigDecimal openLongValueBdt,
            BigDecimal openShortQty,
            BigDecimal openShortProceedsBdt,
            List<TradingDtos.PnlOpenInstrumentRow> openInstruments,
            BigDecimal expenseBdt,
            BigDecimal netPnlBdt
    ) {}

    private record Lot(BigDecimal quantity, BigDecimal unitRate) {}

    private static class InstrumentState {
        private final Deque<Lot> longLots = new ArrayDeque<>();
        private final Deque<Lot> shortLots = new ArrayDeque<>();
    }

    private boolean containsIgnoreCase(String source, String search) {
        return source != null && source.toLowerCase(Locale.ROOT).contains(search);
    }

    private Comparator<TradingDtos.TransactionDetailRow> transactionComparator(String field, String direction) {
        Comparator<TradingDtos.TransactionDetailRow> comparator = switch (field) {
            case "amountBdt" -> Comparator.comparing(TradingDtos.TransactionDetailRow::amountBdt, Comparator.nullsLast(BigDecimal::compareTo));
            case "entryType" -> Comparator.comparing(TradingDtos.TransactionDetailRow::entryType, Comparator.nullsLast(String::compareToIgnoreCase));
            case "partyName" -> Comparator.comparing(TradingDtos.TransactionDetailRow::partyName, Comparator.nullsLast(String::compareToIgnoreCase));
            default -> Comparator.comparing(TradingDtos.TransactionDetailRow::occurredAt, Comparator.nullsLast(LocalDateTime::compareTo));
        };
        return "asc".equalsIgnoreCase(direction) ? comparator : comparator.reversed();
    }

    private UserAccount getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepo.findByUsernameAndActiveTrue(username).orElseThrow(() -> new ApiException("User not found"));
    }

    private String currentActor() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? "system" : auth.getName();
    }

    private String serializeDeal(TradeDeal deal) {
        return "id=" + deal.getId()
                + ",partyId=" + deal.getParty().getId()
                + ",dealType=" + deal.getDealType()
                + ",instrument=" + deal.getInstrumentCode()
                + ",qty=" + deal.getQuantity()
                + ",rate=" + deal.getBdtRate()
                + ",gross=" + deal.getBdtGross()
                + ",time=" + deal.getDealTime()
                + ",notes=" + deal.getNotes();
    }

    private String serializeSettlement(Settlement settlement) {
        return "id=" + settlement.getId()
                + ",partyId=" + settlement.getParty().getId()
                + ",dealId=" + (settlement.getTradeDeal() == null ? null : settlement.getTradeDeal().getId())
                + ",direction=" + settlement.getDirection()
                + ",basis=" + settlement.getBasis()
                + ",amount=" + settlement.getBdtAmount()
                + ",applied=" + settlement.getAppliedAmount()
                + ",advance=" + settlement.getAdvanceAmount()
                + ",time=" + settlement.getSettlementTime()
                + ",paymentMethod=" + settlement.getPaymentMethod()
                + ",paymentReference=" + settlement.getPaymentReference()
                + ",notes=" + settlement.getNotes();
    }

    private String serializeExpense(Expense expense) {
        return "id=" + expense.getId()
                + ",expenseType=" + expense.getExpenseType()
                + ",amount=" + expense.getAmountBdt()
                + ",time=" + expense.getExpenseTime()
                + ",category=" + expense.getCategory()
                + ",notes=" + expense.getNotes();
    }

}
