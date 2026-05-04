import 'package:flutter/material.dart';

import '../../shared/instruments/instrument_labels.dart';
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
      if (!mounted) {
        return;
      }
      DateTime from = DateTime(2020, 1, 1);
      DateTime to = DateTime.now();
      final String sortField = 'occurredAt';
      final String sortDirection = 'desc';
      PartyLedgerModel? ledger;
      bool loading = true;
      bool networkError = false;
      await showModalBottomSheet<void>(
        context: context,
        isScrollControlled: true,
        builder: (context) {
          bool loaded = false;
          return DraggableScrollableSheet(
            expand: false,
            initialChildSize: 0.92,
            builder: (context, controller) => StatefulBuilder(
              builder: (context, setModalState) {
                Future<void> loadLedger() async {
                  setModalState(() => loading = true);
                  try {
                    final result = await widget.repository.partyLedger(
                      party.id,
                      from: from,
                      to: to,
                      sortField: sortField,
                      sortDirection: sortDirection,
                    );
                    AppLogger.log('screen:party-ledger', 'open:success', fields: {
                      'partyId': party.id,
                      'partyName': party.name,
                      'receivableBdt': result.balances.receivableBdt,
                      'payableBdt': result.balances.payableBdt,
                      'netBalanceBdt': result.balances.netBalanceBdt,
                      'lineCount': result.lines.length,
                    });
                    setModalState(() {
                      ledger = result;
                      networkError = false;
                      loading = false;
                    });
                  } on ApiException catch (error) {
                    setModalState(() {
                      ledger = null;
                      networkError = error.isNetworkError;
                      loading = false;
                    });
                    if (context.mounted) {
                      showAppMessage(context, error.message, isError: true);
                    }
                  }
                }

                if (!loaded) {
                  loaded = true;
                  Future.microtask(loadLedger);
                }

                if (loading) {
                  return const Center(child: CircularProgressIndicator());
                }

                if (ledger == null) {
                  return Padding(
                    padding: const EdgeInsets.all(20),
                    child: EmptyStateCard(
                      title: networkError
                          ? 'No internet connection'
                          : 'No party details',
                      message: networkError
                          ? 'Please check your network and try again.'
                          : 'Could not load party transactions right now.',
                      action: ElevatedButton(
                        onPressed: loadLedger,
                        child: const Text('Retry'),
                      ),
                    ),
                  );
                }
                final flow = _partyFlowSummary(ledger!.lines);

                return Padding(
                  padding: const EdgeInsets.all(20),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(ledger!.partyName,
                          style: Theme.of(context).textTheme.titleLarge),
                      const SizedBox(height: 10),
                      Wrap(
                        spacing: 10,
                        runSpacing: 10,
                        children: [
                          BalancePill(
                            label: 'Receivable',
                            value: _formatSignedAmount(
                              ledger!.balances.receivableBdt,
                              positiveSign: '+',
                            ),
                            tone: BalancePillTone.receivable,
                          ),
                          BalancePill(
                            label: 'Payable',
                            value: _formatSignedAmount(
                              ledger!.balances.payableBdt,
                              positiveSign: '-',
                            ),
                            tone: BalancePillTone.payable,
                          ),
                          BalancePill(
                            label: 'NET POSITION ✅',
                            value: _formatSignedAmount(
                                ledger!.balances.netBalanceBdt),
                            tone: ledger!.balances.netBalanceBdt >= 0
                                ? BalancePillTone.netPositive
                                : BalancePillTone.netNegative,
                          ),
                        ],
                      ),
                      const SizedBox(height: 10),
                      Wrap(
                        spacing: 10,
                        runSpacing: 10,
                        children: [
                          BalancePill(
                            label: 'Total Buy',
                            value: formatBdt(flow.totalBuy),
                            tone: BalancePillTone.neutral,
                          ),
                          BalancePill(
                            label: 'Total Sell',
                            value: formatBdt(flow.totalSell),
                            tone: BalancePillTone.neutral,
                          ),
                          BalancePill(
                            label: 'Settlement In',
                            value: formatBdt(flow.settlementIn),
                            tone: BalancePillTone.neutral,
                          ),
                          BalancePill(
                            label: 'Settlement Out',
                            value: formatBdt(flow.settlementOut),
                            tone: BalancePillTone.neutral,
                          ),
                        ],
                      ),
                      const SizedBox(height: 12),
                      Expanded(
                        child: ledger!.lines.isEmpty
                            ? const EmptyStateCard(
                                title: 'No transactions found',
                                message:
                                    'Try another date range, type, search term, or sort.',
                              )
                            : ListView(
                                controller: controller,
                                children: ledger!.lines
                                    .map((row) => Card(
                                          margin:
                                              const EdgeInsets.only(bottom: 10),
                                          child: Padding(
                                            padding: const EdgeInsets.all(16),
                                            child: Column(
                                              crossAxisAlignment:
                                                  CrossAxisAlignment.start,
                                              children: [
                                                Row(
                                                  children: [
                                                    Expanded(
                                                      child: Text(
                                                        row.referenceLabel ??
                                                            '${row.entryType} #${row.entryId}',
                                                        style: Theme.of(context)
                                                            .textTheme
                                                            .titleMedium,
                                                      ),
                                                    ),
                                                    Text(
                                                      _formatSignedLedgerRowAmount(
                                                          row),
                                                      style: Theme.of(context)
                                                          .textTheme
                                                          .titleMedium
                                                          ?.copyWith(
                                                            color:
                                                                _ledgerRowAmountColor(
                                                                    row),
                                                          ),
                                                    ),
                                                    if (_canMutateLedgerRow(row))
                                                      PopupMenuButton<String>(
                                                        onSelected:
                                                            (value) async {
                                                          if (value == 'edit') {
                                                            await _editLedgerRow(
                                                                row,
                                                                party,
                                                                loadLedger);
                                                            return;
                                                          }
                                                          if (value ==
                                                              'delete') {
                                                            await _deleteLedgerRow(
                                                                row, loadLedger);
                                                          }
                                                        },
                                                        itemBuilder: (context) =>
                                                            const [
                                                          PopupMenuItem(
                                                              value: 'edit',
                                                              child:
                                                                  Text('Edit')),
                                                          PopupMenuItem(
                                                              value: 'delete',
                                                              child:
                                                                  Text('Delete')),
                                                        ],
                                                      ),
                                                  ],
                                                ),
                                                const SizedBox(height: 6),
                                                Text(
                                                    '${_entryTypeLabel(row.entryType)} • ${formatDateTime(row.occurredAt)}'),
                                                if ((row.partyName ?? '')
                                                    .isNotEmpty)
                                                  Text('Party: ${row.partyName}'),
                                                if ((row.directionLabel ?? '')
                                                    .isNotEmpty)
                                                  Text(
                                                      'Direction: ${row.directionLabel}'),
                                                if ((row.paymentMethod ?? '')
                                                    .isNotEmpty)
                                                  Text(
                                                      'Payment: ${row.paymentMethod == 'CHECK' ? 'CHEQUE' : row.paymentMethod}'),
                                                if ((row.paymentReference ?? '')
                                                    .isNotEmpty)
                                                  Text(
                                                      'Reference: ${row.paymentReference}'),
                                                if ((row.instrumentCode ?? '')
                                                    .isNotEmpty)
                                                  Text(
                                                      'Instrument: ${instrumentDisplayName(row.instrumentCode!)}'),
                                                if (row.quantity != null)
                                                  Text('Amount: ${row.quantity}'),
                                                if (row.bdtRate != null)
                                                  Text('Rate: ${row.bdtRate}'),
                                                if ((row.category ?? '')
                                                    .isNotEmpty)
                                                  Text(
                                                      'Category: ${row.category}'),
                                                if ((row.notes ?? '')
                                                    .isNotEmpty) ...[
                                                  const SizedBox(height: 4),
                                                  Text(row.notes!),
                                                ],
                                              ],
                                            ),
                                          ),
                                        ))
                                    .toList(),
                              ),
                      ),
                    ],
                  ),
                );
              },
            ),
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

  bool _canMutateLedgerRow(PartyLedgerLineModel row) {
    return widget.session.isOwner &&
        (row.entryType == 'DEAL' ||
            row.entryType == 'SETTLEMENT' ||
            row.entryType == 'EXPENSE');
  }

  Future<void> _deleteLedgerRow(
    PartyLedgerLineModel row,
    Future<void> Function() reload,
  ) async {
    if (row.entryType == 'OPENING_BALANCE') {
      showAppMessage(context, 'Opening balance entries cannot be deleted.',
          isError: true);
      return;
    }
    final confirm = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Delete Transaction'),
        content: Text('Delete ${row.referenceLabel ?? row.entryType}?'),
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
      if (row.entryType == 'DEAL') {
        await widget.repository.deleteDeal(row.entryId);
      } else if (row.entryType == 'SETTLEMENT') {
        await widget.repository.deleteSettlement(row.entryId);
      } else if (row.entryType == 'EXPENSE') {
        await widget.repository.deleteExpense(row.entryId);
      }
      if (!mounted) return;
      showAppMessage(context, 'Transaction deleted');
      await reload();
      await _load();
    } on ApiException catch (error) {
      if (!mounted) return;
      showAppMessage(context, error.message, isError: true);
    }
  }

  Future<void> _editLedgerRow(
    PartyLedgerLineModel row,
    PartyModel party,
    Future<void> Function() reload,
  ) async {
    if (row.entryType == 'OPENING_BALANCE') {
      showAppMessage(context, 'Opening balance entries are not editable.',
          isError: true);
      return;
    }
    if (row.entryType == 'DEAL') {
      await _editLedgerDeal(row, reload);
      return;
    }
    if (row.entryType == 'SETTLEMENT') {
      await _editLedgerSettlement(row, reload);
      return;
    }
    if (row.entryType == 'EXPENSE') {
      await _editLedgerExpense(row, reload);
      return;
    }
    showAppMessage(context, 'Unsupported entry type for edit.', isError: true);
  }

  Future<void> _editLedgerDeal(
    PartyLedgerLineModel row,
    Future<void> Function() reload,
  ) async {
    if (row.partyId == null ||
        row.instrumentCode == null ||
        row.quantity == null ||
        row.bdtRate == null ||
        row.directionLabel == null) {
      showAppMessage(context, 'This deal row is missing required data.',
          isError: true);
      return;
    }
    final qty = TextEditingController(text: row.quantity!.toString());
    final rate = TextEditingController(text: row.bdtRate!.toString());
    final notes = TextEditingController(text: row.notes ?? '');
    int selectedPartyId = row.partyId!;
    String selectedInstrument = row.instrumentCode!;
    String dealType =
        row.directionLabel!.toUpperCase().contains('SELL') ? 'SELL' : 'BUY';
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      builder: (context) => StatefulBuilder(
        builder: (context, setModalState) => Padding(
          padding: EdgeInsets.fromLTRB(
            20,
            24,
            20,
            MediaQuery.of(context).viewInsets.bottom + 24,
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text('Edit Deal', style: Theme.of(context).textTheme.titleLarge),
              const SizedBox(height: 12),
              DropdownButtonFormField<String>(
                value: dealType,
                items: const [
                  DropdownMenuItem(value: 'BUY', child: Text('BUY')),
                  DropdownMenuItem(value: 'SELL', child: Text('SELL')),
                ],
                onChanged: (value) =>
                    setModalState(() => dealType = value ?? 'BUY'),
                decoration: const InputDecoration(labelText: 'Deal Type'),
              ),
              const SizedBox(height: 12),
              DropdownButtonFormField<int>(
                value: selectedPartyId,
                isExpanded: true,
                items: _parties
                    .map((party) => DropdownMenuItem<int>(
                          value: party.id,
                          child: Text(party.name),
                        ))
                    .toList(),
                onChanged: (value) => setModalState(
                    () => selectedPartyId = value ?? selectedPartyId),
                decoration: const InputDecoration(labelText: 'Party'),
              ),
              const SizedBox(height: 12),
              DropdownButtonFormField<String>(
                value: selectedInstrument,
                items: supportedInstrumentCodes
                    .map((code) => DropdownMenuItem<String>(
                          value: code,
                          child: Text(instrumentDisplayName(code)),
                        ))
                    .toList(),
                onChanged: (value) => setModalState(
                    () => selectedInstrument = value ?? selectedInstrument),
                decoration: const InputDecoration(labelText: 'Instrument'),
              ),
              const SizedBox(height: 12),
              TextField(
                  controller: qty,
                  keyboardType: TextInputType.number,
                  decoration: const InputDecoration(labelText: 'Amount')),
              const SizedBox(height: 12),
              TextField(
                  controller: rate,
                  keyboardType: TextInputType.number,
                  decoration: const InputDecoration(labelText: 'Rate')),
              const SizedBox(height: 12),
              TextField(
                  controller: notes,
                  decoration: const InputDecoration(labelText: 'Notes')),
              const SizedBox(height: 16),
              ElevatedButton(
                onPressed: () async {
                  try {
                    await widget.repository.updateDeal(
                      id: row.entryId,
                      dealType: dealType,
                      partyId: selectedPartyId,
                      instrumentCode: selectedInstrument,
                      quantity: double.parse(qty.text),
                      bdtRate: double.parse(rate.text),
                      dealTime: row.occurredAt,
                      notes: notes.text.trim(),
                    );
                    if (!context.mounted) return;
                    Navigator.pop(context);
                  } on ApiException catch (error) {
                    showAppMessage(context, error.message, isError: true);
                  } on FormatException {
                    showAppMessage(context, 'Enter valid numbers',
                        isError: true);
                  }
                },
                child: const Text('Update Deal'),
              ),
            ],
          ),
        ),
      ),
    );
    await reload();
    await _load();
  }

  Future<void> _editLedgerSettlement(
    PartyLedgerLineModel row,
    Future<void> Function() reload,
  ) async {
    if (row.partyId == null) {
      showAppMessage(context, 'This settlement row is missing party data.',
          isError: true);
      return;
    }
    final amount = TextEditingController(text: row.amountBdt.toString());
    final paymentRef = TextEditingController(text: row.paymentReference ?? '');
    final notes = TextEditingController(text: row.notes ?? '');
    bool allowAdvance = row.directionLabel?.contains(' / NONE') ?? false;
    int selectedPartyId = row.partyId!;
    int? selectedDealId = row.tradeDealId;
    List<DealSummary> partyDeals = const [];
    try {
      partyDeals = await widget.repository.listDeals(partyId: selectedPartyId);
    } on ApiException {}
    String paymentMethod = (row.paymentMethod ?? 'CASH').toUpperCase();
    if (paymentMethod != 'BANK' &&
        paymentMethod != 'CHECK' &&
        paymentMethod != 'CASH') {
      paymentMethod = 'CASH';
    }
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      builder: (context) => StatefulBuilder(
        builder: (context, setModalState) => Padding(
          padding: EdgeInsets.fromLTRB(
            20,
            24,
            20,
            MediaQuery.of(context).viewInsets.bottom + 24,
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text('Edit Settlement',
                  style: Theme.of(context).textTheme.titleLarge),
              const SizedBox(height: 12),
              TextField(
                  controller: amount,
                  keyboardType: TextInputType.number,
                  decoration: const InputDecoration(labelText: 'Amount (BDT)')),
              const SizedBox(height: 12),
              DropdownButtonFormField<int>(
                value: selectedPartyId,
                isExpanded: true,
                items: _parties
                    .map((party) => DropdownMenuItem<int>(
                          value: party.id,
                          child: Text(party.name),
                        ))
                    .toList(),
                onChanged: (value) async {
                  if (value == null) return;
                  final deals = await widget.repository.listDeals(partyId: value);
                  setModalState(() {
                    selectedPartyId = value;
                    partyDeals = deals;
                    selectedDealId = null;
                  });
                },
                decoration: const InputDecoration(labelText: 'Party'),
              ),
              const SizedBox(height: 12),
              DropdownButtonFormField<int?>(
                value: selectedDealId,
                isExpanded: true,
                items: [
                  const DropdownMenuItem<int?>(
                      value: null, child: Text('No Related Deal')),
                  ...partyDeals.map(
                    (deal) => DropdownMenuItem<int?>(
                      value: deal.id,
                      child: Text('Deal #${deal.id} • ${deal.dealType}'),
                    ),
                  ),
                ],
                onChanged: (value) => setModalState(() => selectedDealId = value),
                decoration: const InputDecoration(labelText: 'Related Deal'),
              ),
              const SizedBox(height: 12),
              DropdownButtonFormField<String>(
                value: paymentMethod,
                items: const [
                  DropdownMenuItem(value: 'CASH', child: Text('CASH')),
                  DropdownMenuItem(value: 'BANK', child: Text('BANK')),
                  DropdownMenuItem(value: 'CHECK', child: Text('CHEQUE')),
                ],
                onChanged: (value) =>
                    setModalState(() => paymentMethod = value ?? 'CASH'),
                decoration: const InputDecoration(labelText: 'Payment Method'),
              ),
              const SizedBox(height: 12),
              TextField(
                  controller: paymentRef,
                  decoration:
                      const InputDecoration(labelText: 'Payment Reference')),
              const SizedBox(height: 12),
              TextField(
                  controller: notes,
                  decoration: const InputDecoration(labelText: 'Notes')),
              const SizedBox(height: 12),
              SwitchListTile(
                value: allowAdvance,
                onChanged: (value) => setModalState(() => allowAdvance = value),
                title: const Text('Allow Advance'),
                contentPadding: EdgeInsets.zero,
              ),
              const SizedBox(height: 16),
              ElevatedButton(
                onPressed: () async {
                  try {
                    await widget.repository.updateSettlement(
                      id: row.entryId,
                      partyId: selectedPartyId,
                      tradeDealId: selectedDealId,
                      amount: double.parse(amount.text),
                      paymentMethod: paymentMethod,
                      paymentReference: paymentRef.text.trim().isEmpty
                          ? null
                          : paymentRef.text.trim(),
                      allowAdvance: allowAdvance,
                      notes: notes.text.trim(),
                      settlementTime: row.occurredAt,
                    );
                    if (!context.mounted) return;
                    Navigator.pop(context);
                  } on ApiException catch (error) {
                    showAppMessage(context, error.message, isError: true);
                  } on FormatException {
                    showAppMessage(context, 'Enter valid numbers',
                        isError: true);
                  }
                },
                child: const Text('Update Settlement'),
              ),
            ],
          ),
        ),
      ),
    );
    await reload();
    await _load();
  }

  Future<void> _editLedgerExpense(
    PartyLedgerLineModel row,
    Future<void> Function() reload,
  ) async {
    final amount = TextEditingController(text: row.amountBdt.toString());
    final category = TextEditingController(text: row.category ?? 'OTHER');
    final notes = TextEditingController(text: row.notes ?? '');
    String expenseType = row.expenseType ?? 'OTHER';
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      builder: (context) => StatefulBuilder(
        builder: (context, setModalState) => Padding(
          padding: EdgeInsets.fromLTRB(
            20,
            24,
            20,
            MediaQuery.of(context).viewInsets.bottom + 24,
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text('Edit Expense',
                  style: Theme.of(context).textTheme.titleLarge),
              const SizedBox(height: 12),
              TextField(
                  controller: amount,
                  keyboardType: TextInputType.number,
                  decoration: const InputDecoration(labelText: 'Amount (BDT)')),
              const SizedBox(height: 12),
              DropdownButtonFormField<String>(
                value: expenseType,
                items: const [
                  DropdownMenuItem(
                      value: 'OFFICE_MANAGEMENT',
                      child: Text('OFFICE MANAGEMENT')),
                  DropdownMenuItem(value: 'TRANSPORT', child: Text('TRANSPORT')),
                  DropdownMenuItem(value: 'UTILITY', child: Text('UTILITY')),
                  DropdownMenuItem(value: 'RENT', child: Text('RENT')),
                  DropdownMenuItem(
                      value: 'EMPLOYEE_SALARY', child: Text('EMPLOYEE SALARY')),
                  DropdownMenuItem(value: 'OTHER', child: Text('OTHER')),
                ],
                onChanged: (value) =>
                    setModalState(() => expenseType = value ?? expenseType),
                decoration: const InputDecoration(labelText: 'Expense Type'),
              ),
              const SizedBox(height: 12),
              TextField(
                  controller: category,
                  decoration: const InputDecoration(labelText: 'Category')),
              const SizedBox(height: 12),
              TextField(
                  controller: notes,
                  decoration: const InputDecoration(labelText: 'Notes')),
              const SizedBox(height: 16),
              ElevatedButton(
                onPressed: () async {
                  try {
                    await widget.repository.updateExpense(
                      id: row.entryId,
                      expenseType: expenseType,
                      amount: double.parse(amount.text),
                      category: category.text.trim(),
                      notes: notes.text.trim(),
                      expenseTime: row.occurredAt,
                    );
                    if (!context.mounted) return;
                    Navigator.pop(context);
                  } on ApiException catch (error) {
                    showAppMessage(context, error.message, isError: true);
                  } on FormatException {
                    showAppMessage(context, 'Enter valid numbers',
                        isError: true);
                  }
                },
                child: const Text('Update Expense'),
              ),
            ],
          ),
        ),
      ),
    );
    await reload();
    await _load();
  }

  String _entryTypeLabel(String entryType) {
    switch (entryType) {
      case 'OPENING_BALANCE':
        return 'Opening Balance';
      case 'SETTLEMENT':
        return 'Settlement';
      case 'EXPENSE':
        return 'Expense';
      case 'DEAL':
        return 'Deal';
      default:
        return entryType;
    }
  }

  String _formatSignedLedgerRowAmount(PartyLedgerLineModel row) {
    final amount = row.amountBdt;
    if (amount == 0) {
      return formatBdt(0);
    }
    final sign = _isNegativeLedgerRow(row) ? '-' : '+';
    return '$sign${formatBdt(amount.abs())}';
  }

  Color _ledgerRowAmountColor(PartyLedgerLineModel row) {
    final isNegative = _isNegativeLedgerRow(row);
    return isNegative ? Colors.red.shade700 : Colors.green.shade700;
  }

  bool _isNegativeLedgerRow(PartyLedgerLineModel row) {
    final direction = (row.directionLabel ?? '').toUpperCase();
    final isOpeningPayable =
        row.entryType == 'OPENING_BALANCE' && direction.contains('PAYABLE');
    final isOutgoingSettlement =
        row.entryType == 'SETTLEMENT' && direction.startsWith('OUTGOING');
    final isSellDeal = row.entryType == 'DEAL' && direction.contains('SELL');
    return row.entryType == 'EXPENSE' ||
        isOutgoingSettlement ||
        isOpeningPayable ||
        isSellDeal;
  }

  ({double totalBuy, double totalSell, double settlementIn, double settlementOut})
      _partyFlowSummary(List<PartyLedgerLineModel> rows) {
    double totalBuy = 0;
    double totalSell = 0;
    double settlementIn = 0;
    double settlementOut = 0;

    for (final row in rows) {
      final direction = (row.directionLabel ?? '').toUpperCase();
      final amount = row.amountBdt;
      if (row.entryType == 'DEAL') {
        if (direction.contains('BUY')) {
          totalBuy += amount;
        } else if (direction.contains('SELL')) {
          totalSell += amount;
        }
      } else if (row.entryType == 'SETTLEMENT') {
        if (direction.startsWith('INCOMING')) {
          settlementIn += amount;
        } else if (direction.startsWith('OUTGOING')) {
          settlementOut += amount;
        }
      }
    }

    return (
      totalBuy: totalBuy,
      totalSell: totalSell,
      settlementIn: settlementIn,
      settlementOut: settlementOut
    );
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
