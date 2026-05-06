import 'package:flutter/material.dart';
import '../../shared/instruments/instrument_labels.dart';
import '../../shared/models/domain_models.dart';
import '../../shared/services/api_client.dart';

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
  List<CurrencyModel> currencies = const [];
  String currencyCode = 'USD';
  String type = 'BUY';

  Map<String, String> get currencyLabels => currencyLabelMap(currencies);

  @override
  void initState() {
    super.initState();
    _loadCurrencies();
  }

  Future<void> _loadCurrencies() async {
    final data = await widget.api.get<List<CurrencyModel>>(
      '/currencies',
      parser: (json) => (json as List<dynamic>)
          .map((item) => CurrencyModel.fromJson(item as Map<String, dynamic>))
          .toList(),
    );
    if (!mounted) {
      return;
    }
    setState(() {
      currencies = data;
      if (currencies.any((currency) => currency.code == currencyCode)) {
        return;
      }
      currencyCode = currencies.isEmpty ? '' : currencies.first.code;
    });
  }

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
          value: currencies.any((currency) => currency.code == currencyCode)
              ? currencyCode
              : null,
          items: currencies
              .map((currency) => DropdownMenuItem(
                    value: currency.code,
                    child: Text(currencyDisplayName(
                      currency.code,
                      labels: currencyLabels,
                    )),
                  ))
              .toList(),
          onChanged: (value) => setState(() => currencyCode = value ?? 'USD'),
          decoration: const InputDecoration(labelText: 'Currency'),
        ),
        TextField(
            controller: usd,
            decoration: const InputDecoration(labelText: 'Amount')),
        TextField(
            controller: rate,
            decoration: const InputDecoration(labelText: 'BDT Rate')),
        const SizedBox(height: 16),
        ElevatedButton(
          onPressed: () async {
            await widget.api.post<void>(
              '/deals',
              data: {
                'dealType': type,
                'partyId': int.parse(partyId.text),
                'currencyCode': currencyCode,
                'quantity': usd.text,
                'bdtRate': rate.text,
                'dealTime': DateTime.now().toIso8601String(),
                'notes': ''
              },
              parser: (_) {},
            );
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
