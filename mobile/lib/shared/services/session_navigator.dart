import 'package:flutter/material.dart';

import '../../features/auth/login_screen.dart';

class SessionNavigator {
  static final navigatorKey = GlobalKey<NavigatorState>();

  static void forceLogoutToLogin() {
    final navigator = navigatorKey.currentState;
    if (navigator == null) {
      return;
    }
    navigator.pushAndRemoveUntil(
      MaterialPageRoute(builder: (_) => const LoginScreen()),
      (_) => false,
    );
  }
}
