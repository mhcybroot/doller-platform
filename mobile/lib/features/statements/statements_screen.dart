import 'package:flutter/material.dart';

import '../../shared/models/auth_models.dart';
import '../../shared/models/domain_models.dart';
import '../../shared/services/api_client.dart';
import '../../shared/services/doller_repository.dart';
import '../../shared/widgets/finance_widgets.dart';

class StatementsScreen extends StatefulWidget {
  const StatementsScreen({
    super.key,
    required this.repository,
    required this.session,
  });

  final DollerRepository repository;
  final AuthSession session;

  @override
  State<StatementsScreen> createState() => _StatementsScreenState();
}

class _StatementsScreenState extends State<StatementsScreen> {
  late DateTime _from;
  late DateTime _to;
  List<StatementLineModel> _lines = const [];
  DayClosePreviewModel? _preview;
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    final now = DateTime.now();
    _from = now;
    _to = now;
    _load();
  }

  Future<void> _load() async {
    setState(() => _loading = true);
    try {
      final lines = await widget.repository.statements(_from, _to);
      final preview = await widget.repository.previewDayClose(_to);
      if (!mounted) {
        return;
      }
      setState(() {
        _lines = lines;
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

  Future<void> _pickDate(bool isFrom) async {
    final selected = await showDatePicker(
      context: context,
      firstDate: DateTime(2020),
      lastDate: DateTime(2100),
      initialDate: isFrom ? _from : _to,
    );
    if (selected == null) {
      return;
    }
    setState(() {
      if (isFrom) {
        _from = selected;
      } else {
        _to = selected;
      }
    });
    await _load();
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) {
      return const Center(child: CircularProgressIndicator());
    }
    return ListView(
      padding: const EdgeInsets.all(20),
      children: [
        Text('Statements', style: Theme.of(context).textTheme.headlineMedium),
        const SizedBox(height: 8),
        Text(
          'Daily close visibility, range reporting, and operational finance history.',
          style: Theme.of(context).textTheme.bodySmall,
        ),
        const SizedBox(height: 18),
        FinanceSection(
          title: 'Date Range',
          child: Row(
            children: [
              Expanded(
                child: OutlinedButton(
                  onPressed: () => _pickDate(true),
                  child: Text('From ${formatDate(_from)}'),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: OutlinedButton(
                  onPressed: () => _pickDate(false),
                  child: Text('To ${formatDate(_to)}'),
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 16),
        if (_preview != null)
          FinanceSection(
            title: 'Day Close Preview',
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Buy ${formatBdt(_preview!.totalBuyBdt)}'),
                Text('Sell ${formatBdt(_preview!.totalSellBdt)}'),
                Text('Expense ${formatBdt(_preview!.totalExpenseBdt)}'),
                const SizedBox(height: 8),
                Text(
                  'Projected P/L ${formatBdt(_preview!.realizedProfitLossBdt)}',
                  style: Theme.of(context).textTheme.titleMedium,
                ),
                const SizedBox(height: 14),
                Row(
                  children: [
                    Expanded(
                      child: ElevatedButton(
                        onPressed: () async {
                          try {
                            final result = await widget.repository.confirmDayClose(_to);
                            if (!mounted) {
                              return;
                            }
                            showAppMessage(context, 'Day closed: ${result.auditRef}');
                            await _load();
                          } on ApiException catch (error) {
                            showAppMessage(context, error.message, isError: true);
                          }
                        },
                        child: const Text('Confirm Close'),
                      ),
                    ),
                    if (widget.session.isOwner) ...[
                      const SizedBox(width: 12),
                      Expanded(
                        child: OutlinedButton(
                          onPressed: () async {
                            try {
                              await widget.repository.reopenDay(_to, 'Owner correction');
                              if (!mounted) {
                                return;
                              }
                              showAppMessage(context, 'Day reopened');
                              await _load();
                            } on ApiException catch (error) {
                              showAppMessage(context, error.message, isError: true);
                            }
                          },
                          child: const Text('Reopen Day'),
                        ),
                      ),
                    ],
                  ],
                ),
              ],
            ),
          ),
        const SizedBox(height: 16),
        if (_lines.isEmpty)
          const EmptyStateCard(
            title: 'No statements for this range',
            message: 'Pick another date range or close the day to generate statement snapshots.',
          )
        else
          FinanceSection(
            title: 'Statement Lines',
            child: Column(
              children: _lines
                  .map(
                    (line) => ListTile(
                      contentPadding: EdgeInsets.zero,
                      title: Text(formatDate(line.date)),
                      subtitle: Text(
                        'Open cash ${formatBdt(line.openingCash)}  Close cash ${formatBdt(line.closingCash)}',
                      ),
                      trailing: Text(
                        formatBdt(line.pnl),
                        style: TextStyle(
                          fontWeight: FontWeight.w700,
                          color: line.pnl >= 0 ? Colors.green.shade700 : Colors.red.shade700,
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
}
