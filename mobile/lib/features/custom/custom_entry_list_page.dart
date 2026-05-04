import 'package:flutter/material.dart';

import '../../shared/models/domain_models.dart';
import '../../shared/services/api_client.dart';
import '../../shared/services/doller_repository.dart';
import '../../shared/widgets/finance_widgets.dart';
import 'custom_entry_form_page.dart';

class CustomEntryListPage extends StatefulWidget {
  const CustomEntryListPage({
    super.key,
    required this.repository,
    required this.companyId,
    required this.companyName,
    required this.initialFrom,
    required this.initialTo,
    required this.initialSearch,
  });

  final DollerRepository repository;
  final int companyId;
  final String companyName;
  final DateTime initialFrom;
  final DateTime initialTo;
  final String initialSearch;

  @override
  State<CustomEntryListPage> createState() => _CustomEntryListPageState();
}

class _CustomEntryListPageState extends State<CustomEntryListPage> {
  late DateTime _from;
  late DateTime _to;
  late TextEditingController _search;
  CustomEntryListModel? _data;
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _from = widget.initialFrom;
    _to = widget.initialTo;
    _search = TextEditingController(text: widget.initialSearch);
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

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text('${widget.companyName} Entries')),
      floatingActionButton: FloatingActionButton(
        onPressed: () => _openForm(),
        child: const Icon(Icons.add),
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : ListView(
              padding: const EdgeInsets.all(20),
              children: [
                FinanceSection(
                  title: 'Filters',
                  child: Column(
                    children: [
                      Row(
                        children: [
                          Expanded(
                            child: OutlinedButton(
                              onPressed: () async {
                                final picked = await showDatePicker(
                                  context: context,
                                  firstDate: DateTime(2020),
                                  lastDate: DateTime(2100),
                                  initialDate: _from,
                                );
                                if (picked == null) return;
                                setState(() => _from = picked);
                                await _load();
                              },
                              child: Text('From ${formatDate(_from)}'),
                            ),
                          ),
                          const SizedBox(width: 10),
                          Expanded(
                            child: OutlinedButton(
                              onPressed: () async {
                                final picked = await showDatePicker(
                                  context: context,
                                  firstDate: DateTime(2020),
                                  lastDate: DateTime(2100),
                                  initialDate: _to,
                                );
                                if (picked == null) return;
                                setState(() => _to = picked);
                                await _load();
                              },
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
                          labelText: 'Search',
                          suffixIcon: IconButton(onPressed: _load, icon: const Icon(Icons.search)),
                        ),
                      )
                    ],
                  ),
                ),
                const SizedBox(height: 12),
                if ((_data?.rows ?? const <CustomEntryRowModel>[]).isEmpty)
                  const EmptyStateCard(
                    title: 'No entries',
                    message: 'Add profit/cost entries for selected filters.',
                  )
                else
                  ..._data!.rows.map(
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

