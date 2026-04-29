import 'package:flutter/material.dart';

import '../../shared/instruments/instrument_labels.dart';
import '../../shared/models/domain_models.dart';
import '../../shared/services/api_client.dart';
import '../../shared/services/doller_repository.dart';
import '../../shared/widgets/finance_widgets.dart';

class DashboardScreen extends StatefulWidget {
  const DashboardScreen({super.key, required this.repository});

  final DollerRepository repository;

  @override
  State<DashboardScreen> createState() => _DashboardScreenState();
}

class _DashboardScreenState extends State<DashboardScreen> {
  DashboardMetrics? _metrics;
  DashboardPnlExplainModel? _pnlExplain;
  Map<String, int> _stats = const {'pending': 0, 'failed': 0, 'poison': 0};
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() => _loading = true);
    final now = DateTime.now();
    final from = DateTime(now.year, now.month, 1);
    try {
      final metrics = await widget.repository.dashboard(from, now);
      final explain = await widget.repository.dashboardPnlExplain(from, now);
      final stats = await widget.repository.queueStats();
      if (!mounted) {
        return;
      }
      setState(() {
        _metrics = metrics;
        _pnlExplain = explain;
        _stats = stats;
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

  @override
  Widget build(BuildContext context) {
    if (_loading) {
      return const Center(child: CircularProgressIndicator());
    }
    if (_metrics == null) {
      return const EmptyStateCard(
        title: 'No dashboard data',
        message: 'We could not load your finance overview right now.',
      );
    }
    return RefreshIndicator(
      onRefresh: _load,
      child: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          Text('Executive Overview',
              style: Theme.of(context).textTheme.headlineMedium),
          const SizedBox(height: 8),
          Text(
            'A clean view of liquidity, receivables, payables, and sync health.',
            style: Theme.of(context).textTheme.bodySmall,
          ),
          const SizedBox(height: 20),
          GridView.count(
            crossAxisCount: 2,
            shrinkWrap: true,
            crossAxisSpacing: 14,
            mainAxisSpacing: 14,
            physics: const NeverScrollableScrollPhysics(),
            childAspectRatio: 1.15,
            children: [
              MetricCard(
                label: 'Today Gross P/L',
                value: formatBdt(_metrics!.todayGrossPnlBdt),
                caption: 'Sell - Buy (trading only)',
                positive: _metrics!.todayGrossPnlBdt >= 0,
                onTap: _pnlExplain == null ? null : _showPnlExplainDialog,
              ),
              MetricCard(
                label: 'Today Net P/L',
                value: formatBdt(_metrics!.todayNetPnlBdt),
                caption: 'Gross - Owner/Company Expense',
                positive: _metrics!.todayNetPnlBdt >= 0,
                onTap: _pnlExplain == null ? null : _showPnlExplainDialog,
              ),
              MetricCard(
                label: 'Period Gross P/L',
                value: formatBdt(_metrics!.periodGrossPnlBdt),
                caption: 'Selected period trading only',
                positive: _metrics!.periodGrossPnlBdt >= 0,
                onTap: _pnlExplain == null ? null : _showPnlExplainDialog,
              ),
              MetricCard(
                label: 'Period Net P/L',
                value: formatBdt(_metrics!.periodNetPnlBdt),
                caption: 'After owner/company costs',
                positive: _metrics!.periodNetPnlBdt >= 0,
                onTap: _pnlExplain == null ? null : _showPnlExplainDialog,
              ),
            ],
          ),
          const SizedBox(height: 16),
          FinanceSection(
            title: 'Balance Watch',
            child: Row(
              children: [
                Expanded(
                  child: MetricCard(
                    label: 'Receivable',
                    value: formatBdt(_metrics!.receivableBdt),
                    caption: 'Open customer side due',
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: MetricCard(
                    label: 'Payable',
                    value: formatBdt(_metrics!.payableBdt),
                    caption: 'Outstanding supplier side',
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 16),
          FinanceSection(
            title: 'Position Watch',
            child: Row(
              children: [
                Expanded(
                  child: MetricCard(
                    label: 'Position Value',
                    value: formatBdt(_metrics!.totalPositionValuationBdt),
                    caption: 'Total open position valued in BDT',
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: MetricCard(
                    label: 'Sync Queue',
                    value: '${_stats['pending']} pending',
                    caption:
                        '${_stats['failed']} failed / ${_stats['poison']} poison',
                    positive: (_stats['failed'] ?? 0) == 0 &&
                        (_stats['poison'] ?? 0) == 0,
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 16),
          if (_metrics!.positions.isNotEmpty)
            FinanceSection(
              title: 'Per Instrument Positions',
              child: Column(
                children: _metrics!.positions
                    .map(
                      (position) => ListTile(
                        contentPadding: EdgeInsets.zero,
                        title: Text(
                            instrumentDisplayName(position.instrumentCode)),
                        subtitle: Text('Qty ${position.quantity}'),
                        trailing: Text(
                          formatBdt(position.valuationBdt),
                          style: Theme.of(context).textTheme.titleMedium,
                        ),
                      ),
                    )
                    .toList(),
              ),
            ),
          if (_metrics!.positions.isNotEmpty) const SizedBox(height: 16),
          FinanceSection(
            title: 'Sync Controls',
            child: Row(
              children: [
                Expanded(
                  child: ElevatedButton(
                    onPressed: () async {
                      await widget.repository.flushRetries();
                      await _load();
                    },
                    child: const Text('Retry Queue'),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: OutlinedButton(
                    onPressed: () async {
                      await widget.repository.retryPoison();
                      await _load();
                    },
                    child: const Text('Recover Poison'),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  void _showPnlExplainDialog() {
    final explain = _pnlExplain;
    if (explain == null) {
      return;
    }
    showDialog<void>(
      context: context,
      builder: (_) => AlertDialog(
        title: const Text('Profit/Loss Explanation'),
        content: SizedBox(
          width: 560,
          child: SingleChildScrollView(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'P/L is inventory-cost based (FIFO). Sell-first profit is realized on buy-back cover.',
                  style: Theme.of(context).textTheme.bodyMedium,
                ),
                const SizedBox(height: 12),
                _PnlExplainSection(section: explain.today),
                const SizedBox(height: 16),
                _PnlExplainSection(section: explain.period),
              ],
            ),
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text('Close'),
          ),
        ],
      ),
    );
  }
}

class _PnlExplainSection extends StatelessWidget {
  const _PnlExplainSection({required this.section});

  final PnlExplainSectionModel section;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(section.label, style: Theme.of(context).textTheme.titleMedium),
        const SizedBox(height: 8),
        Text('Sell: ${formatBdt(section.sellBdt)}'),
        Text('Buy: ${formatBdt(section.buyBdt)}'),
        Text('Gross: ${formatBdt(section.grossPnlBdt)}'),
        Text('Long FIFO realized: ${formatBdt(section.longFifoRealizedPnlBdt)}'),
        Text('Short-cover realized: ${formatBdt(section.shortCoverRealizedPnlBdt)}'),
        Text('Open long total value: ${formatBdt(section.openLongValueBdt)}'),
        Text('Open short total proceeds: ${formatBdt(section.openShortProceedsBdt)}'),
        const SizedBox(height: 6),
        Text('Open inventory by instrument',
            style: Theme.of(context).textTheme.titleSmall),
        ...section.openInstruments.map(
          (row) => Text(
            '${row.instrumentCode}: Long ${row.openLongQty} (${formatBdt(row.openLongValueBdt)})'
            ' | Short ${row.openShortQty} (${formatBdt(row.openShortProceedsBdt)})',
          ),
        ),
        Text('Expense: ${formatBdt(section.expenseBdt)}'),
        Text(
          'Net: ${formatBdt(section.netPnlBdt)}',
          style: Theme.of(context).textTheme.titleSmall,
        ),
        const SizedBox(height: 8),
        Text(
          'Why this amount: it combines your trading result and then subtracts your operating costs.',
          style: Theme.of(context).textTheme.bodySmall,
        ),
        const SizedBox(height: 8),
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
      ],
    );
  }
}
