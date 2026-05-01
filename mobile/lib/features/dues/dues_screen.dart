import 'package:flutter/material.dart';

import '../../shared/models/domain_models.dart';
import '../../shared/services/api_client.dart';
import '../../shared/services/app_logger.dart';
import '../../shared/services/doller_repository.dart';
import '../../shared/widgets/finance_widgets.dart';

enum DuesSortField { name, dueAmount, lastActivity }

enum DuesSortDirection { asc, desc }

class DuesScreen extends StatefulWidget {
  const DuesScreen({super.key, required this.repository});

  final DollerRepository repository;

  @override
  State<DuesScreen> createState() => _DuesScreenState();
}

class _DuesScreenState extends State<DuesScreen>
    with SingleTickerProviderStateMixin {
  DuesSnapshotModel? _snapshot;
  bool _loading = true;
  bool _networkError = false;
  DuesSortField _sortField = DuesSortField.dueAmount;
  DuesSortDirection _sortDirection = DuesSortDirection.desc;
  bool _showReceivable = true;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    AppLogger.log('screen:dues', 'load:start');
    setState(() => _loading = true);
    try {
      final data = await widget.repository.fetchDuesSnapshot();
      if (!mounted) {
        return;
      }
      final receivableRows =
          data.rows.where((row) => row.receivableBdt > 0).length;
      final payableRows = data.rows.where((row) => row.payableBdt > 0).length;
      AppLogger.log('screen:dues', 'load:success', fields: {
        'totalReceivableBdt': data.totalReceivableBdt,
        'totalPayableBdt': data.totalPayableBdt,
        'grossBdt': data.grossBdt,
        'netBdt': data.netBdt,
        'rowCount': data.rows.length,
        'receivableRowCount': receivableRows,
        'payableRowCount': payableRows,
      });
      setState(() {
        _snapshot = data;
        _networkError = false;
        _loading = false;
      });
    } on ApiException catch (error) {
      if (!mounted) {
        return;
      }
      AppLogger.log('screen:dues', 'load:error', fields: {
        'message': error.message,
        'isNetworkError': error.isNetworkError,
      });
      showAppMessage(context, error.message, isError: true);
      setState(() {
        _snapshot = null;
        _networkError = error.isNetworkError;
        _loading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) {
      return const Center(child: CircularProgressIndicator());
    }
    if (_snapshot == null) {
      return EmptyStateCard(
        title: _networkError ? 'No internet connection' : 'No dues data',
        message: _networkError
            ? 'Please check your network and try again.'
            : 'Could not load dues right now.',
        action: ElevatedButton.icon(
          onPressed: _load,
          icon: const Icon(Icons.refresh),
          label: const Text('Retry'),
        ),
      );
    }

    return ListView(
      padding: const EdgeInsets.all(20),
      children: [
        Text('Dues', style: Theme.of(context).textTheme.headlineMedium),
        const SizedBox(height: 8),
        Text(
          'Track outstanding receivable and payable balances across all parties.',
          style: Theme.of(context).textTheme.bodySmall,
        ),
        const SizedBox(height: 12),
        _summarySection(_snapshot!),
        const SizedBox(height: 16),
        FinanceSection(
          title: 'Party Dues',
          trailing: IconButton(
            onPressed: _load,
            icon: const Icon(Icons.refresh),
            tooltip: 'Refresh',
          ),
          child: Column(
            children: [
              SegmentedButton<bool>(
                segments: const [
                  ButtonSegment(value: true, label: Text('Receivable')),
                  ButtonSegment(value: false, label: Text('Payable')),
                ],
                selected: {_showReceivable},
                onSelectionChanged: (value) {
                  setState(() => _showReceivable = value.first);
                },
              ),
              const SizedBox(height: 14),
              _sortControls(),
              const SizedBox(height: 10),
              _duesList(
                _rowsForTab(isReceivable: _showReceivable),
                isReceivable: _showReceivable,
              ),
            ],
          ),
        ),
      ],
    );
  }

  Widget _summarySection(DuesSnapshotModel snapshot) {
    final effectiveReceivable = snapshot.rows.fold<double>(
      0,
      (sum, row) => sum + _effectiveReceivable(row),
    );
    final effectivePayable = snapshot.rows.fold<double>(
      0,
      (sum, row) => sum + _effectivePayable(row),
    );
    final netUsingEffective = effectiveReceivable - effectivePayable;
    final grossUsingEffective = effectiveReceivable + effectivePayable;
    return Wrap(
      spacing: 10,
      runSpacing: 10,
      children: [
        BalancePill(
          label: 'Total Receivable',
          value: '+${formatBdt(effectiveReceivable)}',
          tone: BalancePillTone.receivable,
        ),
        BalancePill(
          label: 'Total Payable',
          value: '-${formatBdt(effectivePayable)}',
          tone: BalancePillTone.payable,
        ),
        BalancePill(
          label: 'Gross (R+P)',
          value: formatBdt(grossUsingEffective),
          tone: BalancePillTone.neutral,
        ),
        BalancePill(
          label: 'Net (R-P)',
          value: netUsingEffective >= 0
              ? '+${formatBdt(netUsingEffective)}'
              : '-${formatBdt(netUsingEffective.abs())}',
          tone: netUsingEffective >= 0
              ? BalancePillTone.netPositive
              : BalancePillTone.netNegative,
        ),
      ],
    );
  }

  Widget _sortControls() {
    return LayoutBuilder(
      builder: (context, constraints) {
        final stacked = constraints.maxWidth < 430;
        final sortBy = DropdownButtonFormField<DuesSortField>(
          value: _sortField,
          isExpanded: true,
          decoration: const InputDecoration(labelText: 'Sort by'),
          items: const [
            DropdownMenuItem(value: DuesSortField.name, child: Text('Name')),
            DropdownMenuItem(
                value: DuesSortField.dueAmount, child: Text('Due Amount')),
            DropdownMenuItem(
                value: DuesSortField.lastActivity,
                child: Text('Last Activity')),
          ],
          onChanged: (value) {
            if (value == null) {
              return;
            }
            setState(() => _sortField = value);
          },
        );
        final direction = DropdownButtonFormField<DuesSortDirection>(
          value: _sortDirection,
          isExpanded: true,
          decoration: const InputDecoration(labelText: 'Direction'),
          items: const [
            DropdownMenuItem(
                value: DuesSortDirection.desc, child: Text('Descending')),
            DropdownMenuItem(
                value: DuesSortDirection.asc, child: Text('Ascending')),
          ],
          onChanged: (value) {
            if (value == null) {
              return;
            }
            setState(() => _sortDirection = value);
          },
        );

        if (stacked) {
          return Column(
            children: [
              sortBy,
              const SizedBox(height: 10),
              direction,
            ],
          );
        }

        return Row(
          children: [
            Expanded(child: sortBy),
            const SizedBox(width: 10),
            Expanded(child: direction),
          ],
        );
      },
    );
  }

  List<PartyDueRowModel> _rowsForTab({required bool isReceivable}) {
    final rows = (_snapshot?.rows ?? const <PartyDueRowModel>[])
        .where((row) => isReceivable
            ? _effectiveReceivable(row) > 0
            : _effectivePayable(row) > 0)
        .toList();

    AppLogger.log('screen:dues', 'rows:filtered', fields: {
      'tab': isReceivable ? 'receivable' : 'payable',
      'sourceRowCount': _snapshot?.rows.length ?? 0,
      'filteredRowCount': rows.length,
    });

    rows.sort((a, b) {
      int result;
      switch (_sortField) {
        case DuesSortField.name:
          result =
              a.partyName.toLowerCase().compareTo(b.partyName.toLowerCase());
          break;
        case DuesSortField.dueAmount:
          final ad =
              isReceivable ? _effectiveReceivable(a) : _effectivePayable(a);
          final bd =
              isReceivable ? _effectiveReceivable(b) : _effectivePayable(b);
          result = ad.compareTo(bd);
          break;
        case DuesSortField.lastActivity:
          final at = a.lastActivityAt;
          final bt = b.lastActivityAt;
          if (at == null && bt == null) {
            result = 0;
          } else if (at == null) {
            result = -1;
          } else if (bt == null) {
            result = 1;
          } else {
            result = at.compareTo(bt);
          }
          break;
      }
      return _sortDirection == DuesSortDirection.asc ? result : -result;
    });

    return rows;
  }

  Widget _duesList(List<PartyDueRowModel> rows, {required bool isReceivable}) {
    if (rows.isEmpty) {
      return EmptyStateCard(
        title: 'No dues found',
        message: isReceivable
            ? 'No parties have receivable dues right now.'
            : 'No parties have payable dues right now.',
      );
    }

    return Column(
      children: rows.map((row) {
        final tabDue = isReceivable
            ? _effectiveReceivable(row)
            : _effectivePayable(row);
        return Padding(
          padding: const EdgeInsets.only(bottom: 8),
          child: Card(
            child: ListTile(
              title: Text(row.partyName),
              subtitle: Text(
                'Last: ${row.lastActivityAt == null ? 'No activity' : formatDateTime(row.lastActivityAt!)}',
              ),
              isThreeLine: false,
              trailing: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  Text(
                    '${isReceivable ? '+' : '-'}${formatBdt(tabDue)}',
                    style: TextStyle(
                      fontWeight: FontWeight.w800,
                      color: isReceivable
                          ? Colors.green.shade700
                          : Colors.orange.shade700,
                    ),
                  ),
                  if ((row.notes ?? '').trim().isNotEmpty)
                    SizedBox(
                      width: 100,
                      child: Text(
                        row.notes!,
                        style: Theme.of(context).textTheme.bodySmall,
                        overflow: TextOverflow.ellipsis,
                        textAlign: TextAlign.right,
                      ),
                    ),
                ],
              ),
            ),
          ),
        );
      }).toList(),
    );
  }

  double _effectivePayable(PartyDueRowModel row) {
    final effective = row.payableBdt - row.advanceToPartyBdt;
    return effective > 0 ? effective : 0;
  }

  double _effectiveReceivable(PartyDueRowModel row) {
    final effective = row.receivableBdt - row.advanceFromPartyBdt;
    return effective > 0 ? effective : 0;
  }
}
