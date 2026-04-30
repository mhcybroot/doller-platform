import 'package:flutter/material.dart';

import '../../shared/models/auth_models.dart';
import '../../shared/models/domain_models.dart';
import '../../shared/services/api_client.dart';
import '../../shared/services/app_logger.dart';
import '../../shared/services/doller_repository.dart';
import '../../shared/widgets/finance_widgets.dart';

class PartiesScreen extends StatefulWidget {
  const PartiesScreen(
      {super.key, required this.repository, required this.session});

  final DollerRepository repository;
  final AuthSession session;

  @override
  State<PartiesScreen> createState() => _PartiesScreenState();
}

class _PartiesScreenState extends State<PartiesScreen> {
  final _search = TextEditingController();
  List<PartyModel> _parties = const [];
  bool _loading = true;
  bool _networkError = false;

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
        _networkError = false;
        _loading = false;
      });
    } on ApiException catch (error) {
      if (!mounted) {
        return;
      }
      showAppMessage(context, error.message, isError: true);
      setState(() {
        _parties = const [];
        _networkError = error.isNetworkError;
        _loading = false;
      });
    }
  }

  Future<void> _createParty() async {
    final name = TextEditingController();
    final phone = TextEditingController();
    final address = TextEditingController();
    final notes = TextEditingController();
    final openingReceivable = TextEditingController();
    final openingPayable = TextEditingController();
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
                  controller: address,
                  decoration: const InputDecoration(labelText: 'Address')),
              const SizedBox(height: 12),
              TextField(
                  controller: notes,
                  decoration: const InputDecoration(labelText: 'Notes')),
              const SizedBox(height: 12),
              TextField(
                  controller: openingReceivable,
                  keyboardType: TextInputType.number,
                  decoration: const InputDecoration(
                      labelText: 'Opening Receivable (BDT)')),
              const SizedBox(height: 12),
              TextField(
                  controller: openingPayable,
                  keyboardType: TextInputType.number,
                  decoration: const InputDecoration(
                      labelText: 'Opening Payable (BDT)')),
              const SizedBox(height: 18),
              ElevatedButton(
                onPressed: () async {
                  try {
                    final openingReceivableValue = double.tryParse(
                        openingReceivable.text.trim().isEmpty
                            ? '0'
                            : openingReceivable.text.trim());
                    final openingPayableValue = double.tryParse(
                        openingPayable.text.trim().isEmpty
                            ? '0'
                            : openingPayable.text.trim());
                    if (openingReceivableValue == null ||
                        openingPayableValue == null ||
                        openingReceivableValue < 0 ||
                        openingPayableValue < 0) {
                      throw const ApiException(
                          'Opening balances must be valid positive numbers');
                    }
                    await widget.repository.createParty(
                      name.text.trim(),
                      phone.text.trim(),
                      address.text.trim(),
                      notes.text.trim(),
                      openingReceivableBdt: openingReceivableValue,
                      openingPayableBdt: openingPayableValue,
                    );
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

  Future<void> _editParty(PartyModel party) async {
    final name = TextEditingController(text: party.name);
    final phone = TextEditingController(text: party.phone ?? '');
    final address = TextEditingController(text: party.address ?? '');
    final notes = TextEditingController(text: party.notes ?? '');
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
              Text('Edit Party', style: Theme.of(context).textTheme.titleLarge),
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
                  controller: address,
                  decoration: const InputDecoration(labelText: 'Address')),
              const SizedBox(height: 12),
              TextField(
                  controller: notes,
                  decoration: const InputDecoration(labelText: 'Notes')),
              const SizedBox(height: 18),
              ElevatedButton(
                onPressed: () async {
                  try {
                    await widget.repository.updateParty(
                      party.id,
                      name.text.trim(),
                      phone.text.trim(),
                      address.text.trim(),
                      notes.text.trim(),
                    );
                    if (!context.mounted) {
                      return;
                    }
                    Navigator.pop(context);
                  } on ApiException catch (error) {
                    showAppMessage(context, error.message, isError: true);
                  }
                },
                child: const Text('Update Party'),
              ),
            ],
          ),
        );
      },
    );
    await _load();
  }

  Future<void> _deleteParty(PartyModel party) async {
    final shouldDelete = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Delete Party'),
        content: Text('Delete ${party.name}? This is an owner-only action.'),
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
    if (shouldDelete != true) {
      return;
    }
    try {
      await widget.repository.deleteParty(party.id);
      if (!mounted) {
        return;
      }
      showAppMessage(context, 'Party deleted');
      await _load();
    } on ApiException catch (error) {
      if (!mounted) {
        return;
      }
      showAppMessage(context, error.message, isError: true);
    }
  }

  Future<void> _openLedger(PartyModel party) async {
    AppLogger.log('screen:party-ledger', 'open:start', fields: {
      'partyId': party.id,
      'partyName': party.name,
    });
    try {
      final ledger = await widget.repository.partyLedger(party.id);
      if (!mounted) {
        return;
      }
      AppLogger.log('screen:party-ledger', 'open:success', fields: {
        'partyId': party.id,
        'partyName': party.name,
        'receivableBdt': ledger.balances.receivableBdt,
        'payableBdt': ledger.balances.payableBdt,
        'netBalanceBdt': ledger.balances.netBalanceBdt,
        'lineCount': ledger.lines.length,
      });
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
                          value: _formatSignedAmount(
                            ledger.balances.receivableBdt,
                            positiveSign: '+',
                          ),
                          tone: BalancePillTone.receivable,
                        ),
                        BalancePill(
                          label: 'Payable',
                          value: _formatSignedAmount(
                            ledger.balances.payableBdt,
                            positiveSign: '+',
                          ),
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
                          value: _formatSignedAmount(
                              ledger.balances.netBalanceBdt),
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
                              _formatSignedAmount(line.amount),
                              style: TextStyle(
                                color: line.amount >= 0
                                    ? Colors.green.shade700
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
      AppLogger.log('screen:party-ledger', 'open:error', fields: {
        'partyId': party.id,
        'message': error.message,
        'isNetworkError': error.isNetworkError,
      });
      showAppMessage(context, error.message, isError: true);
    }
  }

  @override
  Widget build(BuildContext context) {
    final filtered = _parties.where((party) {
      final q = _search.text.trim().toLowerCase();
      return q.isEmpty ||
          party.name.toLowerCase().contains(q) ||
          (party.phone ?? '').contains(q) ||
          (party.address ?? '').toLowerCase().contains(q);
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
            labelText: 'Search party by name, phone, or address',
            prefixIcon: Icon(Icons.search),
          ),
        ),
        const SizedBox(height: 18),
        if (filtered.isEmpty)
          EmptyStateCard(
            title: _networkError ? 'No internet connection' : 'No parties yet',
            message: _networkError
                ? 'Please check your network and try again.'
                : 'Create your counterparty list first so trading forms can use selectors instead of manual IDs.',
            action: ElevatedButton(
              onPressed: _networkError ? _load : _createParty,
              child: Text(_networkError ? 'Retry' : 'Create Party'),
            ),
          )
        else
          ...filtered.map(
            (party) => Card(
              child: ListTile(
                onTap: () => _openLedger(party),
                title: Text(party.name),
                subtitle: Text(
                    '${party.phone ?? 'No phone'} • ${party.address ?? 'No address'} • ${party.notes ?? ''}'),
                trailing: widget.session.isOwner
                    ? PopupMenuButton<String>(
                        onSelected: (value) async {
                          if (value == 'edit') {
                            await _editParty(party);
                            return;
                          }
                          if (value == 'delete') {
                            await _deleteParty(party);
                          }
                        },
                        itemBuilder: (context) => const [
                          PopupMenuItem(value: 'edit', child: Text('Edit')),
                          PopupMenuItem(value: 'delete', child: Text('Delete')),
                        ],
                        child: const Icon(Icons.more_vert),
                      )
                    : const Icon(Icons.chevron_right),
              ),
            ),
          ),
      ],
    );
  }

  String _formatSignedAmount(double value, {String positiveSign = '+'}) {
    if (value > 0) return '$positiveSign${formatBdt(value)}';
    if (value < 0) return '-${formatBdt(value.abs())}';
    return formatBdt(0);
  }
}
