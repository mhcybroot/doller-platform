import 'package:flutter/material.dart';
import '../../services/api_client.dart';

class DashboardTab extends StatefulWidget {
  final ApiClient api;
  const DashboardTab({super.key, required this.api});

  @override
  State<DashboardTab> createState() => _DashboardTabState();
}

class _DashboardTabState extends State<DashboardTab> {
  Map<String, dynamic>? data;
  Map<String, int> stats = {'pending': 0, 'failed': 0, 'poison': 0};

  @override
  void initState() {
    super.initState();
    load();
  }

  Future<void> load() async {
    final now = DateTime.now();
    final from = '${now.year}-${now.month.toString().padLeft(2, '0')}-01';
    final to = '${now.year}-${now.month.toString().padLeft(2, '0')}-${now.day.toString().padLeft(2, '0')}';
    final res = await widget.api.get('/dashboard', query: {'from': from, 'to': to});
    final s = await widget.api.queueStats();
    setState(() {
      data = Map<String, dynamic>.from(res.data);
      stats = s;
    });
  }

  @override
  Widget build(BuildContext context) {
    if (data == null) return const Center(child: CircularProgressIndicator());
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Text('Today P/L: ${data!['todayPnL']} BDT'),
        Text('Period P/L: ${data!['periodPnL']} BDT'),
        Text('Receivable: ${data!['receivableBdt']} BDT'),
        Text('Payable: ${data!['payableBdt']} BDT'),
        Text('USD Position: ${data!['usdPosition']} USD'),
        const Divider(height: 24),
        Text('Sync Pending: ${stats['pending']}'),
        Text('Sync Failed: ${stats['failed']}'),
        Text('Sync Poison: ${stats['poison']}'),
        const SizedBox(height: 12),
        Row(
          children: [
            ElevatedButton(
              onPressed: () async {
                await widget.api.flushRetries();
                await load();
              },
              child: const Text('Retry Queue'),
            ),
            const SizedBox(width: 8),
            ElevatedButton(
              onPressed: () async {
                await widget.api.retryPoison();
                await load();
              },
              child: const Text('Requeue Poison'),
            ),
          ],
        )
      ],
    );
  }
}
