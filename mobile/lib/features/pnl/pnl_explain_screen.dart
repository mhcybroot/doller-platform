import 'package:flutter/material.dart';

import '../../shared/models/domain_models.dart';
import '../../shared/services/api_client.dart';
import '../../shared/services/doller_repository.dart';
import '../../shared/widgets/finance_widgets.dart';

class PnlExplainScreen extends StatefulWidget {
  const PnlExplainScreen({super.key, required this.repository});

  final DollerRepository repository;

  @override
  State<PnlExplainScreen> createState() => _PnlExplainScreenState();
}

class _PnlExplainScreenState extends State<PnlExplainScreen> {
  String _mode = 'DAILY';
  late DateTime _selectedDate;
  late DateTime _from;
  late DateTime _to;
  late int _selectedMonth;
  late int _selectedYear;
  DashboardPnlExplainModel? _data;
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
      final data = await widget.repository.dashboardPnlExplainByMode(
        mode: _mode,
        date: _mode == 'DAILY' ? _selectedDate : null,
        month: _mode == 'MONTHLY' ? _selectedMonth : null,
        year: _mode == 'MONTHLY' || _mode == 'YEARLY' ? _selectedYear : null,
        from: _mode == 'CUSTOM' ? _from : null,
        to: _mode == 'CUSTOM' ? _to : null,
      );
      if (!mounted) return;
      setState(() {
        _data = data;
        _loading = false;
      });
    } on ApiException catch (error) {
      if (!mounted) return;
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
    if (selected == null) return;
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

  @override
  Widget build(BuildContext context) {
    if (_loading) return const Center(child: CircularProgressIndicator());
    final data = _data;
    if (data == null) {
      return const EmptyStateCard(
        title: 'No P/L data',
        message: 'Try another mode or date range.',
      );
    }
    return ListView(
      padding: const EdgeInsets.all(20),
      children: [
        Text('Profit/Loss Explanation',
            style: Theme.of(context).textTheme.headlineMedium),
        const SizedBox(height: 8),
        Text(
          'Gross = Sell - Buy. Net = Gross - Owner/Company Expense.',
          style: Theme.of(context).textTheme.bodySmall,
        ),
        const SizedBox(height: 16),
        FinanceSection(
          title: 'Period Mode',
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
        GridView.count(
          crossAxisCount: 2,
          shrinkWrap: true,
          crossAxisSpacing: 12,
          mainAxisSpacing: 12,
          physics: const NeverScrollableScrollPhysics(),
          childAspectRatio: 1.2,
          children: [
            MetricCard(
              label: 'Selected Gross P/L',
              value: formatBdt(data.period.grossPnlBdt),
              caption: 'Sell - Buy',
              positive: data.period.grossPnlBdt >= 0,
            ),
            MetricCard(
              label: 'Selected Net P/L',
              value: formatBdt(data.period.netPnlBdt),
              caption: 'Gross - Expense',
              positive: data.period.netPnlBdt >= 0,
            ),
          ],
        ),
        const SizedBox(height: 16),
        FinanceSection(
          title: 'Today',
          child: _ExplainSection(section: data.today),
        ),
        const SizedBox(height: 16),
        FinanceSection(
          title:
              'Selected Period (${formatDate(data.periodFrom)} - ${formatDate(data.periodTo)})',
          child: _ExplainSection(section: data.period),
        ),
      ],
    );
  }
}

class _ExplainSection extends StatelessWidget {
  const _ExplainSection({required this.section});

  final PnlExplainSectionModel section;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text('Sell: ${formatBdt(section.sellBdt)}'),
        Text('Buy: ${formatBdt(section.buyBdt)}'),
        Text('Gross: ${formatBdt(section.grossPnlBdt)}'),
        Text('Expense: ${formatBdt(section.expenseBdt)}'),
        Text('Net: ${formatBdt(section.netPnlBdt)}'),
        const SizedBox(height: 8),
        Text(
          'Why this amount: it adds your trading result and subtracts your operating expenses.',
          style: Theme.of(context).textTheme.bodySmall,
        ),
        const SizedBox(height: 10),
        Text('Expenses by type', style: Theme.of(context).textTheme.titleSmall),
        ...section.expenseGroups.map(
          (group) => ExpansionTile(
            tilePadding: EdgeInsets.zero,
            childrenPadding: EdgeInsets.zero,
            title: Text(group.expenseType),
            subtitle: Text('Total ${formatBdt(group.totalAmountBdt)}'),
            children: group.rows
                .map(
                  (row) => ListTile(
                    contentPadding: EdgeInsets.zero,
                    title: Text(formatDateTime(row.time)),
                    subtitle: Text(
                        '${row.category ?? '-'}${(row.notes ?? '').isEmpty ? '' : ' • ${row.notes}'}'),
                    trailing: Text(formatBdt(row.amountBdt)),
                  ),
                )
                .toList(),
          ),
        ),
        const SizedBox(height: 8),
        Text('Buy Transactions', style: Theme.of(context).textTheme.titleSmall),
        ...section.buyRows.map(
          (row) => ListTile(
            contentPadding: EdgeInsets.zero,
            title: Text(row.referenceLabel ?? 'Deal #${row.dealId}'),
            subtitle: Text(
                '${formatDateTime(row.time)} • ${row.instrumentCode} ${row.quantity} @ ${row.bdtRate}'),
            trailing: Text(formatBdt(row.bdtAmount)),
          ),
        ),
        const SizedBox(height: 8),
        Text('Sell Transactions',
            style: Theme.of(context).textTheme.titleSmall),
        ...section.sellRows.map(
          (row) => ListTile(
            contentPadding: EdgeInsets.zero,
            title: Text(row.referenceLabel ?? 'Deal #${row.dealId}'),
            subtitle: Text(
                '${formatDateTime(row.time)} • ${row.instrumentCode} ${row.quantity} @ ${row.bdtRate}'),
            trailing: Text(formatBdt(row.bdtAmount)),
          ),
        ),
      ],
    );
  }
}
