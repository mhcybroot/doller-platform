import 'dart:io';

import 'package:path_provider/path_provider.dart';
import 'package:share_plus/share_plus.dart';

import '../models/auth_models.dart';
import '../models/domain_models.dart';
import 'api_client.dart';
import 'auth_store.dart';

class DollerRepository {
  DollerRepository(this._api, this._store);

  final ApiClient _api;
  final AuthStore _store;

  Future<AuthSession?> currentSession() => _store.readSession();

  Future<AuthSession> login(String username, String password) async {
    final session = await _api.post<AuthSession>(
      '/auth/login',
      data: {'username': username, 'password': password},
      parser: (json) => AuthSession.fromJson(json as Map<String, dynamic>),
      queueOnNetworkFailure: false,
    );
    await _store.saveSession(session);
    return session;
  }

  Future<AuthSession> initOwner(String username, String password) async {
    final session = await _api.post<AuthSession>(
      '/auth/init-owner',
      data: {'username': username, 'password': password},
      parser: (json) => AuthSession.fromJson(json as Map<String, dynamic>),
      queueOnNetworkFailure: false,
    );
    await _store.saveSession(session);
    return session;
  }

  Future<void> changePassword(String oldPassword, String newPassword) async {
    await _api.post<void>(
      '/auth/change-password',
      data: {'oldPassword': oldPassword, 'newPassword': newPassword},
      parser: (_) {},
      queueOnNetworkFailure: false,
    );
  }

  Future<List<PartyModel>> listParties() async {
    return _api.get<List<PartyModel>>(
      '/parties',
      parser: (json) => (json as List<dynamic>)
          .map((item) => PartyModel.fromJson(item as Map<String, dynamic>))
          .toList(),
    );
  }

  Future<PartyModel> createParty(String name, String phone, String notes) async {
    return _api.post<PartyModel>(
      '/parties',
      data: {'name': name, 'phone': phone, 'notes': notes},
      parser: (json) => PartyModel.fromJson(json as Map<String, dynamic>),
    );
  }

  Future<List<DealSummary>> listDeals({int? partyId}) async {
    return _api.get<List<DealSummary>>(
      '/deals',
      query: partyId == null ? null : {'partyId': partyId},
      parser: (json) => (json as List<dynamic>)
          .map((item) => DealSummary.fromJson(item as Map<String, dynamic>))
          .toList(),
    );
  }

  Future<void> createDeal({
    required String dealType,
    required int partyId,
    required double usdAmount,
    required double bdtRate,
    required String notes,
  }) async {
    await _api.post<void>(
      '/deals',
      data: {
        'dealType': dealType,
        'partyId': partyId,
        'usdAmount': usdAmount,
        'bdtRate': bdtRate,
        'dealTime': DateTime.now().toIso8601String(),
        'notes': notes,
      },
      parser: (_) {},
    );
  }

  Future<void> createSettlement({
    required int partyId,
    int? tradeDealId,
    required double amount,
    required bool allowAdvance,
    required String notes,
  }) async {
    await _api.post<void>(
      '/settlements',
      data: {
        'partyId': partyId,
        'tradeDealId': tradeDealId,
        'bdtAmount': amount,
        'settlementTime': DateTime.now().toIso8601String(),
        'notes': notes,
        'allowAdvance': allowAdvance,
      },
      parser: (_) {},
    );
  }

  Future<SettlementInferenceModel> settlementInference({
    required int partyId,
    int? tradeDealId,
    required double amount,
  }) async {
    return _api.get<SettlementInferenceModel>(
      '/settlements/inference',
      query: {
        'partyId': partyId,
        if (tradeDealId != null) 'tradeDealId': tradeDealId,
        'amount': amount,
      },
      parser: (json) => SettlementInferenceModel.fromJson(json as Map<String, dynamic>),
    );
  }

  Future<void> createExpense({
    required String expenseType,
    int? tradeDealId,
    required double amount,
    required String category,
    required String notes,
  }) async {
    await _api.post<void>(
      '/expenses',
      data: {
        'expenseType': expenseType,
        'tradeDealId': tradeDealId,
        'amountBdt': amount,
        'expenseTime': DateTime.now().toIso8601String(),
        'category': category,
        'notes': notes,
      },
      parser: (_) {},
    );
  }

  Future<DashboardMetrics> dashboard(DateTime from, DateTime to) async {
    return _api.get<DashboardMetrics>(
      '/dashboard',
      query: {'from': _date(from), 'to': _date(to)},
      parser: (json) => DashboardMetrics.fromJson(json as Map<String, dynamic>),
    );
  }

  Future<List<StatementLineModel>> statements(DateTime from, DateTime to) async {
    final isSameDay = _date(from) == _date(to);
    return _api.get<List<StatementLineModel>>(
      isSameDay ? '/statements/daily' : '/statements/range',
      query: isSameDay ? {'date': _date(from)} : {'from': _date(from), 'to': _date(to)},
      parser: (json) => (json as List<dynamic>)
          .map((item) => StatementLineModel.fromJson(item as Map<String, dynamic>))
          .toList(),
    );
  }

  Future<BalanceSheetModel> balanceSheet({
    required String mode,
    DateTime? date,
    int? month,
    int? year,
    DateTime? from,
    DateTime? to,
  }) async {
    return _api.get<BalanceSheetModel>(
      '/reports/balance-sheet',
      query: {
        'mode': mode,
        if (date != null) 'date': _date(date),
        if (month != null) 'month': month,
        if (year != null) 'year': year,
        if (from != null) 'from': _date(from),
        if (to != null) 'to': _date(to),
      },
      parser: (json) => BalanceSheetModel.fromJson(json as Map<String, dynamic>),
    );
  }

