import 'package:flutter/material.dart';
import '../services/api_client.dart';
import 'tabs/dashboard_tab.dart';
import 'tabs/deals_tab.dart';
import 'tabs/expense_tab.dart';
import 'tabs/settlement_tab.dart';
import 'tabs/statements_tab.dart';

class HomeScreen extends StatefulWidget {
  final ApiClient api;
  const HomeScreen({super.key, required this.api});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  int index = 0;

  @override
  Widget build(BuildContext context) {
    final tabs = [
      DashboardTab(api: widget.api),
      DealsTab(api: widget.api),
      SettlementTab(api: widget.api),
      ExpenseTab(api: widget.api),
      StatementsTab(api: widget.api),
    ];

    return Scaffold(
      appBar: AppBar(title: const Text('Doller Ledger')),
      body: tabs[index],
      bottomNavigationBar: NavigationBar(
        selectedIndex: index,
        onDestinationSelected: (i) => setState(() => index = i),
        destinations: const [
          NavigationDestination(icon: Icon(Icons.dashboard), label: 'Dashboard'),
          NavigationDestination(icon: Icon(Icons.swap_horiz), label: 'Deals'),
          NavigationDestination(icon: Icon(Icons.payments), label: 'Settle'),
          NavigationDestination(icon: Icon(Icons.receipt), label: 'Costs'),
          NavigationDestination(icon: Icon(Icons.assessment), label: 'Statements'),
        ],
      ),
    );
  }
}
