import 'package:flutter/material.dart';

import '../../shared/models/domain_models.dart';
import '../../shared/services/api_client.dart';
import '../../shared/services/doller_repository.dart';
import '../../shared/widgets/finance_widgets.dart';
import 'custom_company_page.dart';
import 'custom_summary_page.dart';

class CustomProfitCostHome extends StatefulWidget {
  const CustomProfitCostHome({super.key, required this.repository});

  final DollerRepository repository;

  @override
  State<CustomProfitCostHome> createState() => _CustomProfitCostHomeState();
}

class _CustomProfitCostHomeState extends State<CustomProfitCostHome> {
  List<CompanyModel> _companies = const [];
  int? _companyId;
  DateTime? _from;
  DateTime? _to;
  Map<int, CustomEntrySummaryModel> _companySummaries = const {};
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _loadCompanies();
  }

  Future<void> _loadCompanies() async {
    setState(() => _loading = true);
    try {
      final now = DateTime.now();
      _from ??= DateTime(now.year, now.month, 1);
      _to ??= now;
      final companies = await widget.repository.listCompanies();
      final summaries = <int, CustomEntrySummaryModel>{};
      if (companies.isNotEmpty) {
        final responses = await Future.wait(
          companies.map(
            (company) => widget.repository.listCustomEntries(
              companyId: company.id,
              from: _from!,
              to: _to!,
            ),
          ),
        );
        for (var i = 0; i < companies.length; i++) {
          summaries[companies[i].id] = responses[i].summary;
        }
      }
      if (!mounted) return;
      setState(() {
        _companies = companies;
        _companySummaries = summaries;
        _loading = false;
      });
    } on ApiException catch (e) {
      if (!mounted) return;
      showAppMessage(context, e.message, isError: true);
      setState(() => _loading = false);
    }
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
            Text('Create Company',
                style: Theme.of(context).textTheme.titleLarge),
            const SizedBox(height: 12),
            TextField(
                controller: name,
                decoration: const InputDecoration(labelText: 'Company Name')),
            const SizedBox(height: 12),
            TextField(
                controller: notes,
                decoration: const InputDecoration(labelText: 'Notes')),
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
                  await _loadCompanies();
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
            TextField(
                controller: name,
                decoration: const InputDecoration(labelText: 'Company Name')),
            const SizedBox(height: 12),
            TextField(
                controller: notes,
                decoration: const InputDecoration(labelText: 'Notes')),
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
                  await _loadCompanies();
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
          TextButton(
              onPressed: () => Navigator.pop(context, false),
              child: const Text('Cancel')),
          ElevatedButton(
              onPressed: () => Navigator.pop(context, true),
              child: const Text('Delete')),
        ],
      ),
    );
    if (confirm != true) return;
    try {
      await widget.repository.deleteCompany(company.id);
      if (!mounted) return;
      await _loadCompanies();
    } on ApiException catch (e) {
      showAppMessage(context, e.message, isError: true);
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) return const Center(child: CircularProgressIndicator());
    CompanyModel? selected;
    for (final c in _companies) {
      if (c.id == _companyId) {
        selected = c;
        break;
      }
    }

    return CustomCompanyPage(
      companies: _companies,
      companySummaries: _companySummaries,
      selectedCompanyId: _companyId,
      onChangedCompanyId: (v) => setState(() => _companyId = v),
      onCreateCompany: _createCompany,
      onEditCompany: () {
        if (selected != null) _editCompany(selected);
      },
      onDeleteCompany: () {
        if (selected != null) _deleteCompany(selected);
      },
      onContinue: () {
        if (_companyId == null) return;
        Navigator.of(context).push(
          MaterialPageRoute(
            builder: (_) => CustomSummaryPage(
              repository: widget.repository,
              companyId: _companyId!,
              companyName: selected?.name ?? 'Company',
            ),
          ),
        );
      },
    );
  }
}
