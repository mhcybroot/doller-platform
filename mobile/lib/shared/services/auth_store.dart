import 'dart:convert';

import 'package:flutter_secure_storage/flutter_secure_storage.dart';

import '../models/auth_models.dart';

class AuthStore {
  static const _sessionKey = 'auth_session';
  static const _androidOptions = AndroidOptions(
    encryptedSharedPreferences: true,
    resetOnError: true,
  );
  static const _iosOptions = IOSOptions(
    accessibility: KeychainAccessibility.first_unlock_this_device,
  );

  AuthStore({FlutterSecureStorage? secureStorage})
      : _secureStorage = secureStorage ??
            const FlutterSecureStorage(
              aOptions: _androidOptions,
              iOptions: _iosOptions,
            );

  final FlutterSecureStorage _secureStorage;

  Future<void> saveSession(
    AuthSession session, {
    bool rememberMe = true,
  }) async {
    if (!rememberMe) {
      await clear();
      return;
    }
    final expiresAt = DateTime.now().add(const Duration(hours: 24));
    final payload = {
      'session': session.toJson(),
      'rememberMe': true,
      'expiresAt': expiresAt.toIso8601String(),
    };
    await _secureStorage.write(key: _sessionKey, value: jsonEncode(payload));
  }

  Future<AuthSession?> readSession() async {
    final raw = await _secureStorage.read(key: _sessionKey);
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
    await _secureStorage.delete(key: _sessionKey);
  }
}