  Future<TransactionDetailsModel> transactionDetails({
    required DateTime from,
    required DateTime to,
    String? type,
    int? partyId,
    String? search,
    String? sortField,
    String? sortDirection,
  }) async {
    return _api.get<TransactionDetailsModel>(
      '/reports/transactions',
      query: {
        'from': _date(from),
        'to': _date(to),
        if (type != null && type.isNotEmpty) 'type': type,
        if (partyId != null) 'partyId': partyId,
        if (search != null && search.trim().isNotEmpty) 'search': search.trim(),
        if (sortField != null) 'sortField': sortField,
        if (sortDirection != null) 'sortDirection': sortDirection,
      },
      parser: (json) => TransactionDetailsModel.fromJson(json as Map<String, dynamic>),
    );
  }

  Future<DayClosePreviewModel> previewDayClose(DateTime date) async {
    return _api.get<DayClosePreviewModel>(
      '/day-close/${_date(date)}',
      parser: (json) => DayClosePreviewModel.fromJson(json as Map<String, dynamic>),
    );
  }

  Future<DayCloseResultModel> confirmDayClose(DateTime date) async {
    return _api.post<DayCloseResultModel>(
      '/day-close/${_date(date)}',
      data: const {},
      parser: (json) => DayCloseResultModel.fromJson(json as Map<String, dynamic>),
    );
  }

  Future<DayCloseResultModel> reopenDay(DateTime date, String reason) async {
    return _api.post<DayCloseResultModel>(
      '/day-close/${_date(date)}/reopen',
      data: {'reason': reason},
      parser: (json) => DayCloseResultModel.fromJson(json as Map<String, dynamic>),
    );
  }

  Future<PartyLedgerModel> partyLedger(int partyId) async {
    return _api.get<PartyLedgerModel>(
      '/ledgers/party/$partyId',
      parser: (json) => PartyLedgerModel.fromJson(json as Map<String, dynamic>),
    );
  }

  Future<List<UserModel>> users() async {
    return _api.get<List<UserModel>>(
      '/users',
      parser: (json) => (json as List<dynamic>)
          .map((item) => UserModel.fromJson(item as Map<String, dynamic>))
          .toList(),
    );
  }

  Future<UserModel> createUser(String username, String password, String role) async {
    return _api.post<UserModel>(
      '/users',
      data: {'username': username, 'password': password, 'role': role},
      parser: (json) => UserModel.fromJson(json as Map<String, dynamic>),
    );
  }

  Future<void> deactivateUser(int id) async {
    await _api.post<void>(
      '/users/$id/deactivate',
      data: const {},
      parser: (_) {},
    );
  }

  Future<List<AuditLogModel>> auditLogs() async {
    return _api.get<List<AuditLogModel>>(
      '/audit/logs',
      parser: (json) => (json as List<dynamic>)
          .map((item) => AuditLogModel.fromJson(item as Map<String, dynamic>))
          .toList(),
    );
  }

  Future<void> exportAndShareBalanceSheet({
    required String mode,
    DateTime? date,
    int? month,
    int? year,
    DateTime? from,
    DateTime? to,
  }) async {
    final bytes = await _api.download(
      '/exports/pdf',
      query: {
        'reportType': 'BALANCE_SHEET',
        'mode': mode,
        if (date != null) 'date': _date(date),
        if (month != null) 'month': month,
        if (year != null) 'year': year,
        if (from != null) 'from': _date(from),
        if (to != null) 'to': _date(to),
      },
    );
    final tempDir = await getTemporaryDirectory();
    final file = File('${tempDir.path}/balance_sheet_${DateTime.now().millisecondsSinceEpoch}.pdf');
    await file.writeAsBytes(bytes);
    await Share.shareXFiles([XFile(file.path)]);
  }

  Future<void> exportAndShareTransactionDetails({
    required DateTime from,
    required DateTime to,
    String? type,
    int? partyId,
    String? search,
    String? sortField,
    String? sortDirection,
  }) async {
    final bytes = await _api.download(
      '/exports/pdf',
      query: {
        'reportType': 'TRANSACTION_DETAILS',
        'from': _date(from),
        'to': _date(to),
        if (type != null && type.isNotEmpty) 'type': type,
        if (partyId != null) 'partyId': partyId,
        if (search != null && search.trim().isNotEmpty) 'search': search.trim(),
        if (sortField != null) 'sortField': sortField,
        if (sortDirection != null) 'sortDirection': sortDirection,
      },
    );
    final tempDir = await getTemporaryDirectory();
    final file = File('${tempDir.path}/transaction_details_${_date(from)}_${_date(to)}.pdf');
    await file.writeAsBytes(bytes);
    await Share.shareXFiles([XFile(file.path)]);
  }

  Future<void> exportAndShare(String kind, DateTime from, DateTime to) async {
    await exportAndShareBalanceSheet(
      mode: 'CUSTOM',
      from: from,
      to: to,
    );
  }

  Future<Map<String, int>> queueStats() => _api.queueStats();

  Future<void> flushRetries() => _api.flushRetries();

  Future<void> retryPoison() => _api.retryPoison();

  Future<void> logout() => _api.logout();

  String _date(DateTime value) {
    final month = value.month.toString().padLeft(2, '0');
    final day = value.day.toString().padLeft(2, '0');
    return '${value.year}-$month-$day';
  }
}
