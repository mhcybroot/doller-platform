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
  List<CompanyModel> _companies = const [];
  int? _selectedCompanyId;
  Map<int, CustomEntryListModel> _companyDataById = const {};
  bool _loading = true;
  _TimeSort _timeSort = _TimeSort.newestFirst;

  @override
  void initState() {
    super.initState();
    final now = DateTime.now();
    _from = DateTime(now.year, now.month, 1);
    _to = now;
    _selectedCompanyId = widget.companyId;
    _load();
  }

  Future<void> _load() async {
    setState(() => _loading = true);
    try {
      final companies = await widget.repository.listCompanies();
      final selectedExists =
          companies.any((company) => company.id == _selectedCompanyId);
      final nextSelectedId = companies.isEmpty
          ? null
          : selectedExists
              ? _selectedCompanyId
              : companies.first.id;
      final dataByCompany = <int, CustomEntryListModel>{};
      if (companies.isNotEmpty) {
        final responses = await Future.wait(
          companies.map(
            (company) => widget.repository.listCustomEntries(
              companyId: company.id,
              from: _from,
              to: _to,
              search: _search.text,
            ),
          ),
        );
        for (var i = 0; i < companies.length; i++) {
          dataByCompany[companies[i].id] = responses[i];
        }
      }
      if (!mounted) return;
      setState(() {
        _companies = companies;
        _selectedCompanyId = nextSelectedId;
        _companyDataById = dataByCompany;
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
    final selectedId = _selectedCompanyId;
    if (selectedId == null) return;
    await Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => CustomEntryFormPage(
          repository: widget.repository,
          companyId: selectedId,
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
      if (!mounted) return;
      showAppMessage(context, e.message, isError: true);
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) return const Scaffold(body: Center(child: CircularProgressIndicator()));
    if (_companies.isEmpty) {
      return Scaffold(
        appBar: AppBar(title: const Text('Custom Profit/Cost Summary')),
        body: const Padding(
          padding: EdgeInsets.all(20),
          child: EmptyStateCard(
            title: 'No company',
            message: 'Create a company first to view summary and entries.',
          ),
        ),
      );
    }
    final selectedCompany = _companies.firstWhere(
      (company) => company.id == _selectedCompanyId,
      orElse: () => _companies.first,
    );
    final selectedData = _companyDataById[selectedCompany.id];
    if (selectedData == null) return const Scaffold(body: SizedBox.shrink());
    final allSummary = _allCompaniesSummary();
    final displayRows = List<CustomEntryRowModel>.from(selectedData.rows)
      ..sort(
        (a, b) => _timeSort == _TimeSort.newestFirst
            ? b.entryTime.compareTo(a.entryTime)
            : a.entryTime.compareTo(b.entryTime),
      );
    return Scaffold(
      appBar: AppBar(title: const Text('Custom Profit/Cost Summary')),
      floatingActionButton: FloatingActionButton(
        onPressed: () => _openForm(),
        child: const Icon(Icons.add),
      ),
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          // Text('${selectedCompany.name}', style: Theme.of(context).textTheme.titleMedium),
          Text(' ${selectedCompany.name}',
              style: Theme.of(context).textTheme.titleLarge),
          // const SizedBox(height: 8),
          // Wrap(
          //   spacing: 10,
          //   runSpacing: 10,
          //   children: [
          //     BalancePill(
          //       label: 'Total Profit',
          //       value: '+${formatBdt(allSummary.totalProfitBdt)}',
          //       tone: BalancePillTone.receivable,
          //     ),
          //     BalancePill(
          //       label: 'Total Loss',
          //       value: '-${formatBdt(allSummary.totalLossBdt)}',
          //       tone: BalancePillTone.payable,
          //     ),
          //     BalancePill(
          //       label: 'Net',
          //       value: allSummary.netBdt >= 0
          //           ? '+${formatBdt(allSummary.netBdt)}'
          //           : '-${formatBdt(allSummary.netBdt.abs())}',
          //       tone: allSummary.netBdt >= 0
          //           ? BalancePillTone.netPositive
          //           : BalancePillTone.netNegative,
          //     ),
          //   ],
          // ),
          // const SizedBox(height: 12),
          // FinanceSection(
          //   title: 'Company Wise',
          //   child: Column(
          //     children: _companies.map((company) {
          //       final data = _companyDataById[company.id];
          //       final summary = data?.summary ??
          //           const CustomEntrySummaryModel(
          //             totalProfitBdt: 0,
          //             totalLossBdt: 0,
          //             netBdt: 0,
          //           );
          //       final isSelected = company.id == selectedCompany.id;
          //       return Card(
          //         margin: const EdgeInsets.only(bottom: 8),
          //         child: ListTile(
          //           selected: isSelected,
          //           onTap: () => setState(() => _selectedCompanyId = company.id),
          //           title: Text(company.name),
          //           subtitle: Text(
          //             'P: ${formatBdt(summary.totalProfitBdt)} | C: ${formatBdt(summary.totalLossBdt)} | N: ${summary.netBdt >= 0 ? '+' : '-'}${formatBdt(summary.netBdt.abs())}',
          //           ),
          //           trailing: isSelected ? const Icon(Icons.check_circle) : null,
          //         ),
          //       );
          //     }).toList(),
          //   ),
          // ),
          // const SizedBox(height: 12),
          // Text('Selected Company: ${selectedCompany.name}',
          //     style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 8),
          Wrap(
            spacing: 10,
            runSpacing: 10,
            children: [
              BalancePill(
                label: 'Total Profit',
                value: '+${formatBdt(selectedData.summary.totalProfitBdt)}',
                tone: BalancePillTone.receivable,
              ),
              BalancePill(
                label: 'Total Cost',
                value: '-${formatBdt(selectedData.summary.totalLossBdt)}',
                tone: BalancePillTone.payable,
              ),
              BalancePill(
                label: 'Net',
                value: selectedData.summary.netBdt >= 0
                    ? '+${formatBdt(selectedData.summary.netBdt)}'
                    : '-${formatBdt(selectedData.summary.netBdt.abs())}',
                tone: selectedData.summary.netBdt >= 0
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
                DropdownButtonFormField<_TimeSort>(
                  initialValue: _timeSort,
                  decoration: const InputDecoration(labelText: 'Time Sort'),
                  items: const [
                    DropdownMenuItem(
                      value: _TimeSort.newestFirst,
                      child: Text('Newest First'),
                    ),
                    DropdownMenuItem(
                      value: _TimeSort.oldestFirst,
                      child: Text('Oldest First'),
                    ),
                  ],
                  onChanged: (value) {
                    if (value == null) return;
                    setState(() => _timeSort = value);
                  },
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
          if (displayRows.isEmpty)
            const EmptyStateCard(
              title: 'No entries',
              message: 'Add profit/cost entries for selected filters.',
            )
          else
            ...displayRows.map(
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

  CustomEntrySummaryModel _allCompaniesSummary() {
    var totalProfit = 0.0;
    var totalLoss = 0.0;
    for (final data in _companyDataById.values) {
      totalProfit += data.summary.totalProfitBdt;
      totalLoss += data.summary.totalLossBdt;
    }
    return CustomEntrySummaryModel(
      totalProfitBdt: totalProfit,
      totalLossBdt: totalLoss,
      netBdt: totalProfit - totalLoss,
    );
  }
}

enum _TimeSort { newestFirst, oldestFirst }
