import 'package:flutter/material.dart';
import '../services/api_client.dart';
import '../services/auth_store.dart';
import 'home_screen.dart';

class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key});

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final user = TextEditingController(text: 'owner');
  final pass = TextEditingController(text: 'owner123');
  bool loading = false;

  @override
  Widget build(BuildContext context) {
    final api = ApiClient(AuthStore());
    return Scaffold(
      appBar: AppBar(title: const Text('Doller Ledger Login')),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          children: [
            TextField(controller: user, decoration: const InputDecoration(labelText: 'Username')),
            TextField(controller: pass, decoration: const InputDecoration(labelText: 'Password'), obscureText: true),
            const SizedBox(height: 16),
            ElevatedButton(
              onPressed: loading ? null : () async {
                setState(() => loading = true);
                try {
                  final res = await api.post('/auth/login', {'username': user.text, 'password': pass.text});
                  await AuthStore().saveToken(res.data['accessToken'] as String);
                  if (!context.mounted) return;
                  Navigator.pushReplacement(context, MaterialPageRoute(builder: (_) => HomeScreen(api: api)));
                } finally {
                  if (mounted) setState(() => loading = false);
                }
              },
              child: Text(loading ? 'Logging in...' : 'Login'),
            )
          ],
        ),
      ),
    );
  }
}
