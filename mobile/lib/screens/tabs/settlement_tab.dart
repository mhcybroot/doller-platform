import 'package:flutter/material.dart';
import '../../services/api_client.dart';

class SettlementTab extends StatefulWidget {
  final ApiClient api;
  const SettlementTab({super.key, required this.api});

  @override
  State<SettlementTab> createState() => _SettlementTabState();
}

class _SettlementTabState extends State<SettlementTab> {
  final partyId = TextEditingController();
  final dealId = TextEditingController();
  final amount = TextEditingController();
  final paymentReference = TextEditingController();
  String paymentMethod = 'CASH';
  bool allowAdvance = false;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(16),
      child: ListView(children: [
        TextField(controller: partyId, decoration: const InputDecoration(labelText: 'Party ID')),
        TextField(controller: dealId, decoration: const InputDecoration(labelText: 'Deal ID (optional)')),
        TextField(controller: amount, decoration: const InputDecoration(labelText: 'Settlement BDT Amount')),
        DropdownButtonFormField<String>(
          initialValue: paymentMethod,
          items: const [
            DropdownMenuItem(value: 'CASH', child: Text('CASH')),
            DropdownMenuItem(value: 'BANK', child: Text('BANK')),
            DropdownMenuItem(value: 'CHECK', child: Text('CHEQUE')),
          ],
          onChanged: (value) => setState(() => paymentMethod = value ?? 'CASH'),
          decoration: const InputDecoration(labelText: 'Payment Method'),
        ),
        TextField(
          controller: paymentReference,
          decoration: const InputDecoration(labelText: 'Payment Reference (optional)'),
        ),
        SwitchListTile(
          value: allowAdvance,
          onChanged: (v) => setState(() => allowAdvance = v),
          title: const Text('Allow Overpayment As Advance'),
        ),
        const SizedBox(height: 16),
        ElevatedButton(
          onPressed: () async {
            await widget.api.post('/settlements', {
              'partyId': int.parse(partyId.text),
              'tradeDealId': dealId.text.isEmpty ? null : int.parse(dealId.text),
              'bdtAmount': amount.text,
              'settlementTime': DateTime.now().toIso8601String(),
              'paymentMethod': paymentMethod,
              'paymentReference': paymentReference.text.trim().isEmpty ? null : paymentReference.text.trim(),
              'notes': '',
              'allowAdvance': allowAdvance,
            });
            if (!context.mounted) return;
            ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Settlement saved')));
          },
          child: const Text('Save Settlement'),
        )
      ]),
    );
  }
}
