import 'dart:io';

import 'package:flutter/material.dart';
import 'package:share_plus/share_plus.dart';
import 'package:syncfusion_flutter_pdfviewer/pdfviewer.dart';

import '../../app/app_theme.dart';
import '../../shared/models/auth_models.dart';
import '../../shared/models/domain_models.dart';
import '../../shared/instruments/instrument_labels.dart';
import '../../shared/services/api_client.dart';
import '../../shared/services/doller_repository.dart';
import '../../shared/widgets/finance_widgets.dart';

class StatementsScreen extends StatefulWidget {
  const StatementsScreen({
    super.key,
    required this.repository,
    required this.session,
    this.initialTab = 0,
  });

  final DollerRepository repository;
  final AuthSession session;
  final int initialTab;

  @override
  State<StatementsScreen> createState() => _StatementsScreenState();
}

class _StatementsScreenState extends State<StatementsScreen> {
  late int _tab;

  @override
  void initState() {
    super.initState();
    _tab = widget.initialTab == 1 ? 1 : 0;
  }

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.all(20),
      children: [
        if (_tab == 0)
          _BalanceSheetTab(repository: widget.repository)
        else
          _TransactionDetailsTab(
              repository: widget.repository, session: widget.session),
      ],
    );
  }
}

class _BalanceSheetTab extends StatefulWidget {
  const _BalanceSheetTab({
    required this.repository,
  });

  final DollerRepository repository;

  @override
  State<_BalanceSheetTab> createState() => _BalanceSheetTabState();
}

class _BalanceSheetTabState extends State<_BalanceSheetTab> {
  String _mode = 'DAILY';
  late DateTime _selectedDate;
  late DateTime _from;
  late DateTime _to;
  late int _selectedMonth;
  late int _selectedYear;
  BalanceSheetModel? _report;
  bool _loading = true;
  bool _networkError = false;

  @override
  void initState() {
    super.initState();
    final now = DateTime.now();
    _selectedDate = now;
    _from = now;
    _to = now;
    _selectedMonth = now.month;
    _selectedYear = now.year;
    _load();
  }

  Future<void> _load() async {
    setState(() => _loading = true);
    try {
      final report = await widget.repository.balanceSheet(
        mode: _mode,
        date: _mode == 'DAILY' ? _selectedDate : null,
        month: _mode == 'MONTHLY' ? _selectedMonth : null,
        year: _mode == 'MONTHLY' || _mode == 'YEARLY' ? _selectedYear : null,
        from: _mode == 'CUSTOM' ? _from : null,
        to: _mode == 'CUSTOM' ? _to : null,
      );
      if (!mounted) {
        return;
      }
      setState(() {
        _report = report;
        _networkError = false;
        _loading = false;
      });
    } on ApiException catch (error) {
      if (!mounted) {
        return;
      }
      showAppMessage(context, error.message, isError: true);
      setState(() {
        _report = null;
        _networkError = error.isNetworkError;
        _loading = false;
      });
    }
  }

  Future<void> _pickDate(
      {required bool fromField,
      bool yearOnly = false,
      bool monthPicker = false}) async {
    final initial = yearOnly
        ? DateTime(_selectedYear)
        : monthPicker
            ? DateTime(_selectedYear, _selectedMonth)
            : (fromField ? _from : _to);
    final selected = await showDatePicker(
      context: context,
      firstDate: DateTime(2020),
      lastDate: DateTime(2100),
      initialDate: initial,
    );
    if (selected == null) {
      return;
    }
    setState(() {
      if (yearOnly) {
        _selectedYear = selected.year;
      } else if (monthPicker) {
        _selectedMonth = selected.month;
        _selectedYear = selected.year;
      } else if (_mode == 'DAILY') {
        _selectedDate = selected;
      } else if (fromField) {
        _from = selected;
      } else {
        _to = selected;
      }
    });
    await _load();
  }

