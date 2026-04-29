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

    @PreAuthorize("hasAnyRole('OWNER','STAFF')")
    @PostMapping("/deals") public TradeDeal createDeal(@Valid @RequestBody TradingDtos.DealCreateRequest req) { return service.createDeal(req); }
    @PreAuthorize("hasRole('OWNER')")
    @PutMapping("/deals/{id}")
    public TradeDeal updateDeal(@PathVariable("id") Long id, @Valid @RequestBody TradingDtos.DealUpdateRequest req) { return service.updateDeal(id, req); }
    @PreAuthorize("hasRole('OWNER')")
    @DeleteMapping("/deals/{id}")
    public void deleteDeal(@PathVariable("id") Long id) { service.deleteDeal(id); }
    @GetMapping("/settlements/inference")
    public TradingDtos.SettlementInferenceResponse settlementInference(
            @RequestParam("partyId") Long partyId,
            @RequestParam(value = "tradeDealId", required = false) Long tradeDealId,
            @RequestParam(value = "amount", required = false) BigDecimal amount
    ) {
        return service.settlementInference(partyId, tradeDealId, amount);
    }
    @PreAuthorize("hasAnyRole('OWNER','STAFF')")
    @PostMapping("/settlements") public Settlement createSettlement(@Valid @RequestBody TradingDtos.SettlementCreateRequest req) { return service.createSettlement(req); }
    @PreAuthorize("hasRole('OWNER')")
    @PutMapping("/settlements/{id}")
    public Settlement updateSettlement(@PathVariable("id") Long id, @Valid @RequestBody TradingDtos.SettlementUpdateRequest req) { return service.updateSettlement(id, req); }
    @PreAuthorize("hasRole('OWNER')")
    @DeleteMapping("/settlements/{id}")
    public void deleteSettlement(@PathVariable("id") Long id) { service.deleteSettlement(id); }
    @PreAuthorize("hasAnyRole('OWNER','STAFF')")
    @PostMapping("/expenses") public Expense createExpense(@Valid @RequestBody TradingDtos.ExpenseCreateRequest req) { return service.createExpense(req); }
    @PreAuthorize("hasRole('OWNER')")
    @PutMapping("/expenses/{id}")
    public Expense updateExpense(@PathVariable("id") Long id, @Valid @RequestBody TradingDtos.ExpenseUpdateRequest req) { return service.updateExpense(id, req); }
    @PreAuthorize("hasRole('OWNER')")
    @DeleteMapping("/expenses/{id}")
    public void deleteExpense(@PathVariable("id") Long id) { service.deleteExpense(id); }

    @GetMapping("/day-close/{date}") public TradingDtos.DayClosePreview preview(@PathVariable("date") LocalDate date) { return service.previewDayClose(date); }
    @PreAuthorize("hasRole('OWNER')")
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

    @GetMapping("/reports/balance-sheet")
    public TradingDtos.BalanceSheetResponse balanceSheet(
            @RequestParam(value = "mode", required = false) String mode,
            @RequestParam(value = "date", required = false) LocalDate date,
            @RequestParam(value = "month", required = false) Integer month,
            @RequestParam(value = "year", required = false) Integer year,
            @RequestParam(value = "from", required = false) LocalDate from,
            @RequestParam(value = "to", required = false) LocalDate to
    ) {
        return service.balanceSheetReport(mode, date, month, year, from, to);
    }

    @GetMapping("/reports/transactions")
    public TradingDtos.TransactionDetailsResponse transactionDetails(
            @RequestParam("from") LocalDate from,
            @RequestParam("to") LocalDate to,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "partyId", required = false) Long partyId,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "sortField", required = false) String sortField,
            @RequestParam(value = "sortDirection", required = false) String sortDirection
    ) {
        return service.transactionDetails(from, to, type, partyId, search, sortField, sortDirection);
    }

    @GetMapping("/ledgers/party/{id}")
    public TradingDtos.PartyLedgerResponse ledger(@PathVariable("id") Long id) { return service.partyLedger(id); }

    @GetMapping("/dashboard")
    public TradingDtos.DashboardResponse dashboard(@RequestParam("from") LocalDate from, @RequestParam("to") LocalDate to) { return service.dashboard(from, to); }
    @GetMapping("/dashboard/pnl-explain")
    public TradingDtos.DashboardPnlExplainResponse dashboardPnlExplain(
            @RequestParam(value = "mode", required = false) String mode,
            @RequestParam(value = "date", required = false) LocalDate date,
            @RequestParam(value = "month", required = false) Integer month,
            @RequestParam(value = "year", required = false) Integer year,
            @RequestParam(value = "from", required = false) LocalDate from,
            @RequestParam(value = "to", required = false) LocalDate to
    ) {
        String normalizedMode = mode;
        if ((normalizedMode == null || normalizedMode.isBlank()) && from != null) {
            normalizedMode = "CUSTOM";
        }
        return service.dashboardPnlExplain(normalizedMode, date, month, year, from, to);
    }

    @GetMapping("/dues/snapshot")
    public TradingDtos.DuesSnapshotResponse duesSnapshot() {
        return service.duesSnapshot();
    }
}
