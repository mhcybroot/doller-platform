import 'package:flutter/material.dart';

import '../../shared/models/domain_models.dart';
import '../../shared/services/api_client.dart';
import '../../shared/services/doller_repository.dart';
import '../../shared/widgets/finance_widgets.dart';

class PartiesScreen extends StatefulWidget {
  const PartiesScreen({super.key, required this.repository});

  final DollerRepository repository;

  @override
  State<PartiesScreen> createState() => _PartiesScreenState();
}

class _PartiesScreenState extends State<PartiesScreen> {
  final _search = TextEditingController();
  List<PartyModel> _parties = const [];
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() => _loading = true);
    try {
      final parties = await widget.repository.listParties();
      if (!mounted) {
        return;
      }
      setState(() {
        _parties = parties;
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

  Future<void> _createParty() async {
    final name = TextEditingController();
    final phone = TextEditingController();
    final notes = TextEditingController();
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      builder: (context) {
        return Padding(
          padding: EdgeInsets.fromLTRB(
            20,
            24,
            20,
            MediaQuery.of(context).viewInsets.bottom + 24,
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text('Create Party',
                  style: Theme.of(context).textTheme.titleLarge),
              const SizedBox(height: 16),
              TextField(
                  controller: name,
                  decoration: const InputDecoration(labelText: 'Name')),
              const SizedBox(height: 12),
              TextField(
                  controller: phone,
                  decoration: const InputDecoration(labelText: 'Phone')),
              const SizedBox(height: 12),
              TextField(
                  controller: notes,
                  decoration: const InputDecoration(labelText: 'Notes')),
              const SizedBox(height: 18),
              ElevatedButton(
                onPressed: () async {
                  try {
                    await widget.repository.createParty(
                        name.text.trim(), phone.text.trim(), notes.text.trim());
                    if (!context.mounted) {
                      return;
                    }
                    Navigator.pop(context);
                  } on ApiException catch (error) {
                    showAppMessage(context, error.message, isError: true);
                  }
                },
                child: const Text('Save Party'),
              ),
            ],
          ),
        );
      },
    );
    await _load();
  }

  Future<void> _openLedger(PartyModel party) async {
    try {
      final ledger = await widget.repository.partyLedger(party.id);
      if (!mounted) {
        return;
      }
      await showModalBottomSheet<void>(
        context: context,
        isScrollControlled: true,
        builder: (context) {
          return DraggableScrollableSheet(
            expand: false,
            initialChildSize: 0.84,
            builder: (context, controller) {
              return Padding(
                padding: const EdgeInsets.all(20),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(ledger.partyName,
                        style: Theme.of(context).textTheme.titleLarge),
                    const SizedBox(height: 10),
                    Wrap(
                      spacing: 10,
                      runSpacing: 10,
                      children: [
                        BalancePill(
                          label: 'Receivable',
                          value: formatBdt(ledger.balances.receivableBdt),
                          tone: BalancePillTone.receivable,
                        ),
                        BalancePill(
                          label: 'Payable',
                          value: formatBdt(ledger.balances.payableBdt),
                          tone: BalancePillTone.payable,
                        ),
                        BalancePill(
                          label: 'Advance In',
                          value: formatBdt(ledger.balances.advanceFromPartyBdt),
                          tone: BalancePillTone.advanceIn,
                        ),
                        BalancePill(
                          label: 'Advance Out',
                          value: formatBdt(ledger.balances.advanceToPartyBdt),
                          tone: BalancePillTone.advanceOut,
                        ),
                        BalancePill(
                          label: 'Net Position',
                          value: formatBdt(ledger.balances.netBalanceBdt.abs()),
                          tone: ledger.balances.netBalanceBdt >= 0
                              ? BalancePillTone.netPositive
                              : BalancePillTone.netNegative,
                        ),
                      ],
                    ),
                    const SizedBox(height: 16),
                    Expanded(
                      child: ListView.builder(
                        controller: controller,
                        itemCount: ledger.lines.length,
                        itemBuilder: (context, index) {
                          final line = ledger.lines[index];
                          return ListTile(
                            contentPadding: EdgeInsets.zero,
                            title: Text(line.kind),
                            subtitle: Text(
                                '${formatDateTime(line.time)}  ${line.note ?? ''}'),
                            trailing: Text(
                              formatBdt(line.amount),
                              style: TextStyle(
                                color: line.amount >= 0
                                    ? Colors.black
                                    : Colors.red.shade700,
                                fontWeight: FontWeight.w700,
                              ),
                            ),
                          );
                        },
                      ),
                    ),
                  ],
                ),
              );
            },
          );
        },
      );
    } on ApiException catch (error) {
      if (!mounted) {
        return;
      }
      showAppMessage(context, error.message, isError: true);
    }
  }

  @override
  Widget build(BuildContext context) {
    final filtered = _parties.where((party) {
      final q = _search.text.trim().toLowerCase();
      return q.isEmpty ||
          party.name.toLowerCase().contains(q) ||
          (party.phone ?? '').contains(q);
    }).toList();

    if (_loading) {
      return const Center(child: CircularProgressIndicator());
    }

    return ListView(
      padding: const EdgeInsets.all(20),
      children: [
        Row(
          children: [
            Expanded(
                child: Text('Parties',
                    style: Theme.of(context).textTheme.headlineMedium)),
            IconButton(
                onPressed: _createParty,
                icon: const Icon(Icons.add_circle_outline)),
          ],
        ),
        const SizedBox(height: 8),
        TextField(
          controller: _search,
          onChanged: (_) => setState(() {}),
          decoration: const InputDecoration(
            labelText: 'Search party by name or phone',
            prefixIcon: Icon(Icons.search),
          ),
        ),
        const SizedBox(height: 18),
        if (filtered.isEmpty)
          EmptyStateCard(
            title: 'No parties yet',
            message:
                'Create your counterparty list first so trading forms can use selectors instead of manual IDs.',
            action: ElevatedButton(
                onPressed: _createParty, child: const Text('Create Party')),
          )
        else
          ...filtered.map(
            (party) => Card(
              child: ListTile(
                onTap: () => _openLedger(party),
                title: Text(party.name),
                subtitle:
                    Text('${party.phone ?? 'No phone'}  ${party.notes ?? ''}'),
                trailing: const Icon(Icons.chevron_right),
              ),
            ),
          ),
      ],
    );
  }
}
