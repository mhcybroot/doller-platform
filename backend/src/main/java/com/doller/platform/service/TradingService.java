package com.doller.platform.service;

import com.doller.platform.common.ApiException;
import com.doller.platform.domain.*;
import com.doller.platform.domain.enums.DealType;
import com.doller.platform.domain.enums.SettlementBasis;
import com.doller.platform.domain.enums.SettlementDirection;
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

    public List<TradingDtos.DealSummary> listDeals(Long partyId) {
        return dealRepo.findAll().stream()
                .filter(d -> partyId == null || d.getParty().getId().equals(partyId))
                .sorted(Comparator.comparing(TradeDeal::getDealTime).reversed())
                .map(d -> new TradingDtos.DealSummary(
                        d.getId(),
                        d.getParty().getName(),
                        d.getDealType(),
                        d.getUsdAmount(),
                        d.getBdtGross(),
                        d.getDealTime(),
                        d.isLockedByDayClose()
                ))
                .toList();
    }

    @Transactional
    public Settlement createSettlement(TradingDtos.SettlementCreateRequest req) {
        requireOpenDay(req.settlementTime().toLocalDate());
        Party party = partyRepo.findById(req.partyId()).orElseThrow(() -> new ApiException("Party not found"));
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
                .settlementTime(req.settlementTime())
                .notes(req.notes())
                .build());

        postSettlementLedger(st, req.settlementTime());
        auditService.log("CREATE_SETTLEMENT", "/settlements", "partyId=" + party.getId(), null, null, "settlement:" + st.getId());
        return st;
    }

    public TradingDtos.SettlementInferenceResponse settlementInference(Long partyId, Long tradeDealId, BigDecimal amount) {
        Party party = partyRepo.findById(partyId).orElseThrow(() -> new ApiException("Party not found"));
        TradeDeal deal = resolveDeal(tradeDealId, partyId);
        return toInferenceResponse(party, deal, amount == null ? BigDecimal.ZERO : amount);
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
        BigDecimal receivable = BigDecimal.ZERO;
        BigDecimal payable = BigDecimal.ZERO;
        for (Party party : partyRepo.findAll()) {
            TradingDtos.PartyBalanceSummary balances = partyBalanceSummary(party);
            receivable = receivable.add(balances.receivableBdt());
            payable = payable.add(balances.payableBdt());
        }
        var range = new LocalDateTime[]{from.atStartOfDay(), to.plusDays(1).atStartOfDay().minusNanos(1)};
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
            lines.add(new TradingDtos.PartyLedgerLine(
                    "SETTLEMENT-" + s.getDirection() + "-" + s.getBasis(),
                    s.getSettlementTime(),
                    settlementNetEffect(s),
                    s.getNotes()
            ));
        }
        lines.sort(Comparator.comparing(TradingDtos.PartyLedgerLine::time));
        return new TradingDtos.PartyLedgerResponse(p.getId(), p.getName(), partyBalanceSummary(p), lines);
    }

    public BigDecimal partyOutstanding(Long partyId) {
        TradingDtos.PartyBalanceSummary balances = partyBalanceSummary(
                partyRepo.findById(partyId).orElseThrow(() -> new ApiException("Party not found"))
        );
        return balances.netBalanceBdt();
    }

    public BigDecimal computeAgingDue(Long partyId, int olderThanDays) {
        Party p = partyRepo.findById(partyId).orElseThrow(() -> new ApiException("Party not found"));
        LocalDateTime cutoff = LocalDate.now().minusDays(olderThanDays).atStartOfDay();
        BigDecimal oldDeals = dealRepo.findAll().stream()
                .filter(d -> d.getParty().getId().equals(partyId) && d.getDealType() == DealType.SELL && d.getDealTime().isBefore(cutoff))
                .map(TradeDeal::getBdtGross).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal oldSettle = settlementRepo.findByPartyOrderBySettlementTimeAsc(p).stream()
                .filter(s -> s.getSettlementTime().isBefore(cutoff) &&
                        s.getDirection() == SettlementDirection.INCOMING &&
                        s.getBasis() == SettlementBasis.RECEIVABLE)
                .map(Settlement::getAppliedAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return oldDeals.subtract(oldSettle).max(BigDecimal.ZERO);
    }

    private TradingDtos.PartyBalanceSummary partyBalanceSummary(Party party) {
        List<TradeDeal> deals = dealRepo.findAll().stream()
                .filter(d -> d.getParty().getId().equals(party.getId()))
                .toList();
        List<Settlement> settlements = settlementRepo.findByPartyOrderBySettlementTimeAsc(party);

        BigDecimal receivable = deals.stream()
                .filter(d -> d.getDealType() == DealType.SELL)
                .map(TradeDeal::getBdtGross)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal payable = deals.stream()
                .filter(d -> d.getDealType() == DealType.BUY)
                .map(TradeDeal::getBdtGross)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal advanceFromParty = BigDecimal.ZERO;
        BigDecimal advanceToParty = BigDecimal.ZERO;

        for (Settlement settlement : settlements) {
            if (settlement.getDirection() == SettlementDirection.INCOMING) {
                if (settlement.getBasis() == SettlementBasis.RECEIVABLE) {
                    receivable = receivable.subtract(settlement.getAppliedAmount());
                } else if (settlement.getBasis() == SettlementBasis.ADVANCE_TO_PARTY) {
                    advanceToParty = advanceToParty.subtract(settlement.getAppliedAmount());
                }
                advanceFromParty = advanceFromParty.add(settlement.getAdvanceAmount());
            } else {
                if (settlement.getBasis() == SettlementBasis.PAYABLE) {
                    payable = payable.subtract(settlement.getAppliedAmount());
                } else if (settlement.getBasis() == SettlementBasis.ADVANCE_FROM_PARTY) {
                    advanceFromParty = advanceFromParty.subtract(settlement.getAppliedAmount());
                }
                advanceToParty = advanceToParty.add(settlement.getAdvanceAmount());
            }
        }

        receivable = receivable.max(BigDecimal.ZERO);
        payable = payable.max(BigDecimal.ZERO);
        advanceFromParty = advanceFromParty.max(BigDecimal.ZERO);
        advanceToParty = advanceToParty.max(BigDecimal.ZERO);
        BigDecimal aging = computeAgingDue(party.getId(), 7);
        BigDecimal net = receivable.add(advanceToParty).subtract(payable).subtract(advanceFromParty);
        return new TradingDtos.PartyBalanceSummary(receivable, payable, advanceFromParty, advanceToParty, net, aging);
    }

    private TradingDtos.SettlementInferenceResponse toInferenceResponse(Party party, TradeDeal deal, BigDecimal amount) {
        TradingDtos.PartyBalanceSummary current = partyBalanceSummary(party);
        SettlementPlan plan = inferSettlementPlan(party, deal, amount);
        TradingDtos.PartyBalanceSummary projected = applyPlan(current, plan);
        return new TradingDtos.SettlementInferenceResponse(
                party.getId(),
                deal == null ? null : deal.getId(),
                current,
                projected,
                plan.direction(),
                plan.basis(),
                plan.appliedAmount(),
                plan.advanceAmount(),
                plan.amountLabel(),
                plan.summary()
        );
    }

    private SettlementPlan inferSettlementPlan(Party party, TradeDeal deal, BigDecimal amount) {
        TradingDtos.PartyBalanceSummary current = partyBalanceSummary(party);
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

    private TradingDtos.PartyBalanceSummary applyPlan(TradingDtos.PartyBalanceSummary current, SettlementPlan plan) {
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
        BigDecimal net = receivable.add(advanceToParty).subtract(payable).subtract(advanceFromParty);
        return new TradingDtos.PartyBalanceSummary(receivable, payable, advanceFromParty, advanceToParty, net, current.agingDueBdt());
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
        TradeDeal deal = dealRepo.findById(tradeDealId).orElseThrow(() -> new ApiException("Deal not found"));
        if (!deal.getParty().getId().equals(partyId)) {
            throw new ApiException("Selected deal does not belong to this party");
        }
        return deal;
    }

    private TradeDeal latestDealForParty(Long partyId) {
        return dealRepo.findAll().stream()
                .filter(d -> d.getParty().getId().equals(partyId))
                .max(Comparator.comparing(TradeDeal::getDealTime))
                .orElse(null);
    }

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
