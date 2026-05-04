import 'package:flutter/material.dart';

import '../../shared/models/domain_models.dart';
import '../../shared/services/api_client.dart';
import '../../shared/services/doller_repository.dart';
import '../../shared/widgets/finance_widgets.dart';
import 'custom_entry_form_page.dart';

class CustomSummaryPage extends StatefulWidget {
  const CustomSummaryPage({
    super.key,
    required this.repository,
    required this.companyId,
    required this.companyName,
  });

  final DollerRepository repository;
  final int companyId;
  final String companyName;

  @override
  State<CustomSummaryPage> createState() => _CustomSummaryPageState();
}

class _CustomSummaryPageState extends State<CustomSummaryPage> {
  final _search = TextEditingController();
  late DateTime _from;
  late DateTime _to;
  CustomEntryListModel? _data;
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
      final data = await widget.repository.listCustomEntries(
        companyId: widget.companyId,
        from: _from,
        to: _to,
        search: _search.text,
      );
      if (!mounted) return;
      setState(() {
        _data = data;
        _loading = false;
      });
    } on ApiException catch (e) {
      if (!mounted) return;
      showAppMessage(context, e.message, isError: true);
      setState(() => _loading = false);
    }
  }

  Future<void> _pickDate(bool fromField) async {
    final picked = await showDatePicker(
      context: context,
      firstDate: DateTime(2020),
      lastDate: DateTime(2100),
      initialDate: fromField ? _from : _to,
    );
    if (picked == null) return;
    setState(() {
      if (fromField) {
        _from = picked;
      } else {
        _to = picked;
      }
    });
    await _load();
  }

  Future<void> _openForm({CustomEntryRowModel? existing}) async {
    await Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => CustomEntryFormPage(
          repository: widget.repository,
          companyId: widget.companyId,
          existing: existing,
        ),
      ),
    );
    await _load();
  }

  Future<void> _deleteRow(CustomEntryRowModel row) async {
    final confirm = await showDialog<bool>(
      context: context,
      builder: (_) => AlertDialog(
        title: const Text('Delete Entry'),
        content: Text('Delete ${row.itemPurpose}?'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
          ElevatedButton(onPressed: () => Navigator.pop(context, true), child: const Text('Delete')),
        ],
      ),
    );
    if (confirm != true) return;
    try {
      await widget.repository.deleteCustomEntry(row.id);
      if (!mounted) return;
      await _load();
    } on ApiException catch (e) {
      showAppMessage(context, e.message, isError: true);
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) return const Scaffold(body: Center(child: CircularProgressIndicator()));
    final data = _data;
    if (data == null) return const Scaffold(body: SizedBox.shrink());
    return Scaffold(
      appBar: AppBar(title: Text('${widget.companyName} Summary')),
      floatingActionButton: FloatingActionButton(
        onPressed: () => _openForm(),
        child: const Icon(Icons.add),
      ),
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          Wrap(
            spacing: 10,
            runSpacing: 10,
            children: [
              BalancePill(
                label: 'Total Profit',
                value: '+${formatBdt(data.summary.totalProfitBdt)}',
                tone: BalancePillTone.receivable,
              ),
              BalancePill(
                label: 'Total Loss',
                value: '-${formatBdt(data.summary.totalLossBdt)}',
                tone: BalancePillTone.payable,
              ),
              BalancePill(
                label: 'Net',
                value: data.summary.netBdt >= 0
                    ? '+${formatBdt(data.summary.netBdt)}'
                    : '-${formatBdt(data.summary.netBdt.abs())}',
                tone: data.summary.netBdt >= 0
                    ? BalancePillTone.netPositive
                    : BalancePillTone.netNegative,
              ),
            ],
          ),
          const SizedBox(height: 12),
          FinanceSection(
            title: 'Filters',
            child: Column(
              children: [
                Row(
                  children: [
                    Expanded(
                      child: OutlinedButton(
                        onPressed: () => _pickDate(true),
                        child: Text('From ${formatDate(_from)}'),
                      ),
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: OutlinedButton(
                        onPressed: () => _pickDate(false),
                        child: Text('To ${formatDate(_to)}'),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 10),
                TextField(
                  controller: _search,
                  onSubmitted: (_) => _load(),
                  decoration: InputDecoration(
                    labelText: 'Search item/purpose or note',
                    suffixIcon: IconButton(
                      onPressed: _load,
                      icon: const Icon(Icons.search),
                    ),
                  ),
                )
              ],
            ),
          ),
          const SizedBox(height: 12),
          if (data.rows.isEmpty)
            const EmptyStateCard(
              title: 'No entries',
              message: 'Add profit/cost entries for selected filters.',
            )
          else
            ...data.rows.map(
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
                            child: Text(row.itemPurpose, style: Theme.of(context).textTheme.titleMedium),
                          ),
                          Text(
                            '${row.entryType == 'PROFIT' ? '+' : '-'}${formatBdt(row.amountBdt)}',
                            style: Theme.of(context).textTheme.titleMedium?.copyWith(
                                  color: row.entryType == 'PROFIT'
                                      ? Colors.green.shade700
                                      : Colors.red.shade700,
                                ),
                          ),
                          PopupMenuButton<String>(
                            onSelected: (v) async {
                              if (v == 'edit') {
                                await _openForm(existing: row);
                              } else {
                                await _deleteRow(row);
                              }
                            },
                            itemBuilder: (_) => const [
                              PopupMenuItem(value: 'edit', child: Text('Edit')),
                              PopupMenuItem(value: 'delete', child: Text('Delete')),
                            ],
                          )
                        ],
                      ),
                      const SizedBox(height: 6),
                      Text('${row.entryType} • ${formatDateTime(row.entryTime)}'),
                      if ((row.notes ?? '').isNotEmpty) Text(row.notes!),
                    ],
                  ),
                ),
              ),
            ),
        ],
      ),
    );
  }
}
