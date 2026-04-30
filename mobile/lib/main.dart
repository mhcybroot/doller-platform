import 'package:flutter/material.dart';
import 'package:flutter_dotenv/flutter_dotenv.dart';

import 'app/app_theme.dart';
import 'features/auth/login_screen.dart';
import 'screens/home_screen.dart';
import 'shared/models/auth_models.dart';
import 'shared/services/api_client.dart';
import 'shared/services/auth_store.dart';
import 'shared/services/doller_repository.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await dotenv.load(fileName: '.env');
  runApp(const DollerApp());
}

class DollerApp extends StatelessWidget {
  const DollerApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'NexPay',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.build(),
      home: const AppBootstrap(),
    );
  }
}

class AppBootstrap extends StatefulWidget {
  const AppBootstrap({super.key});

  @override
  State<AppBootstrap> createState() => _AppBootstrapState();
}

class _AppBootstrapState extends State<AppBootstrap> {
  late final DollerRepository _repository;
  Future<AuthSession?>? _sessionFuture;

  @override
  void initState() {
    super.initState();
    _repository = DollerRepository(ApiClient(AuthStore()), AuthStore());
    _sessionFuture = _repository.currentSession();
  }

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<AuthSession?>(
      future: _sessionFuture,
      builder: (context, snapshot) {
        if (snapshot.connectionState != ConnectionState.done) {
          return const Scaffold(
            body: Center(child: CircularProgressIndicator()),
          );
        }
        if (!snapshot.hasData) {
          return const LoginScreen();
        }
        return HomeScreen(repository: _repository, session: snapshot.data!);
      },
    );
  }
}
