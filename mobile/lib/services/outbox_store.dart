import 'dart:convert';
import 'dart:io';

import 'package:path/path.dart';
import 'package:sqflite/sqflite.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';

class OutboxItem {
  final int? id;
  final String method;
  final String path;
  final String bodyJson;
  final int attemptCount;
  final String status;
  final String? lastError;
  final int nextAttemptAtEpoch;

  OutboxItem({
    this.id,
    required this.method,
    required this.path,
    required this.bodyJson,
    required this.attemptCount,
    required this.status,
    required this.lastError,
    required this.nextAttemptAtEpoch,
  });

  Map<String, dynamic> toMap() => {
        'id': id,
        'method': method,
        'path': path,
        'body_json': bodyJson,
        'attempt_count': attemptCount,
        'status': status,
        'last_error': lastError,
        'next_attempt_at_epoch': nextAttemptAtEpoch,
      };
}

class OutboxStore {
  static Database? _db;

  Future<Database> db() async {
    if (_db != null) return _db!;

    if (Platform.isWindows || Platform.isLinux) {
      sqfliteFfiInit();
      databaseFactory = databaseFactoryFfi;
    }

    final path = join(await getDatabasesPath(), 'doller_outbox.db');
    _db = await openDatabase(
      path,
      version: 1,
      onCreate: (db, version) async {
        await db.execute('''
          CREATE TABLE outbox (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            method TEXT NOT NULL,
            path TEXT NOT NULL,
            body_json TEXT NOT NULL,
            attempt_count INTEGER NOT NULL DEFAULT 0,
            status TEXT NOT NULL DEFAULT 'pending',
            last_error TEXT,
            next_attempt_at_epoch INTEGER NOT NULL DEFAULT 0
          )
        ''');
      },
    );
    return _db!;
  }

  Future<void> enqueue(String method, String path, Map<String, dynamic> body) async {
    final d = await db();
    await d.insert('outbox', OutboxItem(
      method: method,
      path: path,
      bodyJson: jsonEncode(body),
      attemptCount: 0,
      status: 'pending',
      lastError: null,
      nextAttemptAtEpoch: 0,
    ).toMap());
  }

  Future<List<OutboxItem>> dueItems() async {
    final d = await db();
    final now = DateTime.now().millisecondsSinceEpoch;
    final rows = await d.query('outbox', where: "status IN ('pending','failed') AND next_attempt_at_epoch <= ?", whereArgs: [now], orderBy: 'id asc');
    return rows.map((r) => OutboxItem(
      id: r['id'] as int,
      method: r['method'] as String,
      path: r['path'] as String,
      bodyJson: r['body_json'] as String,
      attemptCount: r['attempt_count'] as int,
      status: r['status'] as String,
      lastError: r['last_error'] as String?,
      nextAttemptAtEpoch: r['next_attempt_at_epoch'] as int,
    )).toList();
  }

  Future<void> markDone(int id) async {
    final d = await db();
    await d.delete('outbox', where: 'id = ?', whereArgs: [id]);
  }

  Future<void> markFailed(int id, int attempts, String error) async {
    final d = await db();
    final backoffSeconds = attempts >= 5 ? 3600 : (1 << attempts).clamp(2, 300);
    await d.update(
      'outbox',
      {
        'attempt_count': attempts,
        'status': attempts >= 10 ? 'poison' : 'failed',
        'last_error': error,
        'next_attempt_at_epoch': DateTime.now().add(Duration(seconds: backoffSeconds)).millisecondsSinceEpoch,
      },
      where: 'id = ?',
      whereArgs: [id],
    );
  }

  Future<Map<String, int>> stats() async {
    final d = await db();
    final rows = await d.rawQuery("SELECT status, COUNT(*) as c FROM outbox GROUP BY status");
    final out = {'pending': 0, 'failed': 0, 'poison': 0};
    for (final r in rows) {
      out[r['status'] as String] = (r['c'] as int);
    }
    return out;
  }

  Future<void> retryPoison() async {
    final d = await db();
    await d.rawUpdate("UPDATE outbox SET status='pending', next_attempt_at_epoch=0 WHERE status='poison'");
  }
}
