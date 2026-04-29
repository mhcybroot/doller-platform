import 'dart:convert';

import 'package:dio/dio.dart';

import 'auth_store.dart';
import 'outbox_store.dart';

class ApiClient {
  final Dio _dio = Dio(BaseOptions(baseUrl: 'http://76.13.221.43:8088'));
  final AuthStore _store;
  final OutboxStore _outbox = OutboxStore();

  ApiClient(this._store) {
    _dio.interceptors.add(InterceptorsWrapper(onRequest: (options, handler) async {
      final token = await _store.getToken();
      if (token != null) {
        options.headers['Authorization'] = 'Bearer $token';
      }
      handler.next(options);
    }));
  }

  Future<Response<dynamic>> post(String path, dynamic data) async {
    try {
      return await _dio.post(path, data: data);
    } catch (e) {
      await _outbox.enqueue('POST', path, Map<String, dynamic>.from(data as Map));
      rethrow;
    }
  }

  Future<Response<dynamic>> get(String path, {Map<String, dynamic>? query}) async {
    return _dio.get(path, queryParameters: query);
  }

  Future<void> flushRetries() async {
    final items = await _outbox.dueItems();
    for (final item in items) {
      try {
        if (item.method == 'POST') {
          await _dio.post(item.path, data: jsonDecode(item.bodyJson));
        }
        await _outbox.markDone(item.id!);
      } catch (e) {
        await _outbox.markFailed(item.id!, item.attemptCount + 1, e.toString());
      }
    }
  }

  Future<Map<String, int>> queueStats() => _outbox.stats();
  Future<void> retryPoison() => _outbox.retryPoison();
}
