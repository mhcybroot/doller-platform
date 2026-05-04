import 'package:flutter/material.dart';

import '../../shared/models/domain_models.dart';
import '../../shared/services/api_client.dart';
import '../../shared/services/doller_repository.dart';
import '../../shared/widgets/finance_widgets.dart';

class CustomEntryFormPage extends StatefulWidget {
  const CustomEntryFormPage({
    super.key,
    required this.repository,
    required this.companyId,
    this.existing,
  });

  final DollerRepository repository;
  final int companyId;
  final CustomEntryRowModel? existing;

  @override
  State<CustomEntryFormPage> createState() => _CustomEntryFormPageState();
}

class _CustomEntryFormPageState extends State<CustomEntryFormPage> {
  late String _entryType;
  late DateTime _entryTime;
  late TextEditingController _item;
  late TextEditingController _amount;
  late TextEditingController _notes;
  bool _saving = false;

  @override
  void initState() {
    super.initState();
    _entryType = widget.existing?.entryType ?? 'PROFIT';
    _entryTime = widget.existing?.entryTime ?? DateTime.now();
    _item = TextEditingController(text: widget.existing?.itemPurpose ?? '');
    _amount = TextEditingController(
      text: widget.existing == null ? '' : widget.existing!.amountBdt.toString(),
    );
    _notes = TextEditingController(text: widget.existing?.notes ?? '');
  }

  Future<void> _save() async {
    setState(() => _saving = true);
    try {
      final parsed = double.parse(_amount.text.trim());
      if (widget.existing == null) {
        await widget.repository.createCustomEntry(
          companyId: widget.companyId,
          entryType: _entryType,
          amount: parsed,
          entryTime: _entryTime,
          itemPurpose: _item.text.trim(),
          notes: _notes.text.trim().isEmpty ? null : _notes.text.trim(),
        );
      } else {
        await widget.repository.updateCustomEntry(
          id: widget.existing!.id,
          companyId: widget.companyId,
          entryType: _entryType,
          amount: parsed,
          entryTime: _entryTime,
          itemPurpose: _item.text.trim(),
          notes: _notes.text.trim().isEmpty ? null : _notes.text.trim(),
        );
      }
      if (!mounted) return;
      Navigator.pop(context);
    } on FormatException {
      showAppMessage(context, 'Enter valid amount', isError: true);
      setState(() => _saving = false);
    } on ApiException catch (e) {
      showAppMessage(context, e.message, isError: true);
      setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text(widget.existing == null ? 'Add Entry' : 'Edit Entry')),
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          FinanceSection(
            title: 'Entry Form',
            child: Column(
              children: [
                DropdownButtonFormField<String>(
                  initialValue: _entryType,
                  items: const [
                    DropdownMenuItem(value: 'PROFIT', child: Text('Profit')),
                    DropdownMenuItem(value: 'COST', child: Text('Cost')),
                  ],
                  onChanged: (v) => setState(() => _entryType = v ?? 'PROFIT'),
                  decoration: const InputDecoration(labelText: 'Type'),
                ),
                const SizedBox(height: 12),
                OutlinedButton(
                  onPressed: () async {
                    final picked = await showDatePicker(
                      context: context,
                      firstDate: DateTime(2020),
                      lastDate: DateTime(2100),
                      initialDate: _entryTime,
                    );
                    if (picked == null) return;
                    setState(() {
                      _entryTime = DateTime(
                        picked.year,
                        picked.month,
                        picked.day,
                        _entryTime.hour,
                        _entryTime.minute,
                      );
                    });
                  },
                  child: Text('Date ${formatDate(_entryTime)}'),
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: _item,
                  decoration: const InputDecoration(labelText: 'Item/Purpose'),
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: _amount,
                  keyboardType: TextInputType.number,
                  decoration: const InputDecoration(labelText: 'Amount'),
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: _notes,
                  decoration: const InputDecoration(labelText: 'Note'),
                ),
                const SizedBox(height: 16),
                ElevatedButton(
                  onPressed: _saving ? null : _save,
                  child: Text(widget.existing == null ? 'Save Entry' : 'Update Entry'),
                ),
              ],
            ),
          )
        ],
      ),
    );
  }
}

