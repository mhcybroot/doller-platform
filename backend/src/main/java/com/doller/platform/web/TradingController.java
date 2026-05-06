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
    @PostMapping("/deals") public TradingDtos.DealResponse createDeal(@Valid @RequestBody TradingDtos.DealCreateRequest req) { return toDealResponse(service.createDeal(req)); }
    @PreAuthorize("hasRole('OWNER')")
    @PutMapping("/deals/{id}")
    public TradingDtos.DealResponse updateDeal(@PathVariable("id") Long id, @Valid @RequestBody TradingDtos.DealUpdateRequest req) { return toDealResponse(service.updateDeal(id, req)); }
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
    @PostMapping("/settlements") public TradingDtos.SettlementResponse createSettlement(@Valid @RequestBody TradingDtos.SettlementCreateRequest req) { return toSettlementResponse(service.createSettlement(req)); }
    @PreAuthorize("hasRole('OWNER')")
    @PutMapping("/settlements/{id}")
    public TradingDtos.SettlementResponse updateSettlement(@PathVariable("id") Long id, @Valid @RequestBody TradingDtos.SettlementUpdateRequest req) { return toSettlementResponse(service.updateSettlement(id, req)); }
    @PreAuthorize("hasRole('OWNER')")
    @DeleteMapping("/settlements/{id}")
    public void deleteSettlement(@PathVariable("id") Long id) { service.deleteSettlement(id); }
    @PreAuthorize("hasAnyRole('OWNER','STAFF')")
    @PostMapping("/expenses") public TradingDtos.ExpenseResponse createExpense(@Valid @RequestBody TradingDtos.ExpenseCreateRequest req) { return toExpenseResponse(service.createExpense(req)); }
    @PreAuthorize("hasRole('OWNER')")
    @PutMapping("/expenses/{id}")
    public TradingDtos.ExpenseResponse updateExpense(@PathVariable("id") Long id, @Valid @RequestBody TradingDtos.ExpenseUpdateRequest req) { return toExpenseResponse(service.updateExpense(id, req)); }
    @PreAuthorize("hasRole('OWNER')")
    @DeleteMapping("/expenses/{id}")
    public void deleteExpense(@PathVariable("id") Long id) { service.deleteExpense(id); }

    @PreAuthorize("hasRole('OWNER')")
    @GetMapping("/owner/companies")
    public List<TradingDtos.CompanyResponse> companies() {
        return service.listCompanies();
    }

    @PreAuthorize("hasRole('OWNER')")
    @PostMapping("/owner/companies")
    public TradingDtos.CompanyResponse createCompany(@Valid @RequestBody TradingDtos.CompanyCreateRequest req) {
        return service.createCompany(req);
    }

    @PreAuthorize("hasRole('OWNER')")
    @PutMapping("/owner/companies/{id}")
    public TradingDtos.CompanyResponse updateCompany(@PathVariable("id") Long id, @Valid @RequestBody TradingDtos.CompanyUpdateRequest req) {
        return service.updateCompany(id, req);
    }

    @PreAuthorize("hasRole('OWNER')")
    @DeleteMapping("/owner/companies/{id}")
    public void deleteCompany(@PathVariable("id") Long id) {
        service.deleteCompany(id);
    }

    @PreAuthorize("hasRole('OWNER')")
    @GetMapping("/owner/custom-entries")
    public TradingDtos.CustomEntryListResponse customEntries(
            @RequestParam("companyId") Long companyId,
            @RequestParam(value = "from", required = false) LocalDate from,
            @RequestParam(value = "to", required = false) LocalDate to,
            @RequestParam(value = "search", required = false) String search
    ) {
        return service.listCustomEntries(companyId, from, to, search);
    }

    @PreAuthorize("hasRole('OWNER')")
    @PostMapping("/owner/custom-entries")
    public TradingDtos.CustomEntryRow createCustomEntry(@Valid @RequestBody TradingDtos.CustomEntryCreateRequest req) {
        return service.createCustomEntry(req);
    }

    @PreAuthorize("hasRole('OWNER')")
    @PutMapping("/owner/custom-entries/{id}")
    public TradingDtos.CustomEntryRow updateCustomEntry(@PathVariable("id") Long id, @Valid @RequestBody TradingDtos.CustomEntryUpdateRequest req) {
        return service.updateCustomEntry(id, req);
    }

    @PreAuthorize("hasRole('OWNER')")
    @DeleteMapping("/owner/custom-entries/{id}")
    public void deleteCustomEntry(@PathVariable("id") Long id) {
        service.deleteCustomEntry(id);
    }

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
    public TradingDtos.PartyLedgerResponse ledger(
            @PathVariable("id") Long id,
            @RequestParam(value = "from", required = false) LocalDate from,
            @RequestParam(value = "to", required = false) LocalDate to,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "sortField", required = false) String sortField,
            @RequestParam(value = "sortDirection", required = false) String sortDirection
    ) {
        return service.partyLedger(id, from, to, type, search, sortField, sortDirection);
    }

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

    private TradingDtos.DealResponse toDealResponse(TradeDeal deal) {
        return new TradingDtos.DealResponse(
                deal.getId(),
                deal.getParty().getId(),
                deal.getParty().getName(),
                deal.getDealType().name(),
                deal.getCurrencyCode(),
                deal.getQuantity(),
                deal.getBdtRate(),
                deal.getBdtGross(),
                deal.getDealTime(),
                deal.getNotes(),
                deal.isLockedByDayClose()
        );
    }

    private TradingDtos.SettlementResponse toSettlementResponse(Settlement settlement) {
        return new TradingDtos.SettlementResponse(
                settlement.getId(),
                settlement.getParty().getId(),
                settlement.getParty().getName(),
                settlement.getTradeDeal() == null ? null : settlement.getTradeDeal().getId(),
                settlement.getDirection(),
                settlement.getBasis(),
                settlement.getBdtAmount(),
                settlement.getAppliedAmount(),
                settlement.getAdvanceAmount(),
                settlement.getPaymentMethod(),
                settlement.getPaymentReference(),
                settlement.getSettlementTime(),
                settlement.getNotes()
        );
    }

    private TradingDtos.ExpenseResponse toExpenseResponse(Expense expense) {
        return new TradingDtos.ExpenseResponse(
                expense.getId(),
                expense.getExpenseType().name(),
                expense.getTradeDeal() == null ? null : expense.getTradeDeal().getId(),
                expense.getAmountBdt(),
                expense.getExpenseTime(),
                expense.getCategory(),
                expense.getNotes()
        );
    }
}
