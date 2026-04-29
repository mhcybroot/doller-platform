import 'package:flutter/material.dart';

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
          _BalanceSheetTab(
              repository: widget.repository, session: widget.session)
        else
          _TransactionDetailsTab(repository: widget.repository),
      ],
    );
  }
}

class _BalanceSheetTab extends StatefulWidget {
  const _BalanceSheetTab({
    required this.repository,
    required this.session,
  });

  final DollerRepository repository;
  final AuthSession session;

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
  DayClosePreviewModel? _preview;
  bool _loading = true;

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
      DayClosePreviewModel? preview;
      if (_mode == 'DAILY') {
        try {
          preview = await widget.repository.previewDayClose(_selectedDate);
        } on ApiException {
          preview = null;
        }
      }
      if (!mounted) {
        return;
      }
      setState(() {
        _report = report;
        _preview = preview;
        _loading = false;
      });
    } on ApiException catch (error) {
      if (!mounted) {
        return;
      }
      showAppMessage(context, error.message, isError: true);
      setState(() => _loading = false);
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
      return const EmptyStateCard(
        title: 'No report data',
        message: 'Try another reporting period.',
      );
    }
    final isDailyClosed = _mode == 'DAILY' && (_preview?.closed ?? false);

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
                  _reportPill('Opening Cash', formatBdt(report.openingCash),
                      BalancePillTone.neutral),
                  _reportPill('Closing Cash', formatBdt(report.closingCash),
                      BalancePillTone.neutral),
                  _reportPill('Opening USD', formatUsd(report.openingUsd),
                      BalancePillTone.neutral),
                  _reportPill('Closing USD', formatUsd(report.closingUsd),
                      BalancePillTone.neutral),
                ],
              ),
              const SizedBox(height: 12),
              Text('Exposure', style: Theme.of(context).textTheme.titleSmall),
              const SizedBox(height: 12),
              Wrap(
                spacing: 10,
                runSpacing: 10,
                children: [
                  _reportPill(
                      'Opening Receivable',
                      formatBdt(report.openingReceivableBdt),
                      BalancePillTone.receivable),
                  _reportPill(
                      'Closing Receivable',
                      formatBdt(report.closingReceivableBdt),
                      BalancePillTone.receivable),
                  _reportPill(
                      'Opening Payable',
                      formatBdt(report.openingPayableBdt),
                      BalancePillTone.payable),
                  _reportPill(
                      'Closing Payable',
                      formatBdt(report.closingPayableBdt),
                      BalancePillTone.payable),
                  _reportPill(
                      'Opening Advance In',
                      formatBdt(report.openingAdvanceFromPartyBdt),
                      BalancePillTone.advanceIn),
                  _reportPill(
                      'Closing Advance In',
                      formatBdt(report.closingAdvanceFromPartyBdt),
                      BalancePillTone.advanceIn),
                  _reportPill(
                      'Opening Advance Out',
                      formatBdt(report.openingAdvanceToPartyBdt),
                      BalancePillTone.advanceOut),
                  _reportPill(
                      'Closing Advance Out',
                      formatBdt(report.closingAdvanceToPartyBdt),
                      BalancePillTone.advanceOut),
                ],
              ),
              const SizedBox(height: 12),
              Text('Risk', style: Theme.of(context).textTheme.titleSmall),
              const SizedBox(height: 12),
              Wrap(
                spacing: 10,
                runSpacing: 10,
                children: [
                  _reportPill('Opening Aging',
                      formatBdt(report.openingAgingBdt), BalancePillTone.aging),
                  _reportPill('Closing Aging',
                      formatBdt(report.closingAgingBdt), BalancePillTone.aging),
                ],
              ),
              const SizedBox(height: 12),
              Text('Performance',
                  style: Theme.of(context).textTheme.titleSmall),
              const SizedBox(height: 12),
              MetricCard(
                label: 'Total P/L',
                value: formatBdt(report.totalPnl),
                caption:
                    '${formatDate(report.from)} to ${formatDate(report.to)}',
                positive: report.totalPnl >= 0,
              ),
            ],
          ),
        ),
        if (_mode == 'DAILY' && _preview != null) ...[
          const SizedBox(height: 16),
          FinanceSection(
            title: isDailyClosed ? 'Day Close Status' : 'Day Close Controls',
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Buy ${formatBdt(_preview!.totalBuyBdt)}'),
                Text('Sell ${formatBdt(_preview!.totalSellBdt)}'),
                Text('Owner/Company Expense ${formatBdt(_preview!.totalExpenseBdt)}'),
                const SizedBox(height: 8),
                Text(
                  'Projected P/L ${formatBdt(_preview!.realizedProfitLossBdt)}',
                  style: Theme.of(context).textTheme.titleMedium,
                ),
                const SizedBox(height: 14),
                Text(
                  isDailyClosed
                      ? 'This day is already closed. Reopen it only if you need to correct entries.'
                      : 'This day is still open. Confirm close to create the daily balance-sheet snapshot.',
                  style: Theme.of(context).textTheme.bodyMedium,
                ),
                const SizedBox(height: 14),
                if (!isDailyClosed || widget.session.isOwner)
                  Row(
                    children: [
                      if (!isDailyClosed)
                        Expanded(
                          child: ElevatedButton(
                            onPressed: () async {
                              try {
                                final result = await widget.repository
                                    .confirmDayClose(_selectedDate);
                                if (!mounted) return;
                                showAppMessage(
                                    context, 'Day closed: ${result.auditRef}');
                                await _load();
                              } on ApiException catch (error) {
                                if (!mounted) return;
                                if (error.message == 'Day already closed') {
                                  showAppMessage(context,
                                      'This day is already closed. Reloading status.');
                                  await _load();
                                  return;
                                }
                                showAppMessage(context, error.message,
                                    isError: true);
                              }
                            },
                            child: const Text('Confirm Close'),
                          ),
                        ),
                      if (widget.session.isOwner) ...[
                        if (!isDailyClosed) const SizedBox(width: 12),
                        Expanded(
                          child: OutlinedButton(
                            onPressed: isDailyClosed
                                ? () async {
                                    try {
                                      await widget.repository.reopenDay(
                                          _selectedDate, 'Owner correction');
                                      if (!mounted) return;
                                      showAppMessage(context, 'Day reopened');
                                      await _load();
                                    } on ApiException catch (error) {
                                      if (!mounted) return;
                                      showAppMessage(context, error.message,
                                          isError: true);
                                    }
                                  }
                                : null,
                            child:
                                Text(isDailyClosed ? 'Reopen Day' : 'Closed'),
                          ),
                        ),
                      ],
                    ],
                  ),
              ],
            ),
          ),
        ],
        const SizedBox(height: 16),
        if (report.lines.isEmpty)
          const EmptyStateCard(
            title: 'No balance sheet lines',
            message:
                'Close operational days first to build balance-sheet history.',
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
                          'USD ${formatUsd(line.openingUsd)} -> ${formatUsd(line.closingUsd)}',
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
                                  'Open Advance In',
                                  formatBdt(line.openingAdvanceFromPartyBdt),
                                  BalancePillTone.advanceIn),
                              _reportPill(
                                  'Close Advance In',
                                  formatBdt(line.closingAdvanceFromPartyBdt),
                                  BalancePillTone.advanceIn),
                              _reportPill(
                                  'Open Advance Out',
                                  formatBdt(line.openingAdvanceToPartyBdt),
                                  BalancePillTone.advanceOut),
                              _reportPill(
                                  'Close Advance Out',
                                  formatBdt(line.closingAdvanceToPartyBdt),
                                  BalancePillTone.advanceOut),
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
}