  Future<void> _export() async {
    try {
      await widget.repository.exportAndShareBalanceSheet(
        mode: _mode,
        date: _mode == 'DAILY' ? _selectedDate : null,
        month: _mode == 'MONTHLY' ? _selectedMonth : null,
        year: _mode == 'MONTHLY' || _mode == 'YEARLY' ? _selectedYear : null,
        from: _mode == 'CUSTOM' ? _from : null,
        to: _mode == 'CUSTOM' ? _to : null,
      );
    } on ApiException catch (error) {
      if (!mounted) {
        return;
      }
      showAppMessage(context, error.message, isError: true);
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) {
      return const Center(child: CircularProgressIndicator());
    }
    final report = _report;
    if (report == null) {
      return EmptyStateCard(
        title: _networkError ? 'No internet connection' : 'No report data',
        message: _networkError
            ? 'Please check your network and try again.'
            : 'Try another reporting period.',
      );
    }
    return Column(
      children: [
        FinanceSection(
          title: 'Period Mode',
          trailing: IconButton(
            onPressed: _export,
            icon: const Icon(Icons.picture_as_pdf_outlined),
            tooltip: 'Export PDF',
          ),
          child: Column(
            children: [
              SegmentedButton<String>(
                segments: const [
                  ButtonSegment(value: 'DAILY', label: Text('Daily')),
                  ButtonSegment(value: 'MONTHLY', label: Text('Monthly')),
                  ButtonSegment(value: 'YEARLY', label: Text('Yearly')),
                  ButtonSegment(value: 'CUSTOM', label: Text('Range')),
                ],
                selected: {_mode},
                onSelectionChanged: (value) async {
                  setState(() => _mode = value.first);
                  await _load();
                },
              ),
              const SizedBox(height: 14),
              if (_mode == 'DAILY')
                OutlinedButton(
                  onPressed: () => _pickDate(fromField: true),
                  child: Text('Date ${formatDate(_selectedDate)}'),
                ),
              if (_mode == 'MONTHLY')
                Row(
                  children: [
                    Expanded(
                      child: OutlinedButton(
                        onPressed: () =>
                            _pickDate(fromField: true, monthPicker: true),
                        child: Text(
                            'Month ${_selectedMonth.toString().padLeft(2, '0')}'),
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: OutlinedButton(
                        onPressed: () =>
                            _pickDate(fromField: true, yearOnly: true),
                        child: Text('Year $_selectedYear'),
                      ),
                    ),
                  ],
                ),
              if (_mode == 'YEARLY')
                OutlinedButton(
                  onPressed: () => _pickDate(fromField: true, yearOnly: true),
                  child: Text('Year $_selectedYear'),
                ),
              if (_mode == 'CUSTOM')
                Row(
                  children: [
                    Expanded(
                      child: OutlinedButton(
                        onPressed: () => _pickDate(fromField: true),
                        child: Text('From ${formatDate(_from)}'),
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: OutlinedButton(
                        onPressed: () => _pickDate(fromField: false),
                        child: Text('To ${formatDate(_to)}'),
                      ),
                    ),
                  ],
                ),
            ],
          ),
        ),
        const SizedBox(height: 16),
        FinanceSection(
          title: 'Balance Sheet Summary',
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('Liquidity', style: Theme.of(context).textTheme.titleSmall),
              const SizedBox(height: 12),
              Wrap(
                spacing: 10,
                runSpacing: 10,
                children: [
                  _summaryPill('Opening Balance', formatBdt(report.openingCash),
                      BalancePillTone.neutral),
                  _summaryPill('Closing Balance', formatBdt(report.closingCash),
                      BalancePillTone.neutral),
                ],
              ),
              const SizedBox(height: 12),
              Text('Closing Balance By Account',
                  style: Theme.of(context).textTheme.titleSmall),
              const SizedBox(height: 12),
              Wrap(
                spacing: 10,
                runSpacing: 10,
                children: [
                  _summaryPill(
                      'Cash Closing',
                      formatBdt(report.closingCashMethodBdt),
                      BalancePillTone.neutral),
                  _summaryPill(
                      'Bank Closing',
                      formatBdt(report.closingBankMethodBdt),
                      BalancePillTone.neutral),
                  _summaryPill(
                      'Cheque Closing',
                      formatBdt(report.closingCheckMethodBdt),
                      BalancePillTone.neutral),
                ],
              ),
              if (report.instrumentBalances.isNotEmpty) ...[
                const SizedBox(height: 12),
                Text('Multi-Currency Positions',
                    style: Theme.of(context).textTheme.titleSmall),
                const SizedBox(height: 12),
                Wrap(
                  spacing: 10,
                  runSpacing: 10,
                  children: report.instrumentBalances
                      .map(
                        (row) => _summaryPill(
                          instrumentDisplayName(row.instrumentCode),
                          _formatFxFlow(row.openingQty, row.closingQty),
                          BalancePillTone.neutral,
                        ),
                      )
                      .toList(),
                ),
              ],
              const SizedBox(height: 12),
              Text('Exposure', style: Theme.of(context).textTheme.titleSmall),
              const SizedBox(height: 12),
              Wrap(
                spacing: 10,
                runSpacing: 10,
                children: [
                  _summaryPill(
                      'Opening Receivable',
                      formatBdt(report.openingReceivableBdt),
                      BalancePillTone.receivable),
                  _summaryPill(
                      'Closing Receivable',
                      formatBdt(report.closingReceivableBdt),
                      BalancePillTone.receivable),
                  _summaryPill(
                      'Opening Payable',
                      formatBdt(report.openingPayableBdt),
                      BalancePillTone.payable),
                  _summaryPill(
                      'Closing Payable',
                      formatBdt(report.closingPayableBdt),
                      BalancePillTone.payable),
                ],
              ),
              const SizedBox(height: 12),
              Text('Risk', style: Theme.of(context).textTheme.titleSmall),
              const SizedBox(height: 12),
              Wrap(
                spacing: 10,
                runSpacing: 10,
                children: [
                  _summaryPill('Opening Aging',
                      formatBdt(report.openingAgingBdt), BalancePillTone.aging),
                  _summaryPill('Closing Aging',
                      formatBdt(report.closingAgingBdt), BalancePillTone.aging),
                ],
              ),
              const SizedBox(height: 12),
              Text('Performance',
                  style: Theme.of(context).textTheme.titleSmall),
              const SizedBox(height: 12),
              GestureDetector(
                onTap: () => _showBalanceSummaryExplanation(
                    'Total P/L', formatBdt(report.totalPnl)),
                child: MetricCard(
                  label: 'Total P/L',
                  value: formatBdt(report.totalPnl),
                  caption:
                      '${formatDate(report.from)} to ${formatDate(report.to)}',
                  positive: report.totalPnl >= 0,
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 16),
        if (report.lines.isEmpty)
          const EmptyStateCard(
            title: 'No balance sheet lines',
            message: 'No transactions found for the selected period.',
          )
        else
          FinanceSection(
            title: 'Balance Sheet Lines',
            child: Column(
              children: report.lines
                  .map(
                    (line) => Container(
                      margin: const EdgeInsets.only(bottom: 12),
                      decoration: BoxDecoration(
                        borderRadius: BorderRadius.circular(22),
                        border: Border.all(color: AppTheme.border),
                        color: Colors.white,
                      ),
                      child: ExpansionTile(
                        tilePadding: const EdgeInsets.symmetric(
                            horizontal: 16, vertical: 8),
                        childrenPadding:
                            const EdgeInsets.fromLTRB(16, 0, 16, 16),
                        title: Text(formatDate(line.date)),
                        subtitle: Text(
                          'Cash ${formatBdt(line.openingCash)} -> ${formatBdt(line.closingCash)} • '
                          'FX ${line.openingUsd.toStringAsFixed(2)} -> ${line.closingUsd.toStringAsFixed(2)}',
                        ),
                        trailing: Text(
                          formatBdt(line.pnl),
                          style: TextStyle(
                            fontWeight: FontWeight.w700,
                            color: line.pnl >= 0
                                ? Colors.green.shade700
                                : Colors.red.shade700,
                          ),
                        ),
                        children: [
                          Wrap(
                            spacing: 10,
                            runSpacing: 10,
                            children: [
                              _reportPill(
                                  'Open Receivable',
                                  formatBdt(line.openingReceivableBdt),
                                  BalancePillTone.receivable),
                              _reportPill(
                                  'Close Receivable',
                                  formatBdt(line.closingReceivableBdt),
                                  BalancePillTone.receivable),
                              _reportPill(
                                  'Open Payable',
                                  formatBdt(line.openingPayableBdt),
                                  BalancePillTone.payable),
                              _reportPill(
                                  'Close Payable',
                                  formatBdt(line.closingPayableBdt),
                                  BalancePillTone.payable),
                              _reportPill(
                                  'Open Aging',
                                  formatBdt(line.openingAgingBdt),
                                  BalancePillTone.aging),
                              _reportPill(
                                  'Close Aging',
                                  formatBdt(line.closingAgingBdt),
                                  BalancePillTone.aging),
                            ],
                          ),
                        ],
                      ),
                    ),
                  )
                  .toList(),
            ),
          ),
      ],
    );
  }

  String _formatQty(double value) => value.toStringAsFixed(2);

  String _formatFxFlow(double opening, double closing) {
    return 'Open: ${_formatQty(opening)} amt\nClose: ${_formatQty(closing)} amt';
  }

  Widget _summaryPill(String label, String value, BalancePillTone tone) {
    return GestureDetector(
      onTap: () => _showBalanceSummaryExplanation(label, value),
      child: _reportPill(label, value, tone),
    );
  }

  void _showBalanceSummaryExplanation(String title, String value) {
    final (what, why, howNow, changeWhen) = _balanceExplain(title);
    showDialog<void>(
      context: context,
      builder: (_) => AlertDialog(
        title: Text(title),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('এখনের মান: $value'),
            const SizedBox(height: 10),
            Text('এটা কী:\n$what'),
            const SizedBox(height: 10),
            Text('এই অংক কেন দেখাচ্ছে:\n$why'),
            const SizedBox(height: 10),
            Text('এখনের মান কীভাবে হিসাব হচ্ছে:\n$howNow'),
            const SizedBox(height: 10),
            Text('কখন পরিবর্তন হবে:\n$changeWhen'),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text('ঠিক আছে'),
          ),
        ],
      ),
    );
  }

  (String, String, String, String) _balanceExplain(String title) {
    if (title.contains('Balance')) {
      return (
        'মোট টাকার ব্যালেন্স (BDT)।',
        'সব active deal/settlement/expense এর net effect থেকে এই ব্যালেন্স আসে।',
        'Opening = period শুরুর আগের closing balance, Closing = period শেষ পর্যন্ত updated balance।',
        'নতুন transaction যোগ/এডিট/ডিলিট বা period বদলালে মান পরিবর্তন হবে।'
      );
    }
    if (title.contains('FX') || title.contains('USD')) {
      return (
        'Foreign currency quantity summary (unit) দেখায়।',
        'BUY হলে quantity বাড়ে, SELL হলে কমে।',
        'Currency inventory net quantity থেকে opening/closing হিসাব হয়।',
        'FX deal পরিবর্তন বা period বদলালে মান পরিবর্তন হবে।'
      );
    }
    if (title == 'Cash Closing' ||
        title == 'Bank Closing' ||
        title == 'Cheque Closing') {
      return (
        'নির্বাচিত period শেষ হওয়া পর্যন্ত ঐ account-এর closing balance।',
        'All active ledger impact (deal + settlement + expense) থেকে account-wise এই balance আসে।',
        'Formula: Account Closing = cumulative debit - cumulative credit (as of period end)।',
        'যে কোনো transaction add/edit/delete বা period change হলে মান বদলাবে।'
      );
    }
    if (title.contains('Receivable')) {
      return (
        'কাস্টমারের কাছে পাওনা বাকি টাকা।',
        'SELL/settlement/opening balance মিলিয়ে যেটা এখনো পাওয়া বাকি, সেটাই receivable।',
        'Party-wise receivable যোগ করে report total দেখানো হয়।',
        'SELL বাড়লে বা incoming settlement কম/বেশি হলে মান বদলাবে।'
      );
    }
    if (title.contains('Payable')) {
      return (
        'সাপ্লায়ারকে দেওয়ার বাকি টাকা।',
        'BUY/settlement/opening balance থেকে outstanding payable হিসাব হয়।',
        'সব party payable যোগ করে opening/closing payable দেখায়।',
        'BUY, outgoing settlement, opening adjustments বা range change হলে বদলাবে।'
      );
    }
    if (title.contains('Advance')) {
      return (
        'Due ছাড়াও অগ্রিম দেওয়া/নেওয়া টাকার balance।',
        'Settlement due-এর বেশি/কম apply হলে advance-in বা advance-out তৈরি হয়।',
        'Opening/closing advance party-wise ledger net থেকে aggregate করা হয়।',
        'Advance-related settlement যোগ/এডিট/ডিলিট করলে এটি পরিবর্তন হবে।'
      );
    }
    if (title.contains('Aging')) {
      return (
        'পুরনো বকেয়া receivable যেটা এখনো ক্লিয়ার হয়নি।',
        'SELL due থেকে incoming settlement cover বাদ দিয়ে aging outstanding হিসাব হয়।',
        'Party-level aging যোগফল opening/closing aging metric এ দেখানো হয়।',
        'পুরনো due settle হলে কমে, নতুন unpaid receivable যোগ হলে বাড়ে।'
      );
    }
    return (
      'নির্বাচিত সময়ের মোট লাভ/ক্ষতি (Net P/L)।',
      'Trading gross P/L থেকে expense বাদ দিয়ে final net amount দেখায়।',
      'Formula: Net P/L = Period Gross P/L - Period Expense (live recompute)।',
      'Deal/expense update, settlement impact, বা period change হলে এই মান বদলাবে।'
    );
  }
}

Widget _reportPill(String label, String value, BalancePillTone tone,
    {VoidCallback? onTap}) {
  return SizedBox(
    width: 150,
    child: InkWell(
      borderRadius: BorderRadius.circular(18),
      onTap: onTap,
      child: BalancePill(
        label: label,
        value: value,
        tone: tone,
      ),
    ),
  );
}

class _TransactionDetailsTab extends StatefulWidget {
  const _TransactionDetailsTab({
    required this.repository,
    required this.session,
  });

  final DollerRepository repository;
  final AuthSession session;

  @override
  State<_TransactionDetailsTab> createState() => _TransactionDetailsTabState();
}

class _TransactionDetailsTabState extends State<_TransactionDetailsTab> {
  final _searchController = TextEditingController();
  late DateTime _from;
  late DateTime _to;
  String _type = '';
  int? _partyId;
  String _sortField = 'occurredAt';
  String _sortDirection = 'desc';
  List<PartyModel> _parties = const [];
  TransactionDetailsModel? _details;
  bool _loading = true;
  bool _networkError = false;

  @override
  void initState() {
    super.initState();
    final now = DateTime.now();
    _from = DateTime(now.year, now.month, 1);
    _to = now;
    _load();
  }

  Future<void> _load() async {
    setState(() => _loading = true);
    try {
      final parties = await widget.repository.listParties();
      final details = await widget.repository.transactionDetails(
        from: _from,
        to: _to,
        type: _type,
        partyId: _partyId,
        search: _searchController.text,
        sortField: _sortField,
        sortDirection: _sortDirection,
      );
      if (!mounted) {
        return;
      }
      setState(() {
        _parties = parties;
        _details = details;
        _networkError = false;
        _loading = false;
      });
    } on ApiException catch (error) {
      if (!mounted) {
        return;
      }
      showAppMessage(context, error.message, isError: true);
      setState(() {
        _parties = const [];
        _details = null;
        _networkError = error.isNetworkError;
        _loading = false;
      });
    }
  }

  Future<void> _pickRange(bool fromField) async {
    final selected = await showDatePicker(
      context: context,
      firstDate: DateTime(2020),
      lastDate: DateTime(2100),
      initialDate: fromField ? _from : _to,
    );
    if (selected == null) return;
    setState(() {
      if (fromField) {
        _from = selected;
      } else {
        _to = selected;
      }
    });
    await _load();
  }

  Future<void> _export() async {
    try {
      String? selectedPartyName;
      if (_partyId != null) {
        for (final party in _parties) {
          if (party.id == _partyId) {
            selectedPartyName = party.name;
            break;
          }
        }
      }
      final result = await widget.repository.exportTransactionDetailsPdf(
        from: _from,
        to: _to,
        type: _type,
        partyId: _partyId,
        fallbackPartyName: selectedPartyName,
        search: _searchController.text,
        sortField: _sortField,
        sortDirection: _sortDirection,
      );
      if (!mounted) return;
      await Navigator.of(context).push(
        MaterialPageRoute(
          builder: (_) => _PdfPreviewScreen(
            filePath: result.file.path,
            fileName: result.filename,
          ),
        ),
      );
    } on ApiException catch (error) {
      if (!mounted) return;
      showAppMessage(context, error.message, isError: true);
    }
  }

  bool _canMutate(TransactionDetailRowModel row) {
    return widget.session.isOwner &&
        (row.entryType == 'DEAL' ||
            row.entryType == 'SETTLEMENT' ||
            row.entryType == 'EXPENSE');
  }

  Future<void> _deleteRow(TransactionDetailRowModel row) async {
    if (row.entryType == 'OPENING_BALANCE') {
      showAppMessage(context, 'Opening balance entries cannot be deleted.',
          isError: true);
      return;
    }
    final confirm = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Delete Transaction'),
        content: Text('Delete ${row.referenceLabel ?? row.entryType}?'),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(context, false),
              child: const Text('Cancel')),
          ElevatedButton(
              onPressed: () => Navigator.pop(context, true),
              child: const Text('Delete')),
        ],
      ),
    );
    if (confirm != true) return;
    try {
      if (row.entryType == 'DEAL') {
        await widget.repository.deleteDeal(row.entryId);
      } else if (row.entryType == 'SETTLEMENT') {
        await widget.repository.deleteSettlement(row.entryId);
      } else if (row.entryType == 'EXPENSE') {
        await widget.repository.deleteExpense(row.entryId);
      }
      if (!mounted) return;
      showAppMessage(context, 'Transaction deleted');
      await _load();
    } on ApiException catch (error) {
      if (!mounted) return;
      showAppMessage(context, error.message, isError: true);
    }
  }

  Future<void> _editRow(TransactionDetailRowModel row) async {
    if (row.entryType == 'OPENING_BALANCE') {
      showAppMessage(context, 'Opening balance entries are not editable.',
          isError: true);
      return;
    }
    if (row.entryType == 'DEAL') {
      await _editDeal(row);
      return;
    }
    if (row.entryType == 'SETTLEMENT') {
      await _editSettlement(row);
      return;
    }
    if (row.entryType == 'EXPENSE') {
      await _editExpense(row);
    }
  }

  Future<void> _editDeal(TransactionDetailRowModel row) async {
    if (row.partyId == null ||
        row.instrumentCode == null ||
        row.quantity == null ||
        row.bdtRate == null ||
        row.directionLabel == null) {
      showAppMessage(context, 'This deal row is missing required data.',
          isError: true);
      return;
    }
    final qty = TextEditingController(text: row.quantity!.toString());
    final rate = TextEditingController(text: row.bdtRate!.toString());
    final notes = TextEditingController(text: row.notes ?? '');
    int selectedPartyId = row.partyId!;
    String selectedInstrument = row.instrumentCode!;
    String dealType =
        row.directionLabel!.toUpperCase().contains('SELL') ? 'SELL' : 'BUY';
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      builder: (context) {
        return StatefulBuilder(
          builder: (context, setModalState) => Padding(
            padding: EdgeInsets.fromLTRB(
              20,
              24,
              20,
              MediaQuery.of(context).viewInsets.bottom + 24,
            ),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Text('Edit Deal',
                    style: Theme.of(context).textTheme.titleLarge),
                const SizedBox(height: 12),
                DropdownButtonFormField<String>(
                  value: dealType,
                  items: const [
                    DropdownMenuItem(value: 'BUY', child: Text('BUY')),
                    DropdownMenuItem(value: 'SELL', child: Text('SELL')),
                  ],
                  onChanged: (value) =>
                      setModalState(() => dealType = value ?? 'BUY'),
                  decoration: const InputDecoration(labelText: 'Deal Type'),
                ),
                const SizedBox(height: 12),
                DropdownButtonFormField<int>(
                  value: selectedPartyId,
                  isExpanded: true,
                  items: _parties
                      .map((party) => DropdownMenuItem<int>(
                            value: party.id,
                            child: Text(party.name),
                          ))
                      .toList(),
                  onChanged: (value) => setModalState(
                      () => selectedPartyId = value ?? selectedPartyId),
                  decoration: const InputDecoration(labelText: 'Party'),
                ),
                const SizedBox(height: 12),
                DropdownButtonFormField<String>(
                  value: selectedInstrument,
                  items: supportedInstrumentCodes
                      .map((code) => DropdownMenuItem<String>(
                            value: code,
                            child: Text(instrumentDisplayName(code)),
                          ))
                      .toList(),
                  onChanged: (value) => setModalState(
                      () => selectedInstrument = value ?? selectedInstrument),
                  decoration: const InputDecoration(labelText: 'Instrument'),
                ),
                const SizedBox(height: 12),
                TextField(
                    controller: qty,
                    keyboardType: TextInputType.number,
                    decoration: const InputDecoration(labelText: 'Amount')),
                const SizedBox(height: 12),
                TextField(
                    controller: rate,
                    keyboardType: TextInputType.number,
                    decoration: const InputDecoration(labelText: 'Rate')),
                const SizedBox(height: 12),
                TextField(
                    controller: notes,
                    decoration: const InputDecoration(labelText: 'Notes')),
                const SizedBox(height: 16),
                ElevatedButton(
                  onPressed: () async {
                    try {
                      await widget.repository.updateDeal(
                        id: row.entryId,
                        dealType: dealType,
                        partyId: selectedPartyId,
                        instrumentCode: selectedInstrument,
                        quantity: double.parse(qty.text),
                        bdtRate: double.parse(rate.text),
                        dealTime: row.occurredAt,
                        notes: notes.text.trim(),
                      );
                      if (!context.mounted) return;
                      Navigator.pop(context);
                    } on ApiException catch (error) {
                      showAppMessage(context, error.message, isError: true);
                    } on FormatException {
                      showAppMessage(context, 'Enter valid numbers',
                          isError: true);
                    }
                  },
                  child: const Text('Update Deal'),
                ),
              ],
            ),
          ),
        );
      },
    );
    await _load();
  }

  Future<void> _editSettlement(TransactionDetailRowModel row) async {
    if (row.partyId == null) {
      showAppMessage(context, 'This settlement row is missing party data.',
          isError: true);
      return;
    }
    final amount = TextEditingController(text: row.amountBdt.toString());
    final paymentRef = TextEditingController(text: row.paymentReference ?? '');
    final notes = TextEditingController(text: row.notes ?? '');
    bool allowAdvance = row.directionLabel?.contains(' / NONE') ?? false;
    int selectedPartyId = row.partyId!;
    int? selectedDealId = row.tradeDealId;
    List<DealSummary> partyDeals = const [];
    try {
      partyDeals = await widget.repository.listDeals(partyId: selectedPartyId);
    } on ApiException {}
    String paymentMethod = (row.paymentMethod ?? 'CASH').toUpperCase();
    if (paymentMethod != 'BANK' &&
        paymentMethod != 'CHECK' &&
        paymentMethod != 'CASH') {
      paymentMethod = 'CASH';
    }
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      builder: (context) {
        return StatefulBuilder(
          builder: (context, setModalState) => Padding(
            padding: EdgeInsets.fromLTRB(
              20,
              24,
              20,
              MediaQuery.of(context).viewInsets.bottom + 24,
            ),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Text('Edit Settlement',
                    style: Theme.of(context).textTheme.titleLarge),
                const SizedBox(height: 12),
                TextField(
                    controller: amount,
                    keyboardType: TextInputType.number,
                    decoration:
                        const InputDecoration(labelText: 'Amount (BDT)')),
                const SizedBox(height: 12),
                DropdownButtonFormField<int>(
                  value: selectedPartyId,
                  isExpanded: true,
                  items: _parties
                      .map((party) => DropdownMenuItem<int>(
                            value: party.id,
                            child: Text(party.name),
                          ))
                      .toList(),
                  onChanged: (value) async {
                    if (value == null) return;
                    final deals =
                        await widget.repository.listDeals(partyId: value);
                    setModalState(() {
                      selectedPartyId = value;
                      partyDeals = deals;
                      selectedDealId = null;
                    });
                  },
                  decoration: const InputDecoration(labelText: 'Party'),
                ),
                const SizedBox(height: 12),
                DropdownButtonFormField<int?>(
                  value: selectedDealId,
                  isExpanded: true,
                  items: [
                    const DropdownMenuItem<int?>(
                        value: null, child: Text('No Related Deal')),
                    ...partyDeals.map(
                      (deal) => DropdownMenuItem<int?>(
                        value: deal.id,
                        child: Text('Deal #${deal.id} • ${deal.dealType}'),
                      ),
                    ),
                  ],
                  onChanged: (value) =>
                      setModalState(() => selectedDealId = value),
                  decoration: const InputDecoration(labelText: 'Related Deal'),
                ),
                const SizedBox(height: 12),
                DropdownButtonFormField<String>(
                  value: paymentMethod,
                  items: const [
                    DropdownMenuItem(value: 'CASH', child: Text('CASH')),
                    DropdownMenuItem(value: 'BANK', child: Text('BANK')),
                    DropdownMenuItem(value: 'CHECK', child: Text('CHEQUE')),
                  ],
                  onChanged: (value) =>
                      setModalState(() => paymentMethod = value ?? 'CASH'),
                  decoration:
                      const InputDecoration(labelText: 'Payment Method'),
                ),
                const SizedBox(height: 12),
                TextField(
                    controller: paymentRef,
                    decoration:
                        const InputDecoration(labelText: 'Payment Reference')),
                const SizedBox(height: 12),
                TextField(
                    controller: notes,
                    decoration: const InputDecoration(labelText: 'Notes')),
                const SizedBox(height: 12),
                SwitchListTile(
                  value: allowAdvance,
                  onChanged: (value) =>
                      setModalState(() => allowAdvance = value),
                  title: const Text('Allow Advance'),
                  contentPadding: EdgeInsets.zero,
                ),
                const SizedBox(height: 16),
                ElevatedButton(
                  onPressed: () async {
                    try {
                      await widget.repository.updateSettlement(
                        id: row.entryId,
                        partyId: selectedPartyId,
                        tradeDealId: selectedDealId,
                        amount: double.parse(amount.text),
                        paymentMethod: paymentMethod,
                        paymentReference: paymentRef.text.trim().isEmpty
                            ? null
                            : paymentRef.text.trim(),
                        allowAdvance: allowAdvance,
                        notes: notes.text.trim(),
                        settlementTime: row.occurredAt,
                      );
                      if (!context.mounted) return;
                      Navigator.pop(context);
                    } on ApiException catch (error) {
                      showAppMessage(context, error.message, isError: true);
                    } on FormatException {
                      showAppMessage(context, 'Enter valid numbers',
                          isError: true);
                    }
                  },
                  child: const Text('Update Settlement'),
                ),
              ],
            ),
          ),
        );
      },
    );
    await _load();
  }

  Future<void> _editExpense(TransactionDetailRowModel row) async {
    final amount = TextEditingController(text: row.amountBdt.toString());
    final category = TextEditingController(text: row.category ?? 'OTHER');
    final notes = TextEditingController(text: row.notes ?? '');
    String expenseType = row.expenseType ?? 'OTHER';
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      builder: (context) => StatefulBuilder(
        builder: (context, setModalState) => Padding(
          padding: EdgeInsets.fromLTRB(
            20,
            24,
            20,
            MediaQuery.of(context).viewInsets.bottom + 24,
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text('Edit Expense',
                  style: Theme.of(context).textTheme.titleLarge),
              const SizedBox(height: 12),
              TextField(
                  controller: amount,
                  keyboardType: TextInputType.number,
                  decoration: const InputDecoration(labelText: 'Amount (BDT)')),
              const SizedBox(height: 12),
              DropdownButtonFormField<String>(
                value: expenseType,
                items: const [
                  DropdownMenuItem(
                      value: 'OFFICE_MANAGEMENT',
                      child: Text('OFFICE MANAGEMENT')),
                  DropdownMenuItem(
                      value: 'TRANSPORT', child: Text('TRANSPORT')),
                  DropdownMenuItem(value: 'UTILITY', child: Text('UTILITY')),
                  DropdownMenuItem(value: 'RENT', child: Text('RENT')),
                  DropdownMenuItem(
                      value: 'EMPLOYEE_SALARY', child: Text('EMPLOYEE SALARY')),
                  DropdownMenuItem(value: 'OTHER', child: Text('OTHER')),
                ],
                onChanged: (value) =>
                    setModalState(() => expenseType = value ?? expenseType),
                decoration: const InputDecoration(labelText: 'Expense Type'),
              ),
              const SizedBox(height: 12),
              TextField(
                  controller: category,
                  decoration: const InputDecoration(labelText: 'Category')),
              const SizedBox(height: 12),
              TextField(
                  controller: notes,
                  decoration: const InputDecoration(labelText: 'Notes')),
              const SizedBox(height: 16),
              ElevatedButton(
                onPressed: () async {
                  try {
                    await widget.repository.updateExpense(
                      id: row.entryId,
                      expenseType: expenseType,
                      amount: double.parse(amount.text),
                      category: category.text.trim(),
                      notes: notes.text.trim(),
                      expenseTime: row.occurredAt,
                    );
                    if (!context.mounted) return;
                    Navigator.pop(context);
                  } on ApiException catch (error) {
                    showAppMessage(context, error.message, isError: true);
                  } on FormatException {
                    showAppMessage(context, 'Enter valid numbers',
                        isError: true);
                  }
                },
                child: const Text('Update Expense'),
              ),
            ],
          ),
        ),
      ),
    );
    await _load();
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) {
      return const Center(child: CircularProgressIndicator());
    }
    final details = _details;
    if (details == null) {
      return EmptyStateCard(
        title: _networkError ? 'No internet connection' : 'No transaction details',
        message: _networkError
            ? 'Please check your network and try again.'
            : 'Adjust filters and try again.',
      );
    }

    return Column(
      children: [
        FinanceSection(
          title: 'Filters & Export',
          trailing: IconButton(
            onPressed: _export,
            icon: const Icon(Icons.picture_as_pdf_outlined),
            tooltip: 'Export PDF',
          ),
          child: Column(
            children: [
              Row(
                children: [
                  Expanded(
                    child: OutlinedButton(
                      onPressed: () => _pickRange(true),
                      child: Text('From ${formatDate(_from)}'),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: OutlinedButton(
                      onPressed: () => _pickRange(false),
                      child: Text('To ${formatDate(_to)}'),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 12),
              TextField(
                controller: _searchController,
                onSubmitted: (_) => _load(),
                decoration: InputDecoration(
                  labelText: 'Search party, note, category, reference',
                  prefixIcon: const Icon(Icons.search),
                  suffixIcon: IconButton(
                    onPressed: _load,
                    icon: const Icon(Icons.arrow_forward),
                  ),
                ),
              ),
              const SizedBox(height: 12),
              DropdownButtonFormField<String>(
                initialValue: _type.isEmpty ? 'ALL' : _type,
                isExpanded: true,
                items: const [
                  DropdownMenuItem(value: 'ALL', child: Text('All Types')),
                  DropdownMenuItem(value: 'DEAL', child: Text('Deals')),
                  DropdownMenuItem(
                      value: 'SETTLEMENT', child: Text('Settlements')),
                  DropdownMenuItem(value: 'EXPENSE', child: Text('Expenses')),
                  DropdownMenuItem(
                      value: 'OPENING_BALANCE', child: Text('Opening Balance')),
                ],
                onChanged: (value) async {
                  setState(() =>
                      _type = value == null || value == 'ALL' ? '' : value);
                  await _load();
                },
                decoration: const InputDecoration(labelText: 'Type'),
              ),
              const SizedBox(height: 12),
              DropdownButtonFormField<int?>(
                initialValue: _partyId,
                isExpanded: true,
                items: [
                  const DropdownMenuItem<int?>(
                      value: null, child: Text('All Parties')),
                  ..._parties.map(
                    (party) => DropdownMenuItem<int?>(
                      value: party.id,
                      child: Text(party.name),
                    ),
                  ),
                ],
                onChanged: (value) async {
                  setState(() => _partyId = value);
                  await _load();
                },
                decoration: const InputDecoration(labelText: 'Party'),
              ),
              const SizedBox(height: 12),
              Column(
                children: [
                  DropdownButtonFormField<String>(
                    initialValue: _sortField,
                    isExpanded: true,
                    items: const [
                      DropdownMenuItem(
                          value: 'occurredAt', child: Text('Sort by Date')),
                      DropdownMenuItem(
                          value: 'amountBdt', child: Text('Sort by Amount')),
                      DropdownMenuItem(
                          value: 'entryType', child: Text('Sort by Type')),
                      DropdownMenuItem(
                          value: 'partyName', child: Text('Sort by Party')),
                    ],
                    onChanged: (value) async {
                      setState(() => _sortField = value ?? 'occurredAt');
                      await _load();
                    },
                    decoration: const InputDecoration(labelText: 'Sort Field'),
                  ),
                  const SizedBox(height: 12),
                  DropdownButtonFormField<String>(
                    initialValue: _sortDirection,
                    isExpanded: true,
                    items: const [
                      DropdownMenuItem(
                          value: 'desc', child: Text('Newest / Highest')),
                      DropdownMenuItem(
                          value: 'asc', child: Text('Oldest / Lowest')),
                    ],
                    onChanged: (value) async {
                      setState(() => _sortDirection = value ?? 'desc');
                      await _load();
                    },
                    decoration:
                        const InputDecoration(labelText: 'Sort Direction'),
                  ),
                ],
              ),
            ],
          ),
        ),
        const SizedBox(height: 16),
        if (details.rows.isEmpty)
          const EmptyStateCard(
            title: 'No transactions found',
            message:
                'Try another date range, type, search term, or party filter.',
          )
        else
          FinanceSection(
            title: 'Transaction Details',
            child: Column(
              children: details.rows
                  .map(
                    (row) => Card(
                      margin: const EdgeInsets.only(bottom: 10),
                      child: Padding(
                        padding: const EdgeInsets.all(16),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Row(
                              children: [
                                Expanded(
                                  child: Text(
                                    row.referenceLabel ??
                                        '${row.entryType} #${row.entryId}',
                                    style:
                                        Theme.of(context).textTheme.titleMedium,
                                  ),
                                ),
                                Text(
                                  _formatSignedAmount(
                                    row,
                                  ),
                                  style: Theme.of(context)
                                      .textTheme
                                      .titleMedium
                                      ?.copyWith(
                                        color: _amountColor(row),
                                      ),
                                ),
                                if (_canMutate(row))
                                  PopupMenuButton<String>(
                                    onSelected: (value) async {
                                      if (value == 'edit') {
                                        await _editRow(row);
                                        return;
                                      }
                                      if (value == 'delete') {
                                        await _deleteRow(row);
                                      }
                                    },
                                    itemBuilder: (context) => const [
                                      PopupMenuItem(
                                          value: 'edit', child: Text('Edit')),
                                      PopupMenuItem(
                                          value: 'delete',
                                          child: Text('Delete')),
                                    ],
                                  ),
                              ],
                            ),
                            const SizedBox(height: 6),
                            Text(
                                '${_entryTypeLabel(row.entryType)} • ${formatDateTime(row.occurredAt)}'),
                            if ((row.partyName ?? '').isNotEmpty)
                              Text('Party: ${row.partyName}'),
                            if ((row.directionLabel ?? '').isNotEmpty)
                              Text('Direction: ${row.directionLabel}'),
                            if ((row.paymentMethod ?? '').isNotEmpty)
                              Text(
                                  'Payment: ${row.paymentMethod == 'CHECK' ? 'CHEQUE' : row.paymentMethod}'),
                            if ((row.paymentReference ?? '').isNotEmpty)
                              Text('Reference: ${row.paymentReference}'),
                            if ((row.instrumentCode ?? '').isNotEmpty)
                              Text(
                                  'Instrument: ${instrumentDisplayName(row.instrumentCode!)}'),
                            if (row.quantity != null)
                              Text('Amount: ${row.quantity}'),
                            if (row.bdtRate != null)
                              Text('Rate: ${row.bdtRate}'),
                            if ((row.category ?? '').isNotEmpty)
                              Text('Category: ${row.category}'),
                            if ((row.notes ?? '').isNotEmpty) ...[
                              const SizedBox(height: 4),
                              Text(row.notes!),
                            ],
                          ],
                        ),
                      ),
                    ),
                  )
                  .toList(),
            ),
          ),
      ],
    );
  }

  String _entryTypeLabel(String entryType) {
    switch (entryType) {
      case 'OPENING_BALANCE':
        return 'Opening Balance';
      case 'SETTLEMENT':
        return 'Settlement';
      case 'EXPENSE':
        return 'Expense';
      case 'DEAL':
        return 'Deal';
      default:
        return entryType;
    }
  }

  String _formatSignedAmount(TransactionDetailRowModel row) {
    final amount = row.amountBdt;
    if (amount == 0) {
      return formatBdt(0);
    }
    final sign = _isNegativeRow(row) ? '-' : '+';
    return '$sign${formatBdt(amount.abs())}';
  }

  Color _amountColor(TransactionDetailRowModel row) {
    final isNegative = _isNegativeRow(row);
    return isNegative ? Colors.red.shade700 : Colors.green.shade700;
  }

  bool _isNegativeRow(TransactionDetailRowModel row) {
    final direction = (row.directionLabel ?? '').toUpperCase();
    final isOpeningPayable =
        row.entryType == 'OPENING_BALANCE' && direction.contains('PAYABLE');
    final isOutgoingSettlement =
        row.entryType == 'SETTLEMENT' && direction.startsWith('OUTGOING');
    final isSellDeal = row.entryType == 'DEAL' && direction.contains('SELL');
    return row.entryType == 'EXPENSE' ||
        isOutgoingSettlement ||
        isOpeningPayable ||
        isSellDeal;
  }
}

class _PdfPreviewScreen extends StatefulWidget {
  const _PdfPreviewScreen({
    required this.filePath,
    required this.fileName,
  });

  final String filePath;
  final String fileName;

  @override
  State<_PdfPreviewScreen> createState() => _PdfPreviewScreenState();
}

class _PdfPreviewScreenState extends State<_PdfPreviewScreen> {
  bool _sharing = false;

  Future<void> _share() async {
    if (_sharing) return;
    setState(() => _sharing = true);
    try {
      await Share.shareXFiles(
        [XFile(widget.filePath)],
        fileNameOverrides: [widget.fileName],
      );
    } finally {
      if (mounted) {
        setState(() => _sharing = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(widget.fileName),
        actions: [
          IconButton(
            onPressed: _sharing ? null : _share,
            icon: const Icon(Icons.share_outlined),
            tooltip: 'Share PDF',
          ),
        ],
      ),
      body: SfPdfViewer.file(
        File(widget.filePath),
      ),
    );
  }
}
