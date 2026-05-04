import 'package:flutter/material.dart';

import '../../shared/models/domain_models.dart';
import '../../shared/widgets/finance_widgets.dart';

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
  });

  final List<CompanyModel> companies;
  final Map<int, CustomEntrySummaryModel> companySummaries;
  final int? selectedCompanyId;
  final ValueChanged<int?> onChangedCompanyId;
  final VoidCallback onCreateCompany;
  final VoidCallback onEditCompany;
  final VoidCallback onDeleteCompany;
  final VoidCallback onContinue;

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

    return Scaffold(
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          Text('All Companies Summary',
              style: Theme.of(context).textTheme.headlineMedium),
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
                  final summary = companySummaries[company.id] ??
                      const CustomEntrySummaryModel(
                        totalProfitBdt: 0,
                        totalLossBdt: 0,
                        netBdt: 0,
                      );
                  return DropdownMenuItem<int>(
                    value: company.id,
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Expanded(child: Text(company.name)),
                        // Text(
                        //   'P: ${formatBdt(summary.totalProfitBdt)} | L: ${formatBdt(summary.totalLossBdt)}',
                        //   style:
                        //       Theme.of(context).textTheme.bodySmall?.copyWith(
                        //             color: Theme.of(context)
                        //                 .colorScheme
                        //                 .onSurfaceVariant,
                        //           ),
                        // ),
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
          )
        ],
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: onCreateCompany,
        child: const Icon(Icons.add),
      ),
    );
  }
}
