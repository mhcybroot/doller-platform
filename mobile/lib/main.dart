import 'package:flutter/material.dart';
import 'screens/login_screen.dart';

void main() {
  runApp(const DollerApp());
}

class DollerApp extends StatelessWidget {
  const DollerApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Doller Ledger',
      theme: ThemeData(colorSchemeSeed: Colors.teal, useMaterial3: true),
      home: const LoginScreen(),
    );
  }
}
