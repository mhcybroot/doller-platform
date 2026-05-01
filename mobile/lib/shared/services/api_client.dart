import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';

import '../models/auth_models.dart';
import '../config/app_env.dart';
import 'app_logger.dart';
import 'auth_store.dart';
import 'outbox_store.dart';
import 'session_navigator.dart';

class ApiException implements Exception {
  final String message;
  final int? statusCode;
  final bool isNetworkError;

  const ApiException(this.message,
      {this.statusCode, this.isNetworkError = false});

  @override
  String toString() => message;
}

class DownloadResult {
  final Uint8List bytes;
  final Map<String, String> headers;

  const DownloadResult({required this.bytes, required this.headers});
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
          final traceId = AppLogger.newTraceId();
          final startedAt = DateTime.now().microsecondsSinceEpoch;
          options.extra['traceId'] = traceId;
          options.extra['startedAtUs'] = startedAt;
          options.headers[TraceHeader.name] = traceId;
          if (options.extra['skipAuth'] == true) {
            AppLogger.log(
              'api',
              'request',
              traceId: traceId,
              fields: {
                'method': options.method,
                'url': options.uri.toString(),
                'query': options.queryParameters,
                'body': AppLogger.sanitizeValue(options.data),
                'skipAuth': true,
              },
            );
            handler.next(options);
            return;
          }
          final session = await _store.readSession();
          if (session != null && session.accessToken.isNotEmpty) {
            options.headers['Authorization'] = 'Bearer ${session.accessToken}';
          }
          AppLogger.log(
            'api',
            'request',
            traceId: traceId,
            fields: {
              'method': options.method,
              'url': options.uri.toString(),
              'query': options.queryParameters,
              'body': AppLogger.sanitizeValue(options.data),
            },
          );
          handler.next(options);
        },
        onResponse: (response, handler) {
          final traceId = response.requestOptions.extra['traceId'] as String?;
          final startedAtUs =
              response.requestOptions.extra['startedAtUs'] as int? ?? 0;
          final durationMs = startedAtUs == 0
              ? null
              : ((DateTime.now().microsecondsSinceEpoch - startedAtUs) / 1000)
                  .round();
          AppLogger.log(
            'api',
            'response',
            traceId: traceId,
            fields: {
              'method': response.requestOptions.method,
              'url': response.requestOptions.uri.toString(),
              'status': response.statusCode,
              'durationMs': durationMs,
              'responseTraceId':
                  response.headers.value(TraceHeader.name) ?? traceId,
              'summary': AppLogger.summarizeForPath(
                response.requestOptions.path,
                response.data,
              ),
            },
          );
          handler.next(response);
        },
        onError: (error, handler) {
          final traceId = error.requestOptions.extra['traceId'] as String?;
          final startedAtUs =
              error.requestOptions.extra['startedAtUs'] as int? ?? 0;
          final durationMs = startedAtUs == 0
              ? null
              : ((DateTime.now().microsecondsSinceEpoch - startedAtUs) / 1000)
                  .round();
          AppLogger.log(
            'api',
            'error',
            traceId: traceId,
            fields: {
              'method': error.requestOptions.method,
              'url': error.requestOptions.uri.toString(),
              'status': error.response?.statusCode,
              'durationMs': durationMs,
              'type': error.type.name,
              'responseTraceId':
                  error.response?.headers.value(TraceHeader.name) ?? traceId,
              'message': error.message,
              'responseSummary': error.response == null
                  ? null
                  : AppLogger.summarizeForPath(
                      error.requestOptions.path,
                      error.response?.data,
                    ),
            },
          );
          handler.next(error);
        },
      ),
    );
  }

  final Dio _dio;
  final AuthStore _store;
  final OutboxStore _outbox = OutboxStore();
  Future<AuthSession?>? _refreshing;
  bool _logoutInProgress = false;

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
    bool queueOnNetworkFailure = false,
  }) async {
    try {
      final response = await _request(() => _dio.post(path, data: data));
      return parser(response.data);
    } on DioException catch (error) {
      // Online-only mode: never queue writes on network failure.
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

  Future<DownloadResult> downloadWithMetadata(
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
      final headers = <String, String>{};
      response.headers.map.forEach((key, value) {
        if (value.isNotEmpty) {
          headers[key.toLowerCase()] = value.join(',');
        }
      });
      return DownloadResult(
        bytes: Uint8List.fromList(response.data as List<int>),
        headers: headers,
      );
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
      if (_isSessionExpiredResponse(error)) {
        final refreshed = await _refreshSession();
        if (refreshed != null) {
          return sender();
        }
        await _forceLogoutOnUnauthorized();
      }
      rethrow;
    }
  }

  Future<void> _forceLogoutOnUnauthorized() async {
    if (_logoutInProgress) {
      return;
    }
    _logoutInProgress = true;
    await _store.clear();
    SessionNavigator.forceLogoutToLogin();
    _logoutInProgress = false;
  }

  bool _isSessionExpiredResponse(DioException error) {
    final status = error.response?.statusCode;
    if (status == 401) {
      return true;
    }
    if (status != 403) {
      return false;
    }
    final path = error.requestOptions.path;
    if (path.startsWith('/auth/')) {
      return false;
    }
    final data = error.response?.data;
    if (data is Map<String, dynamic>) {
      final message = (data['message'] as String? ?? '').toLowerCase();
      if (message.contains('token') ||
          message.contains('jwt') ||
          message.contains('forbidden') ||
          message.contains('access denied') ||
          message.contains('expired')) {
        return true;
      }
    }
    return true;
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
    final traceId = error.requestOptions.extra['traceId'] as String?;
    final statusCode = error.response?.statusCode;
    final path = error.requestOptions.path;
    if ((statusCode == 401 || statusCode == 403) && !path.startsWith('/auth/')) {
      AppLogger.log(
        'api',
        'mapped_error',
        traceId: traceId,
        fields: {
          'type': error.type.name,
          'statusCode': statusCode,
          'mappedMessage': 'Session expired. Please login again.',
        },
      );
      return const ApiException(
        'Session expired. Please login again.',
        statusCode: 401,
      );
    }
    if (_shouldQueue(error)) {
      AppLogger.log(
        'api',
        'mapped_error',
        traceId: traceId,
        fields: {
          'type': error.type.name,
          'mappedMessage':
              'No internet connection. Please check your network and try again.',
        },
      );
      return const ApiException(
        'No internet connection. Please check your network and try again.',
        isNetworkError: true,
      );
    }
    final data = error.response?.data;
    if (data is Map<String, dynamic>) {
      final message = data['message'] as String? ?? 'Request failed';
      AppLogger.log(
        'api',
        'mapped_error',
        traceId: traceId,
        fields: {
          'type': error.type.name,
          'statusCode': error.response?.statusCode,
          'mappedMessage': message,
        },
      );
      return ApiException(message, statusCode: statusCode);
    }
    AppLogger.log(
      'api',
      'mapped_error',
      traceId: traceId,
      fields: {
        'type': error.type.name,
        'statusCode': statusCode,
        'mappedMessage': error.message ?? 'Network request failed',
      },
    );
    return ApiException(
      error.message ?? 'Network request failed',
      statusCode: statusCode,
    );
  }
}

class TraceHeader {
  static const String name = 'X-Trace-Id';
}
