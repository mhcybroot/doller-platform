import 'package:flutter/material.dart';

import '../../shared/models/domain_models.dart';
import '../../shared/widgets/finance_widgets.dart';

class CustomCompanyPage extends StatefulWidget {
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
  State<CustomCompanyPage> createState() => _CustomCompanyPageState();
}

class _CustomCompanyPageState extends State<CustomCompanyPage> {
  int? _expandedCompanyId;

  @override
  Widget build(BuildContext context) {
    var allProfit = 0.0;
    var allLoss = 0.0;
    for (final summary in widget.companySummaries.values) {
      allProfit += summary.totalProfitBdt;
      allLoss += summary.totalLossBdt;
    }
    final allNet = allProfit - allLoss;

    if (widget.companies.isEmpty) {
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
                      onPressed: widget.onCreateCompany,
                      child: const Text('Create Company')),
                ],
              ),
            ),
          ],
        ),
        floatingActionButton: FloatingActionButton(
          onPressed: widget.onCreateCompany,
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
          FinanceSection(
            title: 'Company Wise',
            child: Column(
              children: widget.companies.map((company) {
                final summary = widget.companySummaries[company.id] ??
                    const CustomEntrySummaryModel(
                      totalProfitBdt: 0,
                      totalLossBdt: 0,
                      netBdt: 0,
                    );
                final isSelected = company.id == widget.selectedCompanyId;
                final isExpanded = _expandedCompanyId == company.id;
                return Card(
                  key: ValueKey(company.id),
                  margin: const EdgeInsets.only(bottom: 8),
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(12),
                    side: BorderSide(
                      color: isSelected
                          ? Theme.of(context).colorScheme.primary
                          : Theme.of(context)
                              .colorScheme
                              .outline
                              .withValues(alpha: 0.2),
                      width: isSelected ? 1.5 : 1,
                    ),
                  ),
                  child: Column(
                    children: [
                      ListTile(
                        onTap: () {
                          setState(() {
                            if (_expandedCompanyId == company.id) {
                              _expandedCompanyId = null;
                            } else {
                              _expandedCompanyId = company.id;
                            }
                          });
                        },
                        contentPadding: const EdgeInsets.symmetric(
                            horizontal: 16, vertical: 4),
                        leading: CircleAvatar(
                          backgroundColor:
                              Theme.of(context).colorScheme.primaryContainer,
                          radius: 20,
                          child: Text(
                            company.name.isNotEmpty
                                ? company.name[0].toUpperCase()
                                : 'C',
                            style: TextStyle(
                              color: Theme.of(context)
                                  .colorScheme
                                  .onPrimaryContainer,
                              fontWeight: FontWeight.bold,
                              fontSize: 16,
                            ),
                          ),
                        ),
                        title: Text(
                          company.name,
                          style:
                              Theme.of(context).textTheme.titleMedium?.copyWith(
                                    fontWeight: FontWeight.w600,
                                    letterSpacing: 0.15,
                                  ),
                        ),
                        trailing: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            if (isSelected)
                              const Icon(Icons.check_circle, size: 20),
                            Icon(
                              isExpanded
                                  ? Icons.expand_less
                                  : Icons.expand_more,
                              color: Theme.of(context)
                                  .colorScheme
                                  .onSurfaceVariant,
                            ),
                          ],
                        ),
                      ),
                      if (isExpanded)
                        Padding(
                          padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
                          child: Wrap(spacing: 8, runSpacing: 8, children: [
                            BalancePill(
                              label: 'Total Profit',
                              value: '${formatBdt(summary.totalProfitBdt)}',
                              tone: BalancePillTone.receivable,
                            ),
                            BalancePill(
                              label: 'Total Cost',
                              value: '${formatBdt(summary.totalLossBdt)}',
                              tone: BalancePillTone.payable,
                            ),
                            BalancePill(
                              label: 'Net',
                              value: summary.netBdt >= 0
                                  ? '+${formatBdt(summary.netBdt)}'
                                  : '-${formatBdt(summary.netBdt.abs())}',
                              tone: summary.netBdt >= 0
                                  ? BalancePillTone.netPositive
                                  : BalancePillTone.netNegative,
                            ),
                          ]),
                        ),
                      if (isExpanded)
                        Padding(
                          padding: const EdgeInsets.fromLTRB(8, 0, 8, 8),
                          child: Row(
                            mainAxisAlignment: MainAxisAlignment.end,
                            children: [
                              TextButton.icon(
                                onPressed: () {
                                  widget.onChangedCompanyId(company.id);
                                },
                                icon: Icon(
                                  isSelected
                                      ? Icons.remove_circle_outline
                                      : Icons.add_circle_outline,
                                  size: 18,
                                ),
                                label: Text(isSelected ? 'Deselect' : 'Select'),
                              ),
                              const SizedBox(width: 8),
                              TextButton.icon(
                                onPressed: widget.onEditCompany,
                                icon: const Icon(Icons.edit_outlined, size: 18),
                                label: const Text('Edit'),
                              ),
                              TextButton.icon(
                                onPressed: widget.onDeleteCompany,
                                icon: Icon(
                                  Icons.delete_outline,
                                  size: 18,
                                  color: Theme.of(context).colorScheme.error,
                                ),
                                label: Text(
                                  'Delete',
                                  style: TextStyle(
                                      color:
                                          Theme.of(context).colorScheme.error),
                                ),
                              ),
                            ],
                          ),
                        ),
                    ],
                  ),
                );
              }).toList(),
            ),
          ),
          const SizedBox(height: 14),
          ElevatedButton(
            onPressed:
                widget.selectedCompanyId == null ? null : widget.onContinue,
            child: const Text('Continue'),
          )
        ],
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: widget.onCreateCompany,
        child: const Icon(Icons.add),
      ),
    );
  }
}
