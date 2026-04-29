import 'package:flutter/material.dart';

import '../features/control/control_center_screen.dart';
import '../features/dashboard/dashboard_screen.dart';
import '../features/parties/parties_screen.dart';
import '../features/statements/statements_screen.dart';
import '../features/trading/trading_screen.dart';
import '../features/auth/login_screen.dart';
import '../shared/models/auth_models.dart';
import '../shared/services/doller_repository.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({
    super.key,
    required this.repository,
    required this.session,
  });

  final DollerRepository repository;
  final AuthSession session;

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  int _index = 0;

  @override
  Widget build(BuildContext context) {
    final screens = [
      DashboardScreen(repository: widget.repository),
      TradingScreen(repository: widget.repository),
      PartiesScreen(repository: widget.repository),
      StatementsScreen(repository: widget.repository, session: widget.session),
    ];

    return Scaffold(
      appBar: AppBar(
        title: const Text('Exchange Platform'),
        actions: [
          if (widget.session.isOwner)
            IconButton(
              icon: const Icon(Icons.admin_panel_settings_outlined),
              onPressed: () {
                Navigator.of(context).push(
                  MaterialPageRoute(
                    builder: (_) => ControlCenterScreen(
                      repository: widget.repository,
                      session: widget.session,
                    ),
                  ),
                );
              },
            ),
          IconButton(
            icon: const Icon(Icons.logout),
            onPressed: () async {
              await widget.repository.logout();
              if (!mounted) {
                return;
              }
              Navigator.of(context).pushAndRemoveUntil(
                MaterialPageRoute(builder: (_) => const LoginScreen()),
                (_) => false,
              );
            },
          ),
        ],
      ),
      body: screens[_index],
      bottomNavigationBar: NavigationBar(
        selectedIndex: _index,
        onDestinationSelected: (value) => setState(() => _index = value),
        destinations: const [
          NavigationDestination(icon: Icon(Icons.pie_chart_outline), label: 'Dashboard'),
          NavigationDestination(icon: Icon(Icons.candlestick_chart), label: 'Trading'),
          NavigationDestination(icon: Icon(Icons.people_alt_outlined), label: 'Party'),
          NavigationDestination(icon: Icon(Icons.assessment_outlined), label: 'Reports'),
        ],
      ),
    );
  }
}
