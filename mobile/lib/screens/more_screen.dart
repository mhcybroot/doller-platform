import 'package:flutter/material.dart';

import '../features/currencies/currency_management_screen.dart';
import '../features/custom/custom_profit_cost_home.dart';
import '../features/dues/dues_screen.dart';
import '../features/pnl/pnl_explain_screen.dart';
import '../features/statements/statements_screen.dart';
import '../shared/models/auth_models.dart';
import '../shared/services/doller_repository.dart';

class MoreScreen extends StatelessWidget {
  const MoreScreen({
    super.key,
    required this.repository,
    required this.session,
  });

  final DollerRepository repository;
  final AuthSession session;

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.all(20),
      children: [
        Text('More', style: Theme.of(context).textTheme.headlineMedium),
        const SizedBox(height: 12),
        Card(
          child: ListTile(
            leading: const Icon(Icons.account_balance_wallet_outlined),
            title: const Text('Dues'),
            subtitle: const Text('Receivable and payable lists with totals'),
            trailing: const Icon(Icons.chevron_right),
            onTap: () {
              Navigator.of(context).push(
                MaterialPageRoute(
                  builder: (_) => Scaffold(
                    backgroundColor: Theme.of(context).scaffoldBackgroundColor,
                    appBar: AppBar(title: const Text('Dues')),
                    body: SafeArea(
                      child: DuesScreen(repository: repository),
                    ),
                  ),
                ),
              );
            },
          ),
        ),
        Card(
          child: ListTile(
            leading: const Icon(Icons.query_stats_outlined),
            title: const Text('Profit/Loss Explanation'),
            subtitle:
                const Text('Owner-friendly gross/net breakdown by period'),
            trailing: const Icon(Icons.chevron_right),
            onTap: () {
              Navigator.of(context).push(
                MaterialPageRoute(
                  builder: (_) => Scaffold(
                    backgroundColor: Theme.of(context).scaffoldBackgroundColor,
                    appBar:
                        AppBar(title: const Text('Profit/Loss Explanation')),
                    body: SafeArea(
                      child: PnlExplainScreen(repository: repository),
                    ),
                  ),
                ),
              );
            },
          ),
        ),
        Card(
          child: ListTile(
            leading: const Icon(Icons.balance_outlined),
            title: const Text('Balance Sheet'),
            subtitle:
                const Text('Opening/closing balances from live transactions'),
            trailing: const Icon(Icons.chevron_right),
            onTap: () {
              Navigator.of(context).push(
                MaterialPageRoute(
                  builder: (_) => Scaffold(
                    backgroundColor: Theme.of(context).scaffoldBackgroundColor,
                    appBar: AppBar(title: const Text('Balance Sheet')),
                    body: SafeArea(
                      child: StatementsScreen(
                        repository: repository,
                        session: session,
                        initialTab: 0,
                      ),
                    ),
                  ),
                ),
              );
            },
          ),
        ),
        Card(
          child: ListTile(
            leading: const Icon(Icons.receipt_long_outlined),
            title: const Text('Transaction Details'),
            subtitle:
                const Text('Filterable deal, settlement, and expense rows'),
            trailing: const Icon(Icons.chevron_right),
            onTap: () {
              Navigator.of(context).push(
                MaterialPageRoute(
                  builder: (_) => Scaffold(
                    backgroundColor: Theme.of(context).scaffoldBackgroundColor,
                    appBar: AppBar(title: const Text('Transaction Details')),
                    body: SafeArea(
                      child: StatementsScreen(
                        repository: repository,
                        session: session,
                        initialTab: 1,
                      ),
                    ),
                  ),
                ),
              );
            },
          ),
        ),
        if (session.isOwner)
          Card(
            child: ListTile(
              leading: const Icon(Icons.currency_exchange_outlined),
              title: const Text('Currency Management'),
              subtitle:
                  const Text('Add, edit, and delete deal currencies'),
              trailing: const Icon(Icons.chevron_right),
              onTap: () {
                Navigator.of(context).push(
                  MaterialPageRoute(
                    builder: (_) => Scaffold(
                      backgroundColor: Theme.of(context).scaffoldBackgroundColor,
                      appBar: AppBar(title: const Text('Currency Management')),
                      body: SafeArea(
                        child: CurrencyManagementScreen(repository: repository),
                      ),
                    ),
                  ),
                );
              },
            ),
          ),
        if (session.isOwner)
          Card(
            child: ListTile(
              leading: const Icon(Icons.assessment_outlined),
              title: const Text('Custom Profit/Cost'),
              subtitle: const Text('Owner custom entries by company with summary'),
              trailing: const Icon(Icons.chevron_right),
              onTap: () {
                Navigator.of(context).push(
                  MaterialPageRoute(
                    builder: (_) => Scaffold(
                      backgroundColor: Theme.of(context).scaffoldBackgroundColor,
                      appBar: AppBar(title: const Text('Custom Profit/Cost')),
                      body: SafeArea(
                        child: CustomProfitCostHome(repository: repository),
                        
                      ),
                    ),
                  ),
                );
              },
            ),
          ),
      ],
    );
  }
}
