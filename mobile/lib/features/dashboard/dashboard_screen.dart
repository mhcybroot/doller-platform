import 'package:flutter/material.dart';

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
      final stats = await widget.repository.queueStats();
      if (!mounted) {
        return;
      }
      setState(() {
        _metrics = metrics;
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
          Text('Executive Overview', style: Theme.of(context).textTheme.headlineMedium),
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
                label: 'Today P/L',
                value: formatBdt(_metrics!.todayPnL),
                caption: 'Realized for current day',
                positive: _metrics!.todayPnL >= 0,
              ),
              MetricCard(
                label: 'Period P/L',
                value: formatBdt(_metrics!.periodPnL),
                caption: 'Month-to-date performance',
                positive: _metrics!.periodPnL >= 0,
              ),
              MetricCard(
                label: 'Receivable',
                value: formatBdt(_metrics!.receivableBdt),
                caption: 'Open customer side due',
              ),
              MetricCard(
                label: 'Payable',
                value: formatBdt(_metrics!.payableBdt),
                caption: 'Outstanding supplier side',
              ),
            ],
          ),
          const SizedBox(height: 16),
          FinanceSection(
            title: 'Position Watch',
            child: Row(
              children: [
                Expanded(
                  child: MetricCard(
                    label: 'USD Position',
                    value: formatUsd(_metrics!.usdPosition),
                    caption: 'Net open dollar balance',
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: MetricCard(
                    label: 'Sync Queue',
                    value: '${_stats['pending']} pending',
                    caption:
                        '${_stats['failed']} failed / ${_stats['poison']} poison',
                    positive: (_stats['failed'] ?? 0) == 0 && (_stats['poison'] ?? 0) == 0,
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 16),
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
}
