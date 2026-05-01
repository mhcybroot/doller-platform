import 'package:flutter/material.dart';

import '../../shared/instruments/instrument_labels.dart';
import '../../shared/models/auth_models.dart';
import '../../shared/models/domain_models.dart';
import '../../shared/services/api_client.dart';
import '../../shared/services/app_logger.dart';
import '../../shared/services/doller_repository.dart';
import '../../shared/widgets/finance_widgets.dart';

class TradingScreen extends StatefulWidget {
  const TradingScreen(
      {super.key, required this.repository, required this.session});

  final DollerRepository repository;
  final AuthSession session;

  @override
  State<TradingScreen> createState() => _TradingScreenState();
}

class _TradingScreenState extends State<TradingScreen> {
  final _quantityController = TextEditingController();
  final _rateController = TextEditingController();
  final _amountController = TextEditingController();
  final _categoryController = TextEditingController();
  final _paymentReferenceController = TextEditingController();
  final _noteController = TextEditingController();
  String _dealType = 'BUY';
  String _instrumentCode = 'USD';
  String _expenseType = 'OFFICE_MANAGEMENT';
  String _paymentMethod = 'CASH';
  bool _allowAdvance = false;
  int _mode = 0;
  int? _selectedPartyId;
  int? _selectedDealId;
  SettlementInferenceModel? _inference;
  String? _inferenceError;
  List<PartyModel> _parties = const [];
  List<DealSummary> _deals = const [];
  bool _loading = true;
  bool _networkError = false;
  int _inferenceVersion = 0;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    AppLogger.log('screen:trading', 'load:start');
    setState(() => _loading = true);
    try {
      final parties = await widget.repository.listParties();
      final deals = await widget.repository.listDeals();
      if (!mounted) {
        return;
      }
      AppLogger.log('screen:trading', 'load:success', fields: {
        'partyCount': parties.length,
        'dealCount': deals.length,
        'selectedPartyId': _selectedPartyId,
        'selectedDealId': _selectedDealId,
      });
      setState(() {
        _parties = parties;
        _deals = deals;
        _networkError = false;
        if (parties.isEmpty) {
          _selectedPartyId = null;
          _selectedDealId = null;
        } else {
          final hasSelectedParty =
              parties.any((party) => party.id == _selectedPartyId);
          _selectedPartyId =
              hasSelectedParty ? _selectedPartyId : parties.first.id;
          final allowedDealIds = deals
              .where((deal) => deal.partyName == _selectedParty?.name)
              .map((deal) => deal.id)
              .toSet();
          if (_selectedDealId != null &&
              !allowedDealIds.contains(_selectedDealId)) {
            _selectedDealId = null;
          }
        }
        _loading = false;
      });
      await _refreshInference();
    } on ApiException catch (error) {
      if (!mounted) {
        return;
      }
      AppLogger.log('screen:trading', 'load:error', fields: {
        'message': error.message,
        'isNetworkError': error.isNetworkError,
      });
      showAppMessage(context, error.message, isError: true);
      setState(() {
        _parties = const [];
        _deals = const [];
        _inference = null;
        _inferenceError = null;
        _networkError = error.isNetworkError;
        _loading = false;
      });
    }
  }

  Future<void> _refreshInference() async {
    if (_mode != 1 || _selectedParty == null) {
      if (!mounted) {
        return;
      }
      setState(() {
        _inference = null;
        _inferenceError = null;
      });
      return;
    }

    final requestId = ++_inferenceVersion;
    final amount = double.tryParse(_amountController.text) ?? 0;
    AppLogger.log('screen:trading', 'inference:start', fields: {
      'requestId': requestId,
      'partyId': _selectedParty?.id,
      'tradeDealId': _selectedDealId,
      'amount': amount,
    });
    try {
      final inference = await widget.repository.settlementInference(
        partyId: _selectedParty!.id,
        tradeDealId: _selectedDealId,
        amount: amount,
      );
      if (!mounted || requestId != _inferenceVersion) {
        return;
      }
      AppLogger.log('screen:trading', 'inference:success', fields: {
        'requestId': requestId,
        'direction': inference.direction,
        'basis': inference.basis,
        'appliedAmount': inference.appliedAmount,
        'advanceAmount': inference.advanceAmount,
      });
      setState(() {
        _inference = inference;
        _inferenceError = null;
      });
    } on ApiException catch (error) {
      if (!mounted || requestId != _inferenceVersion) {
        return;
      }
      AppLogger.log('screen:trading', 'inference:error', fields: {
        'requestId': requestId,
        'message': error.message,
        'isNetworkError': error.isNetworkError,
      });
      setState(() {
        _inference = null;
        _inferenceError = error.message;
      });
    }
  }

  Future<void> _submit() async {
    try {
      if (_mode == 0) {
        if (_selectedParty == null) {
          throw const ApiException('Select a party first');
        }
        AppLogger.log('screen:trading', 'submit:deal:start', fields: {
          'partyId': _selectedParty!.id,
          'dealType': _dealType,
          'instrumentCode': _instrumentCode,
          'quantity': _quantityController.text,
          'bdtRate': _rateController.text,
        });
        await widget.repository.createDeal(
          dealType: _dealType,
          partyId: _selectedParty!.id,
          instrumentCode: _instrumentCode,
          quantity: double.parse(_quantityController.text),
          bdtRate: double.parse(_rateController.text),
          notes: _noteController.text.trim(),
        );
        AppLogger.log('screen:trading', 'submit:deal:success', fields: {
          'partyId': _selectedParty!.id,
          'dealType': _dealType,
          'instrumentCode': _instrumentCode,
        });
        showAppMessage(context, 'Deal saved');
      } else if (_mode == 1) {
        if (_selectedParty == null) {
          throw const ApiException('Select a party first');
        }
        if (_inferenceError != null) {
          throw ApiException(_inferenceError!);
        }
        await widget.repository.createSettlement(
          partyId: _selectedParty!.id,
          tradeDealId: _selectedDealId,
          amount: double.parse(_amountController.text),
          paymentMethod: _paymentMethod,
          paymentReference: _paymentReferenceController.text.trim().isEmpty
              ? null
              : _paymentReferenceController.text.trim(),
          allowAdvance: _allowAdvance,
          notes: _noteController.text.trim(),
        );
        showAppMessage(context, 'Settlement saved');
      } else {
        await widget.repository.createExpense(
          expenseType: _expenseType,
          amount: double.parse(_amountController.text),
          category: _categoryController.text.trim().isEmpty
              ? _expenseType
              : _categoryController.text.trim(),
          notes: _noteController.text.trim(),
        );
        showAppMessage(context, 'Expense saved');
      }
      _noteController.clear();
      _quantityController.clear();
      _rateController.clear();
      _amountController.clear();
      _paymentReferenceController.clear();
      _allowAdvance = false;
      await _load();
    } on ApiException catch (error) {
      AppLogger.log('screen:trading', 'submit:error', fields: {
        'mode': _mode,
        'message': error.message,
        'isNetworkError': error.isNetworkError,
      });
      showAppMessage(context, error.message, isError: true);
    } on FormatException {
      showAppMessage(context, 'Please enter valid numeric values',
          isError: true);
    }
  }

  @override
  Widget build(BuildContext context) {
    final needsPartySelection = _mode != 2;
    if (_loading) {
      return const Center(child: CircularProgressIndicator());
    }
    if (needsPartySelection && _parties.isEmpty) {
      return EmptyStateCard(
        title: _networkError
            ? 'No internet connection'
            : 'Trading needs parties first',
        message: _networkError
            ? 'Please check your network and try again.'
            : 'Create at least one party so deal and settlement forms can use proper selectors.',
      );
    }

    final selectedParty = _selectedParty;
    final selectableDeals = _selectedParty == null
        ? _deals
        : _deals
            .where((deal) => deal.partyName == _selectedParty!.name)
            .toList();
    final selectedDeal = _selectedDeal;
    final amountLabel = _mode == 1
        ? (_inference?.amountLabel ?? 'Settlement Amount (BDT)')
        : 'Expense Amount (BDT)';

    return ListView(
      padding: const EdgeInsets.all(20),
      children: [
        Text('Trading Desk', style: Theme.of(context).textTheme.headlineMedium),
        const SizedBox(height: 8),
        Text(
          'Guided operational capture for buys, settlements, and costs.',
          style: Theme.of(context).textTheme.bodySmall,
        ),
        const SizedBox(height: 18),
        FinanceSection(
          title: 'Action Type',
          child: SegmentedButton<int>(
            segments: const [
              ButtonSegment(value: 0, label: Text('Deal')),
              ButtonSegment(value: 1, label: Text('Settlement')),
              ButtonSegment(value: 2, label: Text('Expense')),
            ],
            selected: {_mode},
            onSelectionChanged: (value) {
              setState(() => _mode = value.first);
              _refreshInference();
            },
          ),
        ),
        const SizedBox(height: 16),
        FinanceSection(
          title: _mode == 0
              ? 'Deal Capture'
              : (_mode == 1 ? 'Settlement Capture' : 'Expense Capture'),
          child: Column(
            children: [
              if (needsPartySelection)
                DropdownButtonFormField<int>(
                  initialValue: selectedParty?.id,
                  items: _parties
                      .map((party) => DropdownMenuItem(
                          value: party.id, child: Text(party.name)))
                      .toList(),
                  onChanged: (value) {
                    setState(() {
                      _selectedPartyId = value;
                      _selectedDealId = null;
                    });
                    _refreshInference();
                  },
                  decoration: const InputDecoration(labelText: 'Party'),
                ),
              if (_mode == 0) ...[
                const SizedBox(height: 12),
                DropdownButtonFormField<String>(
                  initialValue: _dealType,
                  items: const [
                    DropdownMenuItem(value: 'BUY', child: Text('BUY')),
                    DropdownMenuItem(value: 'SELL', child: Text('SELL')),
                  ],
                  onChanged: (value) => setState(() => _dealType = value!),
                  decoration: const InputDecoration(labelText: 'Deal Type'),
                ),
                const SizedBox(height: 12),
                DropdownButtonFormField<String>(
                  initialValue: _instrumentCode,
                  items: supportedInstrumentCodes
                      .map((instrument) => DropdownMenuItem(
                            value: instrument,
                            child: Text(instrumentDisplayName(instrument)),
                          ))
                      .toList(),
                  onChanged: (value) =>
                      setState(() => _instrumentCode = value ?? 'USD'),
                  decoration: const InputDecoration(labelText: 'Instrument'),
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: _quantityController,
                  keyboardType: TextInputType.number,
                  decoration: const InputDecoration(labelText: 'Amount'),
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: _rateController,
                  keyboardType: TextInputType.number,
                  decoration: const InputDecoration(labelText: 'BDT Rate'),
                ),
              ] else ...[
                if (_mode == 1) ...[
                  const SizedBox(height: 12),
                  DropdownButtonFormField<int?>(
                    initialValue: selectedDeal?.id,
                    items: [
                      const DropdownMenuItem<int?>(
                          value: null, child: Text('No specific deal')),
                      ...selectableDeals.map(
                        (deal) => DropdownMenuItem(
                          value: deal.id,
                          child: Text(
                              '#${deal.id} ${deal.dealType} ${instrumentDisplayName(deal.instrumentCode)} ${deal.quantity}'),
                        ),
                      ),
                    ],
                    onChanged: (value) {
                      setState(() => _selectedDealId = value);
                      _refreshInference();
                    },
                    decoration:
                        const InputDecoration(labelText: 'Related Deal'),
                  ),
                ],
                const SizedBox(height: 12),
                TextField(
                  controller: _amountController,
                  keyboardType: TextInputType.number,
                  onChanged: (_) {
                    if (_mode == 1) {
                      _refreshInference();
                    }
                  },
                  decoration: InputDecoration(
                    labelText: amountLabel,
                  ),
                ),
                if (_mode == 1) ...[
                  const SizedBox(height: 12),
                  DropdownButtonFormField<String>(
                    initialValue: _paymentMethod,
                    items: const [
                      DropdownMenuItem(value: 'CASH', child: Text('CASH')),
                      DropdownMenuItem(value: 'BANK', child: Text('BANK')),
                      DropdownMenuItem(value: 'CHECK', child: Text('CHEQUE')),
                    ],
                    onChanged: (value) =>
                        setState(() => _paymentMethod = value ?? 'CASH'),
                    decoration:
                        const InputDecoration(labelText: 'Payment Method'),
                  ),
                  const SizedBox(height: 12),
                  TextField(
                    controller: _paymentReferenceController,
                    decoration:
                        const InputDecoration(labelText: 'Payment Reference'),
                  ),
                  const SizedBox(height: 12),
                  if (_inference != null)
                    _SettlementPreviewCard(inference: _inference!),
                  if (_inferenceError != null)
                    Padding(
                      padding: const EdgeInsets.only(top: 8),
                      child: Text(
                        _inferenceError!,
                        style: TextStyle(
                            color: Theme.of(context).colorScheme.error),
                      ),
                    ),
                  const SizedBox(height: 6),
                  SwitchListTile(
                    contentPadding: EdgeInsets.zero,
                    value: _allowAdvance,
                    onChanged: (value) => setState(() => _allowAdvance = value),
                    title: Text(
                      _inference != null && _inference!.advanceAmount > 0
                          ? 'Allow ${formatBdt(_inference!.advanceAmount)} to become advance'
                          : 'Allow overpayment as advance',
                    ),
                  ),
                ],
                if (_mode == 2) ...[
                  const SizedBox(height: 12),
                  DropdownButtonFormField<String>(
                    initialValue: _expenseType,
                    items: const [
                      DropdownMenuItem(
                          value: 'OFFICE_MANAGEMENT',
                          child: Text('OFFICE_MANAGEMENT')),
                      DropdownMenuItem(
                          value: 'TRANSPORT', child: Text('TRANSPORT')),
                      DropdownMenuItem(
                          value: 'EMPLOYEE_SALARY',
                          child: Text('EMPLOYEE_SALARY')),
                      DropdownMenuItem(
                          value: 'UTILITY', child: Text('UTILITY')),
                      DropdownMenuItem(value: 'RENT', child: Text('RENT')),
                      DropdownMenuItem(value: 'OTHER', child: Text('OTHER')),
                    ],
                    onChanged: (value) => setState(() => _expenseType = value!),
                    decoration:
                        const InputDecoration(labelText: 'Expense Type'),
                  ),
                  const SizedBox(height: 12),
                  TextField(
                    controller: _categoryController,
                    decoration: const InputDecoration(
                        labelText: 'Category Detail (optional)'),
                  ),
                ],
              ],
              const SizedBox(height: 12),
              TextField(
                controller: _noteController,
                decoration: const InputDecoration(labelText: 'Notes'),
                maxLines: 2,
              ),
              const SizedBox(height: 18),
              ElevatedButton(
                onPressed: _submit,
                child: Text(_mode == 0
                    ? 'Save Deal'
                    : (_mode == 1 ? 'Save Settlement' : 'Save Expense')),
              ),
            ],
          ),
        ),
      ],
    );
  }

  PartyModel? get _selectedParty {
    for (final party in _parties) {
      if (party.id == _selectedPartyId) {
        return party;
      }
    }
    return null;
  }

  DealSummary? get _selectedDeal {
    for (final deal in _deals) {
      if (deal.id == _selectedDealId) {
        return deal;
      }
    }
    return null;
  }
}

