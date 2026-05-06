import 'package:flutter/material.dart';

import '../../shared/models/domain_models.dart';
import '../../shared/services/api_client.dart';
import '../../shared/services/doller_repository.dart';
import '../../shared/widgets/finance_widgets.dart';

class CurrencyManagementScreen extends StatefulWidget {
  const CurrencyManagementScreen({super.key, required this.repository});

  final DollerRepository repository;

  @override
  State<CurrencyManagementScreen> createState() =>
      _CurrencyManagementScreenState();
}

class _CurrencyManagementScreenState extends State<CurrencyManagementScreen> {
  List<CurrencyModel> _currencies = const [];
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() => _loading = true);
    try {
      final currencies = await widget.repository.listOwnerCurrencies();
      if (!mounted) {
        return;
      }
      setState(() {
        _currencies = currencies;
        _loading = false;
      });
    } on ApiException catch (error) {
      if (!mounted) {
        return;
      }
      showAppMessage(context, error.message, isError: true);
      setState(() => _loading = false);
    }
  }

  Future<void> _openForm({CurrencyModel? existing}) async {
    final code = TextEditingController(text: existing?.code ?? '');
    final displayName =
        TextEditingController(text: existing?.displayName ?? '');
    final notes = TextEditingController(text: existing?.notes ?? '');
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
            Text(
              existing == null ? 'Add Currency' : 'Edit Currency',
              style: Theme.of(context).textTheme.titleLarge,
            ),
            const SizedBox(height: 12),
            TextField(
              controller: code,
              textCapitalization: TextCapitalization.characters,
              decoration: const InputDecoration(labelText: 'Currency Code'),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: displayName,
              decoration: const InputDecoration(labelText: 'Display Name'),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: notes,
              decoration: const InputDecoration(labelText: 'Notes'),
            ),
            const SizedBox(height: 16),
            ElevatedButton(
              onPressed: () async {
                try {
                  if (existing == null) {
                    await widget.repository.createCurrency(
                      code: code.text.trim(),
                      displayName: displayName.text.trim(),
                      notes:
                          notes.text.trim().isEmpty ? null : notes.text.trim(),
                    );
                  } else {
                    await widget.repository.updateCurrency(
                      id: existing.id,
                      code: code.text.trim(),
                      displayName: displayName.text.trim(),
                      notes:
                          notes.text.trim().isEmpty ? null : notes.text.trim(),
                    );
                  }
                  if (!context.mounted) {
                    return;
                  }
                  Navigator.pop(context);
                  await _load();
                } on ApiException catch (error) {
                  showAppMessage(context, error.message, isError: true);
                }
              },
              child: Text(existing == null ? 'Save' : 'Update'),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _deleteCurrency(CurrencyModel currency) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (_) => AlertDialog(
        title: const Text('Delete Currency'),
        content: Text('Delete ${currency.code}?'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Cancel'),
          ),
          ElevatedButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Delete'),
          ),
        ],
      ),
    );
    if (confirmed != true) {
      return;
    }
    try {
      await widget.repository.deleteCurrency(currency.id);
      if (!mounted) {
        return;
      }
      await _load();
    } on ApiException catch (error) {
      if (!mounted) {
        return;
      }
      showAppMessage(context, error.message, isError: true);
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) {
      return const Center(child: CircularProgressIndicator());
    }
    return ListView(
      padding: const EdgeInsets.all(20),
      children: [
        FinanceSection(
          title: 'Currency Management',
          trailing: IconButton(
            onPressed: () => _openForm(),
            icon: const Icon(Icons.add),
          ),
          child: _currencies.isEmpty
              ? const Padding(
                  padding: EdgeInsets.symmetric(vertical: 16),
                  child: Text('No currencies configured yet.'),
                )
              : Column(
                  children: _currencies
                      .map(
                        (currency) => ListTile(
                          contentPadding: EdgeInsets.zero,
                          title: Text(currency.displayName),
                          subtitle: Text(
                            '${currency.code}${currency.notes == null ? '' : '  ${currency.notes}'}',
                          ),
                          trailing: Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              IconButton(
                                onPressed: () => _openForm(existing: currency),
                                icon: const Icon(Icons.edit_outlined),
                              ),
                              IconButton(
                                onPressed: () => _deleteCurrency(currency),
                                icon: const Icon(Icons.delete_outline),
                              ),
                            ],
                          ),
                        ),
                      )
                      .toList(),
                ),
        ),
      ],
    );
  }
}
