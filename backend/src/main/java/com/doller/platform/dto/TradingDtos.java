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
    public record DayClosePreview(LocalDate date, BigDecimal totalBuyBdt, BigDecimal totalSellBdt, BigDecimal totalExpenseBdt, BigDecimal realizedProfitLossBdt) {}
    public record DashboardResponse(BigDecimal receivableBdt, BigDecimal payableBdt, BigDecimal usdPosition, BigDecimal todayPnL, BigDecimal periodPnL) {}
    public record StatementLine(LocalDate date, BigDecimal openingCash, BigDecimal closingCash, BigDecimal openingUsd, BigDecimal closingUsd, BigDecimal pnl) {}
    public record PartyLedgerLine(String kind, LocalDateTime time, BigDecimal amount, String note) {}
    public record PartyLedgerResponse(Long partyId, String partyName, PartyBalanceSummary balances, List<PartyLedgerLine> lines) {}
    public record DayCloseResponse(LocalDate date, boolean locked, String auditRef, BigDecimal openingCash, BigDecimal closingCash, BigDecimal openingUsd, BigDecimal closingUsd, BigDecimal pnl) {}
    public record ReopenDayRequest(@NotBlank String reason) {}
}
