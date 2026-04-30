import 'dart:convert';

import 'package:shared_preferences/shared_preferences.dart';

import '../models/auth_models.dart';

class AuthStore {
  static const _sessionKey = 'auth_session';

  Future<void> saveSession(
    AuthSession session, {
    bool rememberMe = true,
  }) async {
    final prefs = await SharedPreferences.getInstance();
    if (!rememberMe) {
      await prefs.remove(_sessionKey);
      return;
    }
    final expiresAt = DateTime.now().add(const Duration(hours: 24));
    final payload = {
      'session': session.toJson(),
      'rememberMe': true,
      'expiresAt': expiresAt.toIso8601String(),
    };
    await prefs.setString(_sessionKey, jsonEncode(payload));
  }

  Future<AuthSession?> readSession() async {
    final prefs = await SharedPreferences.getInstance();
    final raw = prefs.getString(_sessionKey);
    if (raw == null || raw.isEmpty) {
      return null;
    }
    final decoded = jsonDecode(raw);
    if (decoded is! Map<String, dynamic>) {
      await clear();
      return null;
    }
    final sessionJson = decoded['session'];
    final expiresAtRaw = decoded['expiresAt'] as String?;
    final rememberMe = decoded['rememberMe'] == true;
    if (sessionJson is! Map<String, dynamic> ||
        expiresAtRaw == null ||
        !rememberMe) {
      await clear();
      return null;
    }
    final expiresAt = DateTime.tryParse(expiresAtRaw);
    if (expiresAt == null || DateTime.now().isAfter(expiresAt)) {
      await clear();
      return null;
    }
    return AuthSession.fromJson(sessionJson);
  }

  Future<void> clear() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove(_sessionKey);
  }
}
