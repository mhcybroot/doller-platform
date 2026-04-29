import 'package:flutter/material.dart';
import '../../services/api_client.dart';

class ExpenseTab extends StatefulWidget {
  final ApiClient api;
  const ExpenseTab({super.key, required this.api});

  @override
  State<ExpenseTab> createState() => _ExpenseTabState();
}

class _ExpenseTabState extends State<ExpenseTab> {
  final amount = TextEditingController();
  final custom = TextEditingController();
  String type = 'OFFICE_MANAGEMENT';

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(16),
      child: ListView(children: [
        DropdownButton<String>(
          value: type,
          items: const [
            DropdownMenuItem(value: 'OFFICE_MANAGEMENT', child: Text('OFFICE_MANAGEMENT')),
            DropdownMenuItem(value: 'TRANSPORT', child: Text('TRANSPORT')),
            DropdownMenuItem(value: 'EMPLOYEE_SALARY', child: Text('EMPLOYEE_SALARY')),
            DropdownMenuItem(value: 'UTILITY', child: Text('UTILITY')),
            DropdownMenuItem(value: 'RENT', child: Text('RENT')),
            DropdownMenuItem(value: 'OTHER', child: Text('OTHER')),
          ],
          onChanged: (v) => setState(() => type = v!),
        ),
        TextField(controller: amount, decoration: const InputDecoration(labelText: 'Cost Amount BDT')),
        TextField(controller: custom, decoration: const InputDecoration(labelText: 'Category Detail (optional)')),
        const SizedBox(height: 16),
        ElevatedButton(
          onPressed: () async {
            await widget.api.post('/expenses', {
              'expenseType': type,
              'amountBdt': amount.text,
              'expenseTime': DateTime.now().toIso8601String(),
              'category': custom.text.trim().isEmpty ? type : custom.text.trim(),
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
