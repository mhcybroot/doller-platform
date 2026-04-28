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
import java.util.List;

@RestController
public class TradingController {
    private final TradingService service;

    public TradingController(TradingService service) {
        this.service = service;
    }

    @PostMapping("/deals") public TradeDeal createDeal(@Valid @RequestBody TradingDtos.DealCreateRequest req) { return service.createDeal(req); }
    @PostMapping("/settlements") public Settlement createSettlement(@Valid @RequestBody TradingDtos.SettlementCreateRequest req) { return service.createSettlement(req); }
    @PostMapping("/expenses") public Expense createExpense(@Valid @RequestBody TradingDtos.ExpenseCreateRequest req) { return service.createExpense(req); }

    @GetMapping("/day-close/{date}") public TradingDtos.DayClosePreview preview(@PathVariable LocalDate date) { return service.previewDayClose(date); }
    @PostMapping("/day-close/{date}") public TradingDtos.DayCloseResponse confirm(@PathVariable LocalDate date) { return service.confirmDayClose(date); }

    @PreAuthorize("hasRole('OWNER')")
    @PostMapping("/day-close/{date}/reopen")
    public TradingDtos.DayCloseResponse reopen(@PathVariable LocalDate date, @Valid @RequestBody TradingDtos.ReopenDayRequest req) {
        return service.reopenDay(date, req.reason());
    }

    @GetMapping("/statements/daily")
    public List<TradingDtos.StatementLine> daily(@RequestParam LocalDate date) { return service.statementRange(date, date); }

    @GetMapping("/statements/range")
    public List<TradingDtos.StatementLine> range(@RequestParam LocalDate from, @RequestParam LocalDate to) { return service.statementRange(from, to); }

    @GetMapping("/ledgers/party/{id}")
    public TradingDtos.PartyLedgerResponse ledger(@PathVariable Long id) { return service.partyLedger(id); }

    @GetMapping("/dashboard")
    public TradingDtos.DashboardResponse dashboard(@RequestParam LocalDate from, @RequestParam LocalDate to) { return service.dashboard(from, to); }
}
