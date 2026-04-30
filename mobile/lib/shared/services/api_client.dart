import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';

import '../models/auth_models.dart';
import '../config/app_env.dart';
import 'auth_store.dart';
import 'outbox_store.dart';

class ApiException implements Exception {
  final String message;
  final int? statusCode;

  const ApiException(this.message, {this.statusCode});

  @override
  String toString() => message;
}

class ApiClient {
  ApiClient(this._store)
      : _dio = Dio(
          BaseOptions(
            baseUrl: AppEnv.baseUrl,
            connectTimeout: const Duration(seconds: 20),
            receiveTimeout: const Duration(seconds: 20),
          ),
        ) {
    _dio.interceptors.add(
      InterceptorsWrapper(
        onRequest: (options, handler) async {
          if (options.extra['skipAuth'] == true) {
            handler.next(options);
            return;
          }
          final session = await _store.readSession();
          if (session != null && session.accessToken.isNotEmpty) {
            options.headers['Authorization'] = 'Bearer ${session.accessToken}';
          }
          handler.next(options);
        },
      ),
    );
  }

  final Dio _dio;
  final AuthStore _store;
  final OutboxStore _outbox = OutboxStore();
  Future<AuthSession?>? _refreshing;

  Future<T> get<T>(
    String path, {
    Map<String, dynamic>? query,
    required T Function(dynamic json) parser,
  }) async {
    try {
      final response =
          await _request(() => _dio.get(path, queryParameters: query));
      return parser(response.data);
    } on DioException catch (error) {
      throw _mapError(error);
    }
  }

  Future<T> post<T>(
    String path, {
    Map<String, dynamic>? data,
    required T Function(dynamic json) parser,
    bool queueOnNetworkFailure = true,
  }) async {
    try {
      final response = await _request(() => _dio.post(path, data: data));
      return parser(response.data);
    } on DioException catch (error) {
      if (_shouldQueue(error) && queueOnNetworkFailure && data != null) {
        await _outbox.enqueue('POST', path, data);
      }
      throw _mapError(error);
    }
  }

  Future<T> put<T>(
    String path, {
    Map<String, dynamic>? data,
    required T Function(dynamic json) parser,
  }) async {
    try {
      final response = await _request(() => _dio.put(path, data: data));
      return parser(response.data);
    } on DioException catch (error) {
      throw _mapError(error);
    }
  }

  Future<T> delete<T>(
    String path, {
    Map<String, dynamic>? data,
    required T Function(dynamic json) parser,
  }) async {
    try {
      final response = await _request(() => _dio.delete(path, data: data));
      return parser(response.data);
    } on DioException catch (error) {
      throw _mapError(error);
    }
  }

  Future<Uint8List> download(
    String path, {
    required Map<String, dynamic> query,
  }) async {
    try {
      final response = await _request(
        () => _dio.get(
          path,
          queryParameters: query,
          options: Options(responseType: ResponseType.bytes),
        ),
      );
      return Uint8List.fromList(response.data as List<int>);
    } on DioException catch (error) {
      throw _mapError(error);
    }
  }

  Future<void> flushRetries() async {
    final items = await _outbox.dueItems();
    for (final item in items) {
      try {
        await _request(
            () => _dio.post(item.path, data: jsonDecode(item.bodyJson)));
        await _outbox.markDone(item.id!);
      } catch (error) {
        await _outbox.markFailed(
            item.id!, item.attemptCount + 1, error.toString());
      }
    }
  }

  Future<Map<String, int>> queueStats() => _outbox.stats();

  Future<void> retryPoison() => _outbox.retryPoison();

  Future<void> logout() => _store.clear();

  Future<Response<dynamic>> _request(
      Future<Response<dynamic>> Function() sender) async {
    try {
      return await sender();
    } on DioException catch (error) {
      if (error.response?.statusCode == 401) {
        final refreshed = await _refreshSession();
        if (refreshed != null) {
          return sender();
        }
      }
      throw error;
    }
  }

  Future<AuthSession?> _refreshSession() async {
    _refreshing ??= _performRefresh();
    final session = await _refreshing!;
    _refreshing = null;
    return session;
  }

  Future<AuthSession?> _performRefresh() async {
    final existing = await _store.readSession();
    if (existing == null || existing.refreshToken.isEmpty) {
      await _store.clear();
      return null;
    }
    try {
      final response = await _dio.post(
        '/auth/refresh',
        data: {'refreshToken': existing.refreshToken},
        options: Options(extra: {'skipAuth': true}),
      );
      final session =
          AuthSession.fromJson(response.data as Map<String, dynamic>);
      await _store.saveSession(session);
      return session;
    } on DioException {
      await _store.clear();
      return null;
    }
  }

  bool _shouldQueue(DioException error) {
    return error.type == DioExceptionType.connectionError ||
        error.type == DioExceptionType.connectionTimeout ||
        error.type == DioExceptionType.receiveTimeout;
  }

  ApiException _mapError(DioException error) {
    final data = error.response?.data;
    if (data is Map<String, dynamic>) {
      final message = data['message'] as String? ?? 'Request failed';
      return ApiException(message, statusCode: error.response?.statusCode);
    }
    return ApiException(
      error.message ?? 'Network request failed',
      statusCode: error.response?.statusCode,
    );
  }
}
