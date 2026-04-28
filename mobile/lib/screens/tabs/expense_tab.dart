import 'package:flutter/material.dart';
import '../../services/api_client.dart';

class ExpenseTab extends StatefulWidget {
  final ApiClient api;
  const ExpenseTab({super.key, required this.api});

  @override
  State<ExpenseTab> createState() => _ExpenseTabState();
}

class _ExpenseTabState extends State<ExpenseTab> {
  final dealId = TextEditingController();
  final amount = TextEditingController();
  final category = TextEditingController(text: 'staff');
  String type = 'DAILY_OVERHEAD';

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(16),
      child: ListView(children: [
        DropdownButton<String>(
          value: type,
          items: const [
            DropdownMenuItem(value: 'DAILY_OVERHEAD', child: Text('DAILY_OVERHEAD')),
            DropdownMenuItem(value: 'TRANSACTION', child: Text('TRANSACTION')),
          ],
          onChanged: (v) => setState(() => type = v!),
        ),
        TextField(controller: dealId, decoration: const InputDecoration(labelText: 'Deal ID (optional)')),
        TextField(controller: amount, decoration: const InputDecoration(labelText: 'Cost Amount BDT')),
        TextField(controller: category, decoration: const InputDecoration(labelText: 'Category')),
        const SizedBox(height: 16),
        ElevatedButton(
          onPressed: () async {
            await widget.api.post('/expenses', {
              'expenseType': type,
              'tradeDealId': dealId.text.isEmpty ? null : int.parse(dealId.text),
              'amountBdt': amount.text,
              'expenseTime': DateTime.now().toIso8601String(),
              'category': category.text,
              'notes': ''
            });
            if (!context.mounted) return;
            ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Cost saved')));
          },
          child: const Text('Save Cost'),
        )
      ]),
    );
  }
}
