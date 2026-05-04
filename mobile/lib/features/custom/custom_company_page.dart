import 'package:flutter/material.dart';

import '../../shared/models/domain_models.dart';
import '../../shared/widgets/finance_widgets.dart';

class CustomCompanyPage extends StatelessWidget {
  const CustomCompanyPage({
    super.key,
    required this.companies,
    required this.selectedCompanyId,
    required this.onChangedCompanyId,
    required this.onCreateCompany,
    required this.onEditCompany,
    required this.onDeleteCompany,
    required this.onContinue,
  });

  final List<CompanyModel> companies;
  final int? selectedCompanyId;
  final ValueChanged<int?> onChangedCompanyId;
  final VoidCallback onCreateCompany;
  final VoidCallback onEditCompany;
  final VoidCallback onDeleteCompany;
  final VoidCallback onContinue;

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.all(20),
      children: [
        Text('Custom Profit/Cost', style: Theme.of(context).textTheme.headlineMedium),
        const SizedBox(height: 12),
        FinanceSection(
          title: 'Company',
          trailing: IconButton(
            onPressed: onCreateCompany,
            icon: const Icon(Icons.add_business_outlined),
          ),
          child: companies.isEmpty
              ? Column(
                  children: [
                    const Text('No company yet. Create your first company.'),
                    const SizedBox(height: 10),
                    ElevatedButton(onPressed: onCreateCompany, child: const Text('Create Company')),
                  ],
                )
              : Column(
                  children: [
                    DropdownButtonFormField<int>(
                      initialValue: selectedCompanyId,
                      isExpanded: true,
                      items: companies
                          .map((c) => DropdownMenuItem<int>(value: c.id, child: Text(c.name)))
                          .toList(),
                      onChanged: onChangedCompanyId,
                      decoration: const InputDecoration(labelText: 'Select Company'),
                    ),
                    const SizedBox(height: 10),
                    LayoutBuilder(
                      builder: (context, constraints) {
                        final compact = constraints.maxWidth < 340;
                        final editButton = OutlinedButton(
                          onPressed: selectedCompanyId == null ? null : onEditCompany,
                          child: const Text('Edit Company'),
                        );
                        final deleteButton = OutlinedButton(
                          onPressed: selectedCompanyId == null ? null : onDeleteCompany,
                          child: const Text('Delete Company'),
                        );
                        if (compact) {
                          return Column(
                            children: [
                              SizedBox(width: double.infinity, child: editButton),
                              const SizedBox(height: 10),
                              SizedBox(width: double.infinity, child: deleteButton),
                            ],
                          );
                        }
                        return Row(
                          children: [
                            Expanded(child: editButton),
                            const SizedBox(width: 10),
                            Expanded(child: deleteButton),
                          ],
                        );
                      },
                    ),
                  ],
                ),
        ),
        const SizedBox(height: 14),
        ElevatedButton(
          onPressed: selectedCompanyId == null ? null : onContinue,
          child: const Text('Continue'),
        )
      ],
    );
  }
}

