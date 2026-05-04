import 'package:flutter/material.dart';

import '../../shared/models/domain_models.dart';
import '../../shared/services/api_client.dart';
import '../../shared/services/doller_repository.dart';
import '../../shared/widgets/finance_widgets.dart';

class CustomProfitCostScreen extends StatefulWidget {
  const CustomProfitCostScreen({super.key, required this.repository});

  final DollerRepository repository;

  @override
  State<CustomProfitCostScreen> createState() => _CustomProfitCostScreenState();
}

class _CustomProfitCostScreenState extends State<CustomProfitCostScreen> {
  List<CompanyModel> _companies = const [];
  int? _companyId;
  CustomEntryListModel? _data;
  final _search = TextEditingController();
  late DateTime _from;
  late DateTime _to;
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
      final companies = await widget.repository.listCompanies();
      int? selected = _companyId;
      if (companies.isNotEmpty) {
        selected ??= companies.first.id;
        if (!companies.any((c) => c.id == selected)) {
          selected = companies.first.id;
        }
      } else {
        selected = null;
      }

      CustomEntryListModel? data;
      if (selected != null) {
        data = await widget.repository.listCustomEntries(
          companyId: selected,
          from: _from,
          to: _to,
          search: _search.text,
        );
      }
      if (!mounted) return;
      setState(() {
        _companies = companies;
        _companyId = selected;
        _data = data;
        _loading = false;
      });
    } on ApiException catch (error) {
      if (!mounted) return;
      showAppMessage(context, error.message, isError: true);
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

  Future<void> _createCompany() async {
    final name = TextEditingController();
    final notes = TextEditingController();
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      builder: (context) => Padding(
        padding: EdgeInsets.fromLTRB(
          20,
          24,
          20,
          MediaQuery.of(context).viewInsets.bottom + 24,
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text('Create Company', style: Theme.of(context).textTheme.titleLarge),
            const SizedBox(height: 12),
            TextField(controller: name, decoration: const InputDecoration(labelText: 'Company Name')),
            const SizedBox(height: 12),
            TextField(controller: notes, decoration: const InputDecoration(labelText: 'Notes')),
            const SizedBox(height: 16),
            ElevatedButton(
              onPressed: () async {
                try {
                  final created = await widget.repository.createCompany(
                    name: name.text.trim(),
                    notes: notes.text.trim().isEmpty ? null : notes.text.trim(),
                  );
                  if (!context.mounted) return;
                  Navigator.pop(context);
                  setState(() => _companyId = created.id);
                  await _load();
                } on ApiException catch (e) {
                  showAppMessage(context, e.message, isError: true);
                }
              },
              child: const Text('Save'),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _editCompany(CompanyModel company) async {
    final name = TextEditingController(text: company.name);
    final notes = TextEditingController(text: company.notes ?? '');
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      builder: (context) => Padding(
        padding: EdgeInsets.fromLTRB(
          20,
          24,
          20,
          MediaQuery.of(context).viewInsets.bottom + 24,
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text('Edit Company', style: Theme.of(context).textTheme.titleLarge),
            const SizedBox(height: 12),
            TextField(controller: name, decoration: const InputDecoration(labelText: 'Company Name')),
            const SizedBox(height: 12),
            TextField(controller: notes, decoration: const InputDecoration(labelText: 'Notes')),
            const SizedBox(height: 16),
            ElevatedButton(
              onPressed: () async {
                try {
                  await widget.repository.updateCompany(
                    id: company.id,
                    name: name.text.trim(),
                    notes: notes.text.trim().isEmpty ? null : notes.text.trim(),
                  );
                  if (!context.mounted) return;
                  Navigator.pop(context);
                  await _load();
                } on ApiException catch (e) {
                  showAppMessage(context, e.message, isError: true);
                }
              },
              child: const Text('Update'),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _deleteCompany(CompanyModel company) async {
    final confirm = await showDialog<bool>(
      context: context,
      builder: (_) => AlertDialog(
        title: const Text('Delete Company'),
        content: Text('Delete ${company.name}?'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
          ElevatedButton(onPressed: () => Navigator.pop(context, true), child: const Text('Delete')),
        ],
      ),
    );
    if (confirm != true) return;
    try {
      await widget.repository.deleteCompany(company.id);
      if (!mounted) return;
      await _load();
    } on ApiException catch (e) {
      showAppMessage(context, e.message, isError: true);
    }
  }

  Future<void> _upsertEntry({CustomEntryRowModel? existing}) async {
    if (_companyId == null) {
      showAppMessage(context, 'Please create/select a company first', isError: true);
      return;
    }
    String entryType = existing?.entryType ?? 'PROFIT';
    final amount = TextEditingController(text: existing == null ? '' : existing.amountBdt.toString());
    final item = TextEditingController(text: existing?.itemPurpose ?? '');
    final notes = TextEditingController(text: existing?.notes ?? '');
    DateTime entryTime = existing?.entryTime ?? DateTime.now();

    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      builder: (context) => StatefulBuilder(
        builder: (context, setModal) => Padding(
          padding: EdgeInsets.fromLTRB(
            20,
            24,
            20,
            MediaQuery.of(context).viewInsets.bottom + 24,
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(existing == null ? 'Add Entry' : 'Edit Entry', style: Theme.of(context).textTheme.titleLarge),
              const SizedBox(height: 12),
              DropdownButtonFormField<String>(
                initialValue: entryType,
                items: const [
                  DropdownMenuItem(value: 'PROFIT', child: Text('Profit')),
                  DropdownMenuItem(value: 'COST', child: Text('Cost')),
                ],
                onChanged: (v) => setModal(() => entryType = v ?? 'PROFIT'),
                decoration: const InputDecoration(labelText: 'Type'),
              ),
              const SizedBox(height: 12),
              OutlinedButton(
                onPressed: () async {
                  final picked = await showDatePicker(
                    context: context,
                    firstDate: DateTime(2020),
                    lastDate: DateTime(2100),
                    initialDate: entryTime,
                  );
                  if (picked != null) {
                    setModal(() => entryTime = DateTime(
                        picked.year, picked.month, picked.day, entryTime.hour, entryTime.minute));
                  }
                },
                child: Text('Date ${formatDate(entryTime)}'),
              ),
              const SizedBox(height: 12),
              TextField(
                controller: item,
                decoration: const InputDecoration(labelText: 'Item/Purpose'),
              ),
              const SizedBox(height: 12),
              TextField(
                controller: amount,
                keyboardType: TextInputType.number,
                decoration: const InputDecoration(labelText: 'Amount'),
              ),
              const SizedBox(height: 12),
              TextField(
                controller: notes,
                decoration: const InputDecoration(labelText: 'Note'),
              ),
              const SizedBox(height: 16),
              ElevatedButton(
                onPressed: () async {
                  try {
                    final parsed = double.parse(amount.text.trim());
                    if (existing == null) {
                      await widget.repository.createCustomEntry(
                        companyId: _companyId!,
                        entryType: entryType,
                        amount: parsed,
                        entryTime: entryTime,
                        itemPurpose: item.text.trim(),
                        notes: notes.text.trim().isEmpty ? null : notes.text.trim(),
                      );
                    } else {
                      await widget.repository.updateCustomEntry(
                        id: existing.id,
                        companyId: _companyId!,
                        entryType: entryType,
                        amount: parsed,
                        entryTime: entryTime,
                        itemPurpose: item.text.trim(),
                        notes: notes.text.trim().isEmpty ? null : notes.text.trim(),
                      );
                    }
                    if (!context.mounted) return;
                    Navigator.pop(context);
                    await _load();
                  } on FormatException {
                    showAppMessage(context, 'Enter valid amount', isError: true);
                  } on ApiException catch (e) {
                    showAppMessage(context, e.message, isError: true);
                  }
                },
                child: Text(existing == null ? 'Save Entry' : 'Update Entry'),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Future<void> _deleteEntry(CustomEntryRowModel row) async {
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
    if (_loading) return const Center(child: CircularProgressIndicator());

    return ListView(
      padding: const EdgeInsets.all(20),
      children: [
        Text('Custom Profit/Cost', style: Theme.of(context).textTheme.headlineMedium),
        const SizedBox(height: 12),
        FinanceSection(
          title: 'Company',
          trailing: IconButton(
            onPressed: _createCompany,
            icon: const Icon(Icons.add_business_outlined),
          ),
          child: _companies.isEmpty
              ? Column(
                  children: [
                    const Text('No company yet. Create your first company.'),
                    const SizedBox(height: 10),
                    ElevatedButton(onPressed: _createCompany, child: const Text('Create Company')),
                  ],
                )
              : Column(
                  children: [
                    DropdownButtonFormField<int>(
                      initialValue: _companyId,
                      isExpanded: true,
                      items: _companies
                          .map((c) => DropdownMenuItem<int>(value: c.id, child: Text(c.name)))
                          .toList(),
                      onChanged: (v) async {
                        setState(() => _companyId = v);
                        await _load();
                      },
                      decoration: const InputDecoration(labelText: 'Select Company'),
                    ),
                    const SizedBox(height: 10),
                    Row(
                      children: [
                        Expanded(
                          child: OutlinedButton(
                            onPressed: () {
                              final c = _companies.firstWhere((x) => x.id == _companyId);
                              _editCompany(c);
                            },
                            child: const Text('Edit Company'),
                          ),
                        ),
                        const SizedBox(width: 10),
                        Expanded(
                          child: OutlinedButton(
                            onPressed: () {
                              final c = _companies.firstWhere((x) => x.id == _companyId);
                              _deleteCompany(c);
                            },
                            child: const Text('Delete Company'),
                          ),
                        ),
                      ],
                    )
                  ],
                ),
        ),
        if (_companyId != null && _data != null) ...[
          const SizedBox(height: 12),
          Wrap(
            spacing: 10,
            runSpacing: 10,
            children: [
              BalancePill(label: 'Total Profit', value: '+${formatBdt(_data!.summary.totalProfitBdt)}', tone: BalancePillTone.receivable),
              BalancePill(label: 'Total Loss', value: '-${formatBdt(_data!.summary.totalLossBdt)}', tone: BalancePillTone.payable),
              BalancePill(
                label: 'Net',
                value: _data!.summary.netBdt >= 0
                    ? '+${formatBdt(_data!.summary.netBdt)}'
                    : '-${formatBdt(_data!.summary.netBdt.abs())}',
                tone: _data!.summary.netBdt >= 0 ? BalancePillTone.netPositive : BalancePillTone.netNegative,
              ),
            ],
          ),
          const SizedBox(height: 12),
          FinanceSection(
            title: 'Filter & Add',
            trailing: IconButton(
              onPressed: () => _upsertEntry(),
              icon: const Icon(Icons.add),
            ),
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
                    suffixIcon: IconButton(onPressed: _load, icon: const Icon(Icons.search)),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 12),
          if (_data!.rows.isEmpty)
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
                                  color: row.entryType == 'PROFIT' ? Colors.green.shade700 : Colors.red.shade700,
                                ),
                          ),
                          PopupMenuButton<String>(
                            onSelected: (v) async {
                              if (v == 'edit') {
                                await _upsertEntry(existing: row);
                              } else {
                                await _deleteEntry(row);
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
            )
        ],
      ],
    );
  }
}

