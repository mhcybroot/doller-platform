import 'package:flutter/material.dart';

import '../../shared/models/domain_models.dart';
import '../../shared/widgets/finance_widgets.dart';
import 'custom_filters.dart';

class CustomCompanyPage extends StatelessWidget {
  const CustomCompanyPage({
    super.key,
    required this.companies,
    required this.companySummaries,
    required this.selectedCompanyId,
    required this.onChangedCompanyId,
    required this.onCreateCompany,
    required this.onEditCompany,
    required this.onDeleteCompany,
    required this.onContinue,
    required this.from,
    required this.to,
    required this.timeSort,
    required this.entryTypeFilter,
    required this.searchController,
    required this.allCompanyRows,
    required this.allCompanyProfit,
    required this.allCompanyCost,
    required this.exportingAllPdf,
    required this.onPickDate,
    required this.onTimeSortChanged,
    required this.onEntryTypeFilterChanged,
    required this.onSearchSubmit,
    required this.onSearchTap,
    required this.onExportAllPdf,
  });

  final List<CompanyModel> companies;
  final Map<int, CustomEntrySummaryModel> companySummaries;
  final int? selectedCompanyId;
  final ValueChanged<int?> onChangedCompanyId;
  final VoidCallback onCreateCompany;
  final VoidCallback onEditCompany;
  final VoidCallback onDeleteCompany;
  final VoidCallback onContinue;
  final DateTime from;
  final DateTime to;
  final CustomTimeSort timeSort;
  final CustomEntryTypeFilter entryTypeFilter;
  final TextEditingController searchController;
  final List<CustomEntryRowModel> allCompanyRows;
  final double allCompanyProfit;
  final double allCompanyCost;
  final bool exportingAllPdf;
  final Future<void> Function(bool fromField) onPickDate;
  final ValueChanged<CustomTimeSort> onTimeSortChanged;
  final ValueChanged<CustomEntryTypeFilter> onEntryTypeFilterChanged;
  final Future<void> Function() onSearchSubmit;
  final Future<void> Function() onSearchTap;
  final Future<void> Function() onExportAllPdf;

