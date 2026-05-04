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

    return Scaffold(
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          Text('All Companies Summary',
              style: Theme.of(context).textTheme.headlineMedium),
          const SizedBox(height: 8),
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
                label: 'Total Loss',
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
          FinanceSection(
            title: 'Company Wise',
            child: Column(
              children: companies.map((company) {
                final summary = companySummaries[company.id] ??
                    const CustomEntrySummaryModel(
                      totalProfitBdt: 0,
                      totalLossBdt: 0,
                      netBdt: 0,
                    );
                final isSelected = company.id == selectedCompanyId;
                return Card(
                  margin: const EdgeInsets.only(bottom: 8),
                  child: ListTile(
                    selected: isSelected,
                    onTap: () => onChangedCompanyId(company.id),
                    title: Text(company.name),
                    subtitle: Text(
                      'P: ${formatBdt(summary.totalProfitBdt)} | C: ${formatBdt(summary.totalLossBdt)} | N: ${summary.netBdt >= 0 ? '+' : '-'}${formatBdt(summary.netBdt.abs())}',
                    ),
                    trailing: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        if (isSelected) const Icon(Icons.check_circle),
                        PopupMenuButton<String>(
                          onSelected: (value) async {
                            if (value == 'edit') {
                              onEditCompany();
                              return;
                            }
                            if (value == 'delete') {
                              onDeleteCompany();
                            }
                          },
                          itemBuilder: (context) => const [
                            PopupMenuItem(value: 'edit', child: Text('Edit')),
                            PopupMenuItem(
                                value: 'delete', child: Text('Delete')),
                          ],
                          child: const Icon(Icons.more_vert),
                        ),
                      ],
                    ),
                  ),
                );
              }).toList(),
            ),
          ),
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
