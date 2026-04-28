package com.doller.platform.web;

import com.doller.platform.domain.Expense;
import com.doller.platform.domain.Settlement;
import com.doller.platform.domain.TradeDeal;
import com.doller.platform.dto.TradingDtos;
import com.doller.platform.service.TradingService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;

@RestController
public class TradingController {
    private final TradingService service;

    public TradingController(TradingService service) {
        this.service = service;
    }

    @GetMapping("/deals")
    public List<TradingDtos.DealSummary> deals(@RequestParam(value = "partyId", required = false) Long partyId) {
        return service.listDeals(partyId);
    }

    @PostMapping("/deals") public TradeDeal createDeal(@Valid @RequestBody TradingDtos.DealCreateRequest req) { return service.createDeal(req); }
    @GetMapping("/settlements/inference")
    public TradingDtos.SettlementInferenceResponse settlementInference(
            @RequestParam("partyId") Long partyId,
            @RequestParam(value = "tradeDealId", required = false) Long tradeDealId,
            @RequestParam(value = "amount", required = false) BigDecimal amount
    ) {
        return service.settlementInference(partyId, tradeDealId, amount);
    }
    @PostMapping("/settlements") public Settlement createSettlement(@Valid @RequestBody TradingDtos.SettlementCreateRequest req) { return service.createSettlement(req); }
    @PostMapping("/expenses") public Expense createExpense(@Valid @RequestBody TradingDtos.ExpenseCreateRequest req) { return service.createExpense(req); }

    @GetMapping("/day-close/{date}") public TradingDtos.DayClosePreview preview(@PathVariable("date") LocalDate date) { return service.previewDayClose(date); }
    @PostMapping("/day-close/{date}") public TradingDtos.DayCloseResponse confirm(@PathVariable("date") LocalDate date) { return service.confirmDayClose(date); }

    @PreAuthorize("hasRole('OWNER')")
    @PostMapping("/day-close/{date}/reopen")
    public TradingDtos.DayCloseResponse reopen(@PathVariable("date") LocalDate date, @Valid @RequestBody TradingDtos.ReopenDayRequest req) {
        return service.reopenDay(date, req.reason());
    }

    @GetMapping("/statements/daily")
    public List<TradingDtos.StatementLine> daily(@RequestParam("date") LocalDate date) { return service.statementRange(date, date); }

    @GetMapping("/statements/range")
    public List<TradingDtos.StatementLine> range(@RequestParam("from") LocalDate from, @RequestParam("to") LocalDate to) { return service.statementRange(from, to); }

    @GetMapping("/ledgers/party/{id}")
    public TradingDtos.PartyLedgerResponse ledger(@PathVariable("id") Long id) { return service.partyLedger(id); }

    @GetMapping("/dashboard")
    public TradingDtos.DashboardResponse dashboard(@RequestParam("from") LocalDate from, @RequestParam("to") LocalDate to) { return service.dashboard(from, to); }
}
