package com.doller.platform.dto;

import com.doller.platform.domain.enums.DealType;
import com.doller.platform.domain.enums.ExpenseType;
import com.doller.platform.domain.enums.InstrumentCode;
import com.doller.platform.domain.enums.SettlementBasis;
import com.doller.platform.domain.enums.SettlementDirection;
import com.doller.platform.domain.enums.SettlementPaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class TradingDtos {
    public record DealCreateRequest(@NotNull DealType dealType, @NotNull Long partyId, @NotNull InstrumentCode instrumentCode, @NotNull @DecimalMin("0.000001") BigDecimal quantity,
                                    @NotNull @DecimalMin("0.000001") BigDecimal bdtRate, @NotNull LocalDateTime dealTime, String notes) {}
    public record DealUpdateRequest(@NotNull DealType dealType, @NotNull Long partyId, @NotNull InstrumentCode instrumentCode, @NotNull @DecimalMin("0.000001") BigDecimal quantity,
                                    @NotNull @DecimalMin("0.000001") BigDecimal bdtRate, @NotNull LocalDateTime dealTime, String notes) {}
    public record DealSummary(Long id, String partyName, DealType dealType, InstrumentCode instrumentCode, BigDecimal quantity, BigDecimal bdtGross,
                              LocalDateTime dealTime, boolean lockedByDayClose) {}
    public record SettlementCreateRequest(@NotNull Long partyId, Long tradeDealId, @NotNull @DecimalMin("0.01") BigDecimal bdtAmount,
                                          @NotNull LocalDateTime settlementTime, @NotNull SettlementPaymentMethod paymentMethod,
                                          String paymentReference, String notes, boolean allowAdvance) {}
    public record SettlementUpdateRequest(@NotNull Long partyId, Long tradeDealId, @NotNull @DecimalMin("0.01") BigDecimal bdtAmount,
                                          @NotNull LocalDateTime settlementTime, @NotNull SettlementPaymentMethod paymentMethod,
                                          String paymentReference, String notes, boolean allowAdvance) {}
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
    public record ExpenseUpdateRequest(@NotNull ExpenseType expenseType, Long tradeDealId, @NotNull @DecimalMin("0.01") BigDecimal amountBdt,
                                       @NotNull LocalDateTime expenseTime, @NotBlank String category, String notes) {}
    public record DayClosePreview(LocalDate date, BigDecimal totalBuyBdt, BigDecimal totalSellBdt, BigDecimal totalExpenseBdt, BigDecimal realizedProfitLossBdt, boolean closed) {}
    public record InstrumentPosition(String instrumentCode, BigDecimal quantity, BigDecimal valuationBdt) {}
    public record DashboardResponse(
            BigDecimal receivableBdt,
            BigDecimal payableBdt,
            BigDecimal totalPositionValuationBdt,
            BigDecimal todayPnL,
            BigDecimal periodPnL,
            BigDecimal todayBuyBdt,
            BigDecimal todaySellBdt,
            BigDecimal todayGrossPnlBdt,
            BigDecimal todayExpenseBdt,
            BigDecimal todayNetPnlBdt,
            BigDecimal periodBuyBdt,
            BigDecimal periodSellBdt,
            BigDecimal periodGrossPnlBdt,
            BigDecimal periodExpenseBdt,
            BigDecimal periodNetPnlBdt,
            List<InstrumentPosition> positions
    ) {}
    public record PnlExpenseRow(
            Long expenseId,
            String expenseType,
            LocalDateTime time,
            BigDecimal amountBdt,
            String category,
            String notes,
            String referenceLabel
    ) {}
    public record PnlExpenseGroup(
            String expenseType,
            BigDecimal totalAmountBdt,
            List<PnlExpenseRow> rows
    ) {}
    public record PnlDealRow(
            Long dealId,
            LocalDateTime time,
            String dealType,
            String instrumentCode,
            BigDecimal quantity,
            BigDecimal bdtRate,
            BigDecimal bdtAmount,
            String partyName,
            String notes,
            String referenceLabel
    ) {}
    public record PnlOpenInstrumentRow(
            String instrumentCode,
            BigDecimal openLongQty,
            BigDecimal openLongValueBdt,
            BigDecimal openShortQty,
            BigDecimal openShortProceedsBdt
    ) {}
    public record PnlExplainSection(
            String label,
            BigDecimal buyBdt,
            BigDecimal sellBdt,
            BigDecimal grossPnlBdt,
            String costMethod,
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
            List<PnlOpenInstrumentRow> openInstruments,
            BigDecimal expenseBdt,
            BigDecimal netPnlBdt,
            List<PnlExpenseGroup> expenseGroups,
            List<PnlDealRow> buyRows,
            List<PnlDealRow> sellRows
    ) {}
    public record DashboardPnlExplainResponse(
            String mode,
            LocalDate periodFrom,
            LocalDate periodTo,
            PnlExplainSection today,
            PnlExplainSection period
    ) {}
    public record PartyDueRow(
            Long partyId,
            String partyName,
            String phone,
            String notes,
            BigDecimal receivableBdt,
            BigDecimal payableBdt,
            BigDecimal advanceFromPartyBdt,
            BigDecimal advanceToPartyBdt,
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
            BigDecimal settlementCashNetBdt,
            BigDecimal settlementBankNetBdt,
            BigDecimal settlementCheckNetBdt,
            BigDecimal closingCashMethodBdt,
            BigDecimal closingBankMethodBdt,
            BigDecimal closingCheckMethodBdt,
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
            List<InstrumentBalanceRow> instrumentBalances,
            List<StatementLine> lines
    ) {}
    public record InstrumentBalanceRow(
            String instrumentCode,
            BigDecimal openingQty,
            BigDecimal closingQty
    ) {}
    public record TransactionDetailRow(
            String entryType,
            Long entryId,
            LocalDateTime occurredAt,
            Long partyId,
            String partyName,
            Long tradeDealId,
            String instrumentCode,
            BigDecimal quantity,
            BigDecimal amountBdt,
            BigDecimal bdtRate,
            String directionLabel,
            String referenceLabel,
            String paymentMethod,
            String paymentReference,
            String notes,
            String expenseType,
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
    public record TransactionDealExportRow(
            Long dealId,
            LocalDate date,
            String time,
            String direction,
            String instrumentCode,
            BigDecimal quantity,
            BigDecimal bdtRate,
            BigDecimal amountBdt
    ) {}
    public record TransactionSettlementExportRow(
            Long settlementId,
            LocalDate date,
            String time,
            String direction,
            String paymentMethod,
            String paymentReference,
            Long relatedDealId,
            BigDecimal amountBdt
    ) {}
    public record TransactionDealSummary(
            long count,
            BigDecimal totalBuyBdt,
            BigDecimal totalSellBdt,
            BigDecimal netDealExposureBdt
    ) {}
    public record TransactionSettlementSummary(
            long count,
            BigDecimal totalIncomingBdt,
            BigDecimal totalOutgoingBdt,
            long linkedCount,
            long unlinkedCount
    ) {}
    public record PartyIdentity(
            Long partyId,
            String partyName,
            String phone,
            String address
    ) {}
    public record TransactionPartyExposureSummary(
            BigDecimal beforeReceivableBdt,
            BigDecimal beforePayableBdt,
            BigDecimal receivableBdt,
            BigDecimal payableBdt,
            BigDecimal netBalanceBdt,
            BigDecimal agingDueBdt
    ) {}
    public record TransactionPartyExportSection(
            PartyIdentity party,
            List<TransactionDealExportRow> deals,
            List<TransactionSettlementExportRow> settlements,
            TransactionDealSummary dealSummary,
            TransactionSettlementSummary settlementSummary,
            TransactionPartyExposureSummary exposureSummary
    ) {}
    public record TransactionExportReport(
            LocalDate from,
            LocalDate to,
            String typeFilter,
            Long partyIdFilter,
            String search,
            String sortField,
            String sortDirection,
            List<TransactionPartyExportSection> partySections,
            TransactionDealSummary grandDealSummary,
            TransactionSettlementSummary grandSettlementSummary,
            TransactionPartyExposureSummary grandExposureSummary
    ) {}
    public record PartyLedgerLine(
            String kind,
            LocalDateTime time,
            BigDecimal amount,
            String note,
            String entryType,
            Long entryId,
            LocalDateTime occurredAt,
            Long partyId,
            String partyName,
            Long tradeDealId,
            String instrumentCode,
            BigDecimal quantity,
            BigDecimal amountBdt,
            BigDecimal bdtRate,
            String directionLabel,
            String referenceLabel,
            String paymentMethod,
            String paymentReference,
            String notes,
            String expenseType,
            String category
    ) {}
    public record PartyLedgerResponse(Long partyId, String partyName, PartyBalanceSummary balances, List<PartyLedgerLine> lines) {}
    public record DayCloseResponse(LocalDate date, boolean locked, String auditRef, BigDecimal openingCash, BigDecimal closingCash, BigDecimal openingUsd, BigDecimal closingUsd, BigDecimal pnl) {}
    public record ReopenDayRequest(@NotBlank String reason) {}
}
