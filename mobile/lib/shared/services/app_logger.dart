import 'dart:convert';
import 'dart:math';

import 'package:flutter/foundation.dart';

class AppLogger {
  static const _prefix = '[NexPay]';
  static final Random _random = Random();

  static String newTraceId() {
    final now = DateTime.now().microsecondsSinceEpoch;
    final salt = _random.nextInt(1 << 32).toRadixString(16);
    return 't$now$salt';
  }

  static void log(
    String area,
    String message, {
    String? traceId,
    Map<String, Object?> fields = const {},
  }) {
    final head = StringBuffer(_prefix);
    if (traceId != null && traceId.isNotEmpty) {
      head.write('[$traceId]');
    }
    head.write('[$area] $message');

    if (kReleaseMode) {
      debugPrint(head.toString());
      return;
    }

    final payload = fields.isEmpty ? '' : ' ${jsonEncode(_sanitize(fields))}';
    debugPrint('$head$payload');
  }

  static Object? sanitizeValue(Object? value) => _sanitize(value);

  static String summarizeForPath(String path, Object? data) {
    final sanitized = _sanitize(data);
    if (sanitized is Map<String, dynamic>) {
      if (path.contains('/dues/snapshot')) {
        final rows = sanitized['rows'];
        return jsonEncode({
          'totalReceivableBdt': sanitized['totalReceivableBdt'],
          'totalPayableBdt': sanitized['totalPayableBdt'],
          'grossBdt': sanitized['grossBdt'],
          'netBdt': sanitized['netBdt'],
          'rowCount': rows is List ? rows.length : 0,
          'rowsPreview': rows is List ? rows.take(3).toList() : const [],
        });
      }
      if (path.contains('/ledgers/party/')) {
        final balances = sanitized['balances'];
        final lines = sanitized['lines'];
        return jsonEncode({
          'balances': balances,
          'lineCount': lines is List ? lines.length : 0,
          'linesPreview': lines is List ? lines.take(3).toList() : const [],
        });
      }
      if (path.contains('/dashboard')) {
        return jsonEncode({
          'receivableBdt': sanitized['receivableBdt'],
          'payableBdt': sanitized['payableBdt'],
          'totalPositionValuationBdt': sanitized['totalPositionValuationBdt'],
          'positionsCount': sanitized['positions'] is List
              ? (sanitized['positions'] as List).length
              : 0,
        });
      }
    }
    if (sanitized is List) {
      return jsonEncode(
          {'count': sanitized.length, 'preview': sanitized.take(3).toList()});
    }
    return jsonEncode(sanitized);
  }

  static Object? _sanitize(Object? value) {
    if (value is Map) {
      return value.map((key, rawValue) {
        final normalizedKey = key.toString();
        return MapEntry(normalizedKey, _sanitizeField(normalizedKey, rawValue));
      });
    }
    if (value is List) {
      return value.take(10).map(_sanitize).toList();
    }
    return value;
  }

  static Object? _sanitizeField(String key, Object? value) {
    final lower = key.toLowerCase();
    if (lower.contains('password') ||
        lower.contains('token') ||
        lower.contains('authorization')) {
      return '***';
    }
    if (lower.contains('paymentreference')) {
      final text = (value ?? '').toString();
      if (text.isEmpty) {
        return text;
      }
      return text.length <= 4
          ? '***'
          : '${text.substring(0, 2)}***${text.substring(text.length - 2)}';
    }
    if (lower.contains('notes')) {
      final text = (value ?? '').toString().trim();
      if (text.isEmpty) {
        return text;
      }
      return text.length <= 48 ? text : '${text.substring(0, 48)}...';
    }
    return _sanitize(value);
  }
}
