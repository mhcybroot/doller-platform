import 'dart:convert';

import 'package:path/path.dart';
import 'package:sqflite/sqflite.dart';

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

  Map<String, dynamic> toMap() {
    return {
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
}

class OutboxStore {
  static Database? _db;

  Future<Database> db() async {
    if (_db != null) {
      return _db!;
    }
    final path = join(await getDatabasesPath(), 'doller_outbox.db');
    _db = await openDatabase(
      path,
      version: 1,
      onCreate: (database, version) async {
        await database.execute(
          '''
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
          ''',
        );
      },
    );
    return _db!;
  }

  Future<void> enqueue(String method, String path, Map<String, dynamic> body) async {
    final database = await db();
    await database.insert(
      'outbox',
      OutboxItem(
        method: method,
        path: path,
        bodyJson: jsonEncode(body),
        attemptCount: 0,
        status: 'pending',
        lastError: null,
        nextAttemptAtEpoch: 0,
      ).toMap(),
    );
  }

  Future<List<OutboxItem>> dueItems() async {
    final database = await db();
    final now = DateTime.now().millisecondsSinceEpoch;
    final rows = await database.query(
      'outbox',
      where: "status IN ('pending','failed') AND next_attempt_at_epoch <= ?",
      whereArgs: [now],
      orderBy: 'id asc',
    );
    return rows
        .map(
          (row) => OutboxItem(
            id: row['id'] as int,
            method: row['method'] as String,
            path: row['path'] as String,
            bodyJson: row['body_json'] as String,
            attemptCount: row['attempt_count'] as int,
            status: row['status'] as String,
            lastError: row['last_error'] as String?,
            nextAttemptAtEpoch: row['next_attempt_at_epoch'] as int,
          ),
        )
        .toList();
  }

  Future<void> markDone(int id) async {
    final database = await db();
    await database.delete('outbox', where: 'id = ?', whereArgs: [id]);
  }

  Future<void> markFailed(int id, int attempts, String error) async {
    final database = await db();
    final backoffSeconds = attempts >= 5 ? 3600 : (1 << attempts).clamp(2, 300);
    await database.update(
      'outbox',
      {
        'attempt_count': attempts,
        'status': attempts >= 10 ? 'poison' : 'failed',
        'last_error': error,
        'next_attempt_at_epoch': DateTime.now()
            .add(Duration(seconds: backoffSeconds))
            .millisecondsSinceEpoch,
      },
      where: 'id = ?',
      whereArgs: [id],
    );
  }

  Future<Map<String, int>> stats() async {
    final database = await db();
    final rows = await database.rawQuery(
      "SELECT status, COUNT(*) as c FROM outbox GROUP BY status",
    );
    final out = {'pending': 0, 'failed': 0, 'poison': 0};
    for (final row in rows) {
      out[row['status'] as String] = row['c'] as int;
    }
    return out;
  }

  Future<void> retryPoison() async {
    final database = await db();
    await database.rawUpdate(
      "UPDATE outbox SET status = 'pending', next_attempt_at_epoch = 0 WHERE status = 'poison'",
    );
  }
}
