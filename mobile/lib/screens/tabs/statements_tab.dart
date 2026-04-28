import 'package:flutter/material.dart';
import '../../services/api_client.dart';

class StatementsTab extends StatefulWidget {
  final ApiClient api;
  const StatementsTab({super.key, required this.api});

  @override
  State<StatementsTab> createState() => _StatementsTabState();
}

class _StatementsTabState extends State<StatementsTab> {
  List<dynamic> lines = [];

  Future<void> load() async {
    final now = DateTime.now();
    final date = '${now.year}-${now.month.toString().padLeft(2, '0')}-${now.day.toString().padLeft(2, '0')}';
    final res = await widget.api.get('/statements/daily', query: {'date': date});
    setState(() => lines = List<dynamic>.from(res.data));
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(16),
      child: Column(children: [
        Row(children: [
          ElevatedButton(onPressed: load, child: const Text('Load Today Statement')),
          const SizedBox(width: 8),
          ElevatedButton(
            onPressed: () async {
              final now = DateTime.now();
              final date = '${now.year}-${now.month.toString().padLeft(2, '0')}-${now.day.toString().padLeft(2, '0')}';
              await widget.api.post('/day-close/$date', {});
              if (!context.mounted) return;
              ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Day closed')));
            },
            child: const Text('Close Day'),
          ),
          const SizedBox(width: 8),
          ElevatedButton(
            onPressed: () async {
              final now = DateTime.now();
              final date = '${now.year}-${now.month.toString().padLeft(2, '0')}-${now.day.toString().padLeft(2, '0')}';
              await widget.api.post('/day-close/$date/reopen', {'reason': 'Correction required'});
              if (!context.mounted) return;
              ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Day reopened')));
            },
            child: const Text('Reopen Day'),
          )
        ]),
        const SizedBox(height: 12),
        Expanded(
          child: ListView.builder(
            itemCount: lines.length,
            itemBuilder: (_, i) => ListTile(
              title: Text('Date: ${lines[i]['date']}'),
              subtitle: Text('P/L: ${lines[i]['pnl']} | Cash: ${lines[i]['closingCash']}'),
            ),
          ),
        )
      ]),
    );
  }
}