Widget _reportPill(String label, String value, BalancePillTone tone) {
  return SizedBox(
    width: 150,
    child: BalancePill(
      label: label,
      value: value,
      tone: tone,
    ),
  );
}

class _TransactionDetailsTab extends StatefulWidget {
  const _TransactionDetailsTab({required this.repository});

  final DollerRepository repository;

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
        _loading = false;
      });
    } on ApiException catch (error) {
      if (!mounted) {
        return;
      }
      showAppMessage(context, error.message, isError: true);
      setState(() => _loading = false);
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
      await widget.repository.exportAndShareTransactionDetails(
        from: _from,
        to: _to,
        type: _type,
        partyId: _partyId,
        search: _searchController.text,
        sortField: _sortField,
        sortDirection: _sortDirection,
      );
    } on ApiException catch (error) {
      if (!mounted) return;
      showAppMessage(context, error.message, isError: true);
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) {
      return const Center(child: CircularProgressIndicator());
    }
    final details = _details;
    if (details == null) {
      return const EmptyStateCard(
        title: 'No transaction details',
        message: 'Adjust filters and try again.',
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
                      value: 'OPENING_BALANCE',
                      child: Text('Opening Balance')),
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
                                        color: row.entryType == 'EXPENSE'
                                            ? Colors.red.shade700
                                            : AppTheme.ink,
                                      ),
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
                              Text('Quantity: ${row.quantity}'),
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
    final direction = (row.directionLabel ?? '').toUpperCase();
    final isOutgoingSettlement =
        row.entryType == 'SETTLEMENT' && direction.startsWith('OUTGOING');
    final sign = row.entryType == 'EXPENSE' || isOutgoingSettlement ? '-' : '+';
    return '$sign${formatBdt(amount.abs())}';
  }
}