  @override
  Widget build(BuildContext context) {
    var allProfit = 0.0;
    var allLoss = 0.0;
    for (final summary in companySummaries.values) {
      allProfit += summary.totalProfitBdt;
      allLoss += summary.totalLossBdt;
    }
    final allNet = allProfit - allLoss;

    if (companies.isEmpty) {
      return Scaffold(
        body: ListView(
          padding: const EdgeInsets.all(20),
          children: [
            Text('Custom Profit/Cost',
                style: Theme.of(context).textTheme.headlineMedium),
            const SizedBox(height: 40),
            Center(
              child: Column(
                children: [
                  const Text('No company yet. Create your first company.'),
                  const SizedBox(height: 10),
                  ElevatedButton(
                      onPressed: onCreateCompany,
                      child: const Text('Create Company')),
                ],
              ),
            ),
          ],
        ),
        floatingActionButton: FloatingActionButton(
          onPressed: onCreateCompany,
          child: const Icon(Icons.add),
        ),
      );
    }

    final selectedCompany = selectedCompanyId != null
        ? companies.firstWhere(
            (c) => c.id == selectedCompanyId,
            orElse: () => companies.first,
          )
        : null;
    final selectedSummary = selectedCompanyId != null
        ? companySummaries[selectedCompanyId] ??
            const CustomEntrySummaryModel(
              totalProfitBdt: 0,
              totalLossBdt: 0,
              netBdt: 0,
            )
        : null;
    final allCompanyNet = allCompanyProfit - allCompanyCost;

    return Scaffold(
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          Row(
            children: [
              Expanded(
                child: Text('All Companies Summary',
                    style: Theme.of(context).textTheme.headlineMedium),
              ),
              IconButton(
                onPressed: exportingAllPdf ? null : onExportAllPdf,
                icon: exportingAllPdf
                    ? const SizedBox(
                        width: 20,
                        height: 20,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : const Icon(Icons.picture_as_pdf_outlined),
                tooltip: 'Export All Companies PDF',
              ),
            ],
          ),
          const SizedBox(height: 16),
          Wrap(
            spacing: 10,
            runSpacing: 10,
            children: [
              BalancePill(
                label: 'Total Profit',
                value: '+${formatBdt(allProfit)}',
                tone: BalancePillTone.receivable,
              ),
              BalancePill(
                label: 'Total Cost',
                value: '-${formatBdt(allLoss)}',
                tone: BalancePillTone.payable,
              ),
              BalancePill(
                label: 'Net',
                value: allNet >= 0
                    ? '+${formatBdt(allNet)}'
                    : '-${formatBdt(allNet.abs())}',
                tone: allNet >= 0
                    ? BalancePillTone.netPositive
                    : BalancePillTone.netNegative,
              ),
            ],
          ),
          const SizedBox(height: 12),
          Text('Company Wise', style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 8),
          Card(
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(12),
            ),
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: DropdownButtonFormField<int>(
                initialValue: selectedCompanyId,
                decoration: const InputDecoration(
                  labelText: 'Select Company',
                  border: InputBorder.none,
                ),
                hint: const Text('No company selected'),
                isExpanded: true,
                items: companies.map((company) {
                  return DropdownMenuItem<int>(
                    value: company.id,
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Expanded(child: Text(company.name)),
                      ],
                    ),
                  );
                }).toList(),
                onChanged: (value) => onChangedCompanyId(value),
              ),
            ),
          ),
          if (selectedCompany != null && selectedSummary != null) ...[
            const SizedBox(height: 12),
            Text('Selected: ${selectedCompany.name}',
                style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: 8),
            Wrap(
              spacing: 10,
              runSpacing: 10,
              children: [
                BalancePill(
                  label: 'Total Profit',
                  value: '+${formatBdt(selectedSummary.totalProfitBdt)}',
                  tone: BalancePillTone.receivable,
                ),
                BalancePill(
                  label: 'Total Cost',
                  value: '-${formatBdt(selectedSummary.totalLossBdt)}',
                  tone: BalancePillTone.payable,
                ),
                BalancePill(
                  label: 'Net',
                  value: selectedSummary.netBdt >= 0
                      ? '+${formatBdt(selectedSummary.netBdt)}'
                      : '-${formatBdt(selectedSummary.netBdt.abs())}',
                  tone: selectedSummary.netBdt >= 0
                      ? BalancePillTone.netPositive
                      : BalancePillTone.netNegative,
                ),
              ],
            ),
            const SizedBox(height: 12),
            Row(
              mainAxisAlignment: MainAxisAlignment.end,
              children: [
                TextButton.icon(
                  onPressed: onEditCompany,
                  icon: const Icon(Icons.edit_outlined, size: 18),
                  label: const Text('Edit'),
                ),
                TextButton.icon(
                  onPressed: onDeleteCompany,
                  icon: Icon(
                    Icons.delete_outline,
                    size: 18,
                    color: Theme.of(context).colorScheme.error,
                  ),
                  label: Text(
                    'Delete',
                    style:
                        TextStyle(color: Theme.of(context).colorScheme.error),
                  ),
                ),
              ],
            ),
          ],
          const SizedBox(height: 14),
          ElevatedButton(
            onPressed: selectedCompanyId == null ? null : onContinue,
            child: const Text('Continue'),
          ),
          const SizedBox(height: 18),
          Text('All Companies Summary (Filtered)',
              style: Theme.of(context).textTheme.titleLarge),
          const SizedBox(height: 8),
          Wrap(
            spacing: 10,
            runSpacing: 10,
            children: [
              BalancePill(
                label: 'Total Profit',
                value: '+${formatBdt(allCompanyProfit)}',
                tone: BalancePillTone.receivable,
              ),
              BalancePill(
                label: 'Total Cost',
                value: '-${formatBdt(allCompanyCost)}',
                tone: BalancePillTone.payable,
              ),
              BalancePill(
                label: 'Net',
                value: allCompanyNet >= 0
                    ? '+${formatBdt(allCompanyNet)}'
                    : '-${formatBdt(allCompanyNet.abs())}',
                tone: allCompanyNet >= 0
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
                        onPressed: () => onPickDate(true),
                        child: Text('From ${formatDate(from)}'),
                      ),
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: OutlinedButton(
                        onPressed: () => onPickDate(false),
                        child: Text('To ${formatDate(to)}'),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 10),
                DropdownButtonFormField<CustomTimeSort>(
                  initialValue: timeSort,
                  decoration: const InputDecoration(labelText: 'Time Sort'),
                  items: const [
                    DropdownMenuItem(
                      value: CustomTimeSort.newestFirst,
                      child: Text('Newest First'),
                    ),
                    DropdownMenuItem(
                      value: CustomTimeSort.oldestFirst,
                      child: Text('Oldest First'),
                    ),
                  ],
                  onChanged: (value) {
                    if (value == null) return;
                    onTimeSortChanged(value);
                  },
                ),
                const SizedBox(height: 10),
                DropdownButtonFormField<CustomEntryTypeFilter>(
                  initialValue: entryTypeFilter,
                  decoration: const InputDecoration(labelText: 'Entry Type'),
                  items: const [
                    DropdownMenuItem(
                      value: CustomEntryTypeFilter.all,
                      child: Text('All'),
                    ),
                    DropdownMenuItem(
                      value: CustomEntryTypeFilter.profitOnly,
                      child: Text('Profit Only'),
                    ),
                    DropdownMenuItem(
                      value: CustomEntryTypeFilter.lossOnly,
                      child: Text('Cost Only'),
                    ),
                  ],
                  onChanged: (value) {
                    if (value == null) return;
                    onEntryTypeFilterChanged(value);
                  },
                ),
                const SizedBox(height: 10),
                TextField(
                  controller: searchController,
                  onSubmitted: (_) => onSearchSubmit(),
                  decoration: InputDecoration(
                    labelText: 'Search item/purpose or note',
                    suffixIcon: IconButton(
                      onPressed: onSearchTap,
                      icon: const Icon(Icons.search),
                    ),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 12),
          if (allCompanyRows.isEmpty)
            const EmptyStateCard(
              title: 'No entries',
              message: 'No profit/cost entries found for selected filters.',
            )
          else
            ...allCompanyRows.map(
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
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(row.itemPurpose,
                                    style:
                                        Theme.of(context).textTheme.titleMedium),
                                const SizedBox(height: 4),
                                Text(
                                  row.companyName,
                                  style: Theme.of(context)
                                      .textTheme
                                      .bodySmall
                                      ?.copyWith(
                                        color: Theme.of(context)
                                            .colorScheme
                                            .onSurfaceVariant,
                                      ),
                                ),
                              ],
                            ),
                          ),
                          Text(
                            '${row.entryType == 'PROFIT' ? '+' : '-'}${formatBdt(row.amountBdt)}',
                            style: Theme.of(context)
                                .textTheme
                                .titleMedium
                                ?.copyWith(
                                  color: row.entryType == 'PROFIT'
                                      ? Colors.green.shade700
                                      : Colors.red.shade700,
                                ),
                          ),
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
      floatingActionButton: FloatingActionButton(
        onPressed: onCreateCompany,
        child: const Icon(Icons.add),
      ),
    );
  }
}