class _SettlementPreviewCard extends StatelessWidget {
  const _SettlementPreviewCard({required this.inference});

  final SettlementInferenceModel inference;

  @override
  Widget build(BuildContext context) {
    return FinanceSection(
      title: 'Settlement Preview',
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(inference.summary,
              style: Theme.of(context).textTheme.bodyMedium),
          const SizedBox(height: 14),
          Wrap(
            spacing: 10,
            runSpacing: 10,
            children: [
              BalancePill(
                label: 'Receivable',
                value: formatBdt(inference.current.receivableBdt),
                tone: BalancePillTone.receivable,
              ),
              BalancePill(
                label: 'Payable',
                value: formatBdt(inference.current.payableBdt),
                tone: BalancePillTone.payable,
              ),
            ],
          ),
          const SizedBox(height: 14),
          Text('After save', style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 10),
          Wrap(
            spacing: 10,
            runSpacing: 10,
            children: [
              BalancePill(
                label: 'Receivable',
                value: formatBdt(inference.projected.receivableBdt),
                tone: BalancePillTone.receivable,
              ),
              BalancePill(
                label: 'Payable',
                value: formatBdt(inference.projected.payableBdt),
                tone: BalancePillTone.payable,
              ),
            ],
          ),
        ],
      ),
    );
  }
}
