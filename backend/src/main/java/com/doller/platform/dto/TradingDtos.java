package com.doller.platform.dto;

import com.doller.platform.domain.enums.DealType;
import com.doller.platform.domain.enums.ExpenseType;
import com.doller.platform.domain.enums.SettlementBasis;
import com.doller.platform.domain.enums.SettlementDirection;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class TradingDtos {
    public record DealCreateRequest(@NotNull DealType dealType, @NotNull Long partyId, @NotNull @DecimalMin("0.000001") BigDecimal usdAmount,
                                    @NotNull @DecimalMin("0.000001") BigDecimal bdtRate, @NotNull LocalDateTime dealTime, String notes) {}
    public record DealSummary(Long id, String partyName, DealType dealType, BigDecimal usdAmount, BigDecimal bdtGross,
                              LocalDateTime dealTime, boolean lockedByDayClose) {}
    public record SettlementCreateRequest(@NotNull Long partyId, Long tradeDealId, @NotNull @DecimalMin("0.01") BigDecimal bdtAmount,
                                          @NotNull LocalDateTime settlementTime, String notes, boolean allowAdvance) {}
    public record PartyBalanceSummary(BigDecimal receivableBdt, BigDecimal payableBdt, BigDecimal advanceFromPartyBdt,
                                      BigDecimal advanceToPartyBdt, BigDecimal netBalanceBdt, BigDecimal agingDueBdt) {}
    public record SettlementInferenceResponse(
            Long partyId,
            Long tradeDealId,
            PartyBalanceSummary current,
            PartyBalanceSummary projected,
            SettlementDirection direction,
            SettlementBasis basis,
            BigDecimal appliedAmount,
            BigDecimal advanceAmount,
            String amountLabel,
            String summary
    ) {}
    public record ExpenseCreateRequest(@NotNull ExpenseType expenseType, Long tradeDealId, @NotNull @DecimalMin("0.01") BigDecimal amountBdt,
                                       @NotNull LocalDateTime expenseTime, @NotBlank String category, String notes) {}
    public record DayClosePreview(LocalDate date, BigDecimal totalBuyBdt, BigDecimal totalSellBdt, BigDecimal totalExpenseBdt, BigDecimal realizedProfitLossBdt, boolean closed) {}
    public record DashboardResponse(BigDecimal receivableBdt, BigDecimal payableBdt, BigDecimal usdPosition, BigDecimal todayPnL, BigDecimal periodPnL) {}
    public record PartyDueRow(
            Long partyId,
            String partyName,
            String phone,
            String notes,
            BigDecimal receivableBdt,
            BigDecimal payableBdt,
            BigDecimal netBdt,
            LocalDateTime lastActivityAt
    ) {}
    public record DuesSnapshotResponse(
            BigDecimal totalReceivableBdt,
            BigDecimal totalPayableBdt,
            BigDecimal grossBdt,
            BigDecimal netBdt,
            List<PartyDueRow> rows
    ) {}
    public record StatementLine(
            LocalDate date,
            BigDecimal openingCash,
            BigDecimal closingCash,
            BigDecimal openingUsd,
            BigDecimal closingUsd,
            BigDecimal openingReceivableBdt,
            BigDecimal closingReceivableBdt,
            BigDecimal openingPayableBdt,
            BigDecimal closingPayableBdt,
            BigDecimal openingAdvanceFromPartyBdt,
            BigDecimal closingAdvanceFromPartyBdt,
            BigDecimal openingAdvanceToPartyBdt,
            BigDecimal closingAdvanceToPartyBdt,
            BigDecimal openingAgingBdt,
            BigDecimal closingAgingBdt,
            BigDecimal pnl
    ) {}
    public record BalanceSheetResponse(
            String mode,
            LocalDate from,
            LocalDate to,
            BigDecimal openingCash,
            BigDecimal closingCash,
            BigDecimal openingUsd,
            BigDecimal closingUsd,
            BigDecimal openingReceivableBdt,
            BigDecimal closingReceivableBdt,
            BigDecimal openingPayableBdt,
            BigDecimal closingPayableBdt,
            BigDecimal openingAdvanceFromPartyBdt,
            BigDecimal closingAdvanceFromPartyBdt,
            BigDecimal openingAdvanceToPartyBdt,
            BigDecimal closingAdvanceToPartyBdt,
            BigDecimal openingAgingBdt,
            BigDecimal closingAgingBdt,
            BigDecimal totalPnl,
            List<StatementLine> lines
    ) {}
    public record TransactionDetailRow(
            String entryType,
            Long entryId,
            LocalDateTime occurredAt,
            Long partyId,
            String partyName,
            BigDecimal amountBdt,
            BigDecimal usdAmount,
            BigDecimal bdtRate,
            String directionLabel,
            String referenceLabel,
            String notes,
            String category
    ) {}
    public record TransactionDetailsResponse(
            LocalDate from,
            LocalDate to,
            String typeFilter,
            Long partyId,
            String search,
            String sortField,
            String sortDirection,
            List<TransactionDetailRow> rows
    ) {}
    public record PartyLedgerLine(String kind, LocalDateTime time, BigDecimal amount, String note) {}
    public record PartyLedgerResponse(Long partyId, String partyName, PartyBalanceSummary balances, List<PartyLedgerLine> lines) {}
    public record DayCloseResponse(LocalDate date, boolean locked, String auditRef, BigDecimal openingCash, BigDecimal closingCash, BigDecimal openingUsd, BigDecimal closingUsd, BigDecimal pnl) {}
    public record ReopenDayRequest(@NotBlank String reason) {}
}
