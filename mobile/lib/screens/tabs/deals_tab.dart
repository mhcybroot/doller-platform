import 'package:flutter/material.dart';
import '../../shared/instruments/instrument_labels.dart';
import '../../services/api_client.dart';

class DealsTab extends StatefulWidget {
  final ApiClient api;
  const DealsTab({super.key, required this.api});

  @override
  State<DealsTab> createState() => _DealsTabState();
}

class _DealsTabState extends State<DealsTab> {
  final partyId = TextEditingController();
  final usd = TextEditingController();
  final rate = TextEditingController();
  String instrument = 'USD';
  String type = 'BUY';

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(16),
      child: ListView(children: [
        DropdownButton<String>(
          value: type,
          items: const [
            DropdownMenuItem(value: 'BUY', child: Text('BUY')),
            DropdownMenuItem(value: 'SELL', child: Text('SELL'))
          ],
          onChanged: (v) => setState(() => type = v!),
        ),
        TextField(
            controller: partyId,
            decoration: const InputDecoration(labelText: 'Party ID')),
        DropdownButtonFormField<String>(
          value: instrument,
          items: supportedInstrumentCodes
              .map((code) => DropdownMenuItem(
                    value: code,
                    child: Text(instrumentDisplayName(code)),
                  ))
              .toList(),
          onChanged: (value) => setState(() => instrument = value ?? 'USD'),
          decoration: const InputDecoration(labelText: 'Instrument'),
        ),
        TextField(
            controller: usd,
            decoration: const InputDecoration(labelText: 'Quantity')),
        TextField(
            controller: rate,
            decoration: const InputDecoration(labelText: 'BDT Rate')),
        const SizedBox(height: 16),
        ElevatedButton(
          onPressed: () async {
            await widget.api.post('/deals', {
              'dealType': type,
              'partyId': int.parse(partyId.text),
              'instrumentCode': instrument,
              'quantity': usd.text,
              'bdtRate': rate.text,
              'dealTime': DateTime.now().toIso8601String(),
              'notes': ''
            });
            if (!context.mounted) return;
            ScaffoldMessenger.of(context)
                .showSnackBar(const SnackBar(content: Text('Deal saved')));
          },
          child: const Text('Save Deal'),
        )
      ]),
    );
  }
}
