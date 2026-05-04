import 'dart:io';

import 'package:path_provider/path_provider.dart';
import 'package:share_plus/share_plus.dart';

import '../models/auth_models.dart';
import '../models/domain_models.dart';
import 'api_client.dart';
import 'app_logger.dart';
import 'auth_store.dart';

class PdfExportResult {
  final File file;
  final String filename;

  const PdfExportResult({required this.file, required this.filename});
}

class DollerRepository {
  DollerRepository(this._api, this._store);

  final ApiClient _api;
  final AuthStore _store;

  Future<AuthSession?> currentSession() => _store.readSession();

  Future<AuthSession> login(
    String username,
    String password, {
    bool rememberMe = false,
  }) async {
    final session = await _api.post<AuthSession>(
      '/auth/login',
      data: {'username': username, 'password': password},
      parser: (json) => AuthSession.fromJson(json as Map<String, dynamic>),
      queueOnNetworkFailure: false,
    );
    await _store.saveSession(session, rememberMe: rememberMe);
    return session;
  }

  Future<AuthSession> initOwner(String username, String password) async {
    final session = await _api.post<AuthSession>(
      '/auth/init-owner',
      data: {'username': username, 'password': password},
      parser: (json) => AuthSession.fromJson(json as Map<String, dynamic>),
      queueOnNetworkFailure: false,
    );
    await _store.saveSession(session, rememberMe: true);
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

  Future<PartyModel> createParty(
    String name,
    String phone,
    String address,
    String notes, {
    double openingReceivableBdt = 0,
    double openingPayableBdt = 0,
  }) async {
    return _api.post<PartyModel>(
      '/parties',
      data: {
        'name': name,
        'phone': phone,
        'address': address,
        'notes': notes,
        'openingReceivableBdt': openingReceivableBdt,
        'openingPayableBdt': openingPayableBdt,
      },
      parser: (json) => PartyModel.fromJson(json as Map<String, dynamic>),
    );
  }

  Future<PartyModel> updateParty(
    int id,
    String name,
    String phone,
    String address,
    String notes,
  ) async {
    return _api.put<PartyModel>(
      '/parties/$id',
      data: {
        'name': name,
        'phone': phone,
        'address': address,
        'notes': notes,
      },
      parser: (json) => PartyModel.fromJson(json as Map<String, dynamic>),
    );
  }

  Future<void> deleteParty(int id) async {
    await _api.delete<void>(
      '/parties/$id',
      parser: (_) {},
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
    required String instrumentCode,
    required double quantity,
    required double bdtRate,
    required String notes,
  }) async {
    AppLogger.log('repo', 'createDeal:start', fields: {
      'dealType': dealType,
      'partyId': partyId,
      'instrumentCode': instrumentCode,
      'quantity': quantity,
      'bdtRate': bdtRate,
      'notes': notes,
    });
    await _api.post<void>(
      '/deals',
      data: {
        'dealType': dealType,
        'partyId': partyId,
        'instrumentCode': instrumentCode,
        'quantity': quantity,
        'bdtRate': bdtRate,
        'dealTime': DateTime.now().toIso8601String(),
        'notes': notes,
      },
      parser: (_) {},
    );
    AppLogger.log('repo', 'createDeal:success', fields: {
      'dealType': dealType,
      'partyId': partyId,
      'instrumentCode': instrumentCode,
      'quantity': quantity,
      'bdtRate': bdtRate,
    });
  }

  Future<void> updateDeal({
    required int id,
    required String dealType,
    required int partyId,
    required String instrumentCode,
    required double quantity,
    required double bdtRate,
    required DateTime dealTime,
    required String notes,
  }) async {
    await _api.put<void>(
      '/deals/$id',
      data: {
        'dealType': dealType,
        'partyId': partyId,
        'instrumentCode': instrumentCode,
        'quantity': quantity,
        'bdtRate': bdtRate,
        'dealTime': dealTime.toIso8601String(),
        'notes': notes,
      },
      parser: (_) {},
    );
  }

  Future<void> deleteDeal(int id) async {
    await _api.delete<void>(
      '/deals/$id',
      parser: (_) {},
    );
  }

  Future<void> createSettlement({
    required int partyId,
    int? tradeDealId,
    required double amount,
    required String paymentMethod,
    String? paymentReference,
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
        'paymentMethod': paymentMethod,
        'paymentReference': paymentReference,
        'notes': notes,
        'allowAdvance': allowAdvance,
      },
      parser: (_) {},
    );
  }

  Future<void> updateSettlement({
    required int id,
    required int partyId,
    int? tradeDealId,
    required double amount,
    required String paymentMethod,
    String? paymentReference,
    required bool allowAdvance,
    required String notes,
    required DateTime settlementTime,
  }) async {
    await _api.put<void>(
      '/settlements/$id',
      data: {
        'partyId': partyId,
        'tradeDealId': tradeDealId,
        'bdtAmount': amount,
        'settlementTime': settlementTime.toIso8601String(),
        'paymentMethod': paymentMethod,
        'paymentReference': paymentReference,
        'notes': notes,
        'allowAdvance': allowAdvance,
      },
      parser: (_) {},
    );
  }

  Future<void> deleteSettlement(int id) async {
    await _api.delete<void>(
      '/settlements/$id',
      parser: (_) {},
    );
  }

  Future<SettlementInferenceModel> settlementInference({
    required int partyId,
    int? tradeDealId,
    required double amount,
  }) async {
    AppLogger.log('repo', 'settlementInference:start', fields: {
      'partyId': partyId,
      'tradeDealId': tradeDealId,
      'amount': amount,
    });
    final result = await _api.get<SettlementInferenceModel>(
      '/settlements/inference',
      query: {
        'partyId': partyId,
        if (tradeDealId != null) 'tradeDealId': tradeDealId,
        'amount': amount,
      },
      parser: (json) =>
          SettlementInferenceModel.fromJson(json as Map<String, dynamic>),
    );
    AppLogger.log('repo', 'settlementInference:success', fields: {
      'partyId': partyId,
      'tradeDealId': tradeDealId,
      'amount': amount,
      'direction': result.direction,
      'basis': result.basis,
      'currentReceivableBdt': result.current.receivableBdt,
      'currentPayableBdt': result.current.payableBdt,
      'projectedReceivableBdt': result.projected.receivableBdt,
      'projectedPayableBdt': result.projected.payableBdt,
    });
    return result;
  }

  Future<void> createExpense({
    required String expenseType,
    required double amount,
    required String category,
    required String notes,
  }) async {
    await _api.post<void>(
      '/expenses',
      data: {
        'expenseType': expenseType,
        'amountBdt': amount,
        'expenseTime': DateTime.now().toIso8601String(),
        'category': category,
        'notes': notes,
      },
      parser: (_) {},
    );
  }

  Future<void> updateExpense({
    required int id,
    required String expenseType,
    required double amount,
    required String category,
    required String notes,
    required DateTime expenseTime,
  }) async {
    await _api.put<void>(
      '/expenses/$id',
      data: {
        'expenseType': expenseType,
        'amountBdt': amount,
        'expenseTime': expenseTime.toIso8601String(),
        'category': category,
        'notes': notes,
      },
      parser: (_) {},
    );
  }

  Future<void> deleteExpense(int id) async {
    await _api.delete<void>(
      '/expenses/$id',
      parser: (_) {},
    );
  }

  Future<List<CompanyModel>> listCompanies() async {
    return _api.get<List<CompanyModel>>(
      '/owner/companies',
      parser: (json) => (json as List<dynamic>)
          .map((item) => CompanyModel.fromJson(item as Map<String, dynamic>))
          .toList(),
    );
  }

  Future<CompanyModel> createCompany({
    required String name,
    String? notes,
  }) async {
    return _api.post<CompanyModel>(
      '/owner/companies',
      data: {'name': name, 'notes': notes},
      parser: (json) => CompanyModel.fromJson(json as Map<String, dynamic>),
    );
  }

  Future<CompanyModel> updateCompany({
    required int id,
    required String name,
    String? notes,
  }) async {
    return _api.put<CompanyModel>(
      '/owner/companies/$id',
      data: {'name': name, 'notes': notes},
      parser: (json) => CompanyModel.fromJson(json as Map<String, dynamic>),
    );
  }

  Future<void> deleteCompany(int id) async {
    await _api.delete<void>(
      '/owner/companies/$id',
      parser: (_) {},
    );
  }

  Future<CustomEntryListModel> listCustomEntries({
    required int companyId,
    required DateTime from,
    required DateTime to,
    String? search,
  }) async {
    return _api.get<CustomEntryListModel>(
      '/owner/custom-entries',
      query: {
        'companyId': companyId,
        'from': _date(from),
        'to': _date(to),
        if (search != null && search.trim().isNotEmpty) 'search': search.trim(),
      },
      parser: (json) =>
          CustomEntryListModel.fromJson(json as Map<String, dynamic>),
    );
  }

  Future<CustomEntryRowModel> createCustomEntry({
    required int companyId,
    required String entryType,
    required double amount,
    DateTime? entryTime,
    required String itemPurpose,
    String? notes,
  }) async {
    return _api.post<CustomEntryRowModel>(
      '/owner/custom-entries',
      data: {
        'companyId': companyId,
        'entryType': entryType,
        'amountBdt': amount,
        if (entryTime != null) 'entryTime': entryTime.toIso8601String(),
        'itemPurpose': itemPurpose,
        'notes': notes,
      },
      parser: (json) =>
          CustomEntryRowModel.fromJson(json as Map<String, dynamic>),
    );
  }

  Future<CustomEntryRowModel> updateCustomEntry({
    required int id,
    required int companyId,
    required String entryType,
    required double amount,
    required DateTime entryTime,
    required String itemPurpose,
    String? notes,
  }) async {
    return _api.put<CustomEntryRowModel>(
      '/owner/custom-entries/$id',
      data: {
        'companyId': companyId,
        'entryType': entryType,
        'amountBdt': amount,
        'entryTime': entryTime.toIso8601String(),
        'itemPurpose': itemPurpose,
        'notes': notes,
      },
      parser: (json) =>
          CustomEntryRowModel.fromJson(json as Map<String, dynamic>),
    );
  }

  Future<void> deleteCustomEntry(int id) async {
    await _api.delete<void>(
      '/owner/custom-entries/$id',
      parser: (_) {},
    );
  }

  Future<DashboardMetrics> dashboard(DateTime from, DateTime to) async {
    AppLogger.log('repo', 'dashboard:start', fields: {
      'from': _date(from),
      'to': _date(to),
    });
    final result = await _api.get<DashboardMetrics>(
      '/dashboard',
      query: {'from': _date(from), 'to': _date(to)},
      parser: (json) => DashboardMetrics.fromJson(json as Map<String, dynamic>),
    );
    AppLogger.log('repo', 'dashboard:success', fields: {
      'from': _date(from),
      'to': _date(to),
      'receivableBdt': result.receivableBdt,
      'payableBdt': result.payableBdt,
      'totalPositionValuationBdt': result.totalPositionValuationBdt,
      'positionsCount': result.positions.length,
    });
    return result;
  }

  Future<DashboardPnlExplainModel> dashboardPnlExplain(
      DateTime from, DateTime to) async {
    return _api.get<DashboardPnlExplainModel>(
      '/dashboard/pnl-explain',
      query: {'mode': 'CUSTOM', 'from': _date(from), 'to': _date(to)},
      parser: (json) =>
          DashboardPnlExplainModel.fromJson(json as Map<String, dynamic>),
    );
  }

  Future<DashboardPnlExplainModel> dashboardPnlExplainByMode({
    required String mode,
    DateTime? date,
    int? month,
    int? year,
    DateTime? from,
    DateTime? to,
  }) async {
    return _api.get<DashboardPnlExplainModel>(
      '/dashboard/pnl-explain',
      query: {
        'mode': mode,
        if (date != null) 'date': _date(date),
        if (month != null) 'month': month,
        if (year != null) 'year': year,
        if (from != null) 'from': _date(from),
        if (to != null) 'to': _date(to),
      },
      parser: (json) =>
          DashboardPnlExplainModel.fromJson(json as Map<String, dynamic>),
    );
  }

  Future<DuesSnapshotModel> fetchDuesSnapshot() async {
    AppLogger.log('repo', 'fetchDuesSnapshot:start');
    final result = await _api.get<DuesSnapshotModel>(
      '/dues/snapshot',
      parser: (json) =>
          DuesSnapshotModel.fromJson(json as Map<String, dynamic>),
    );
    AppLogger.log('repo', 'fetchDuesSnapshot:success', fields: {
      'totalReceivableBdt': result.totalReceivableBdt,
      'totalPayableBdt': result.totalPayableBdt,
      'grossBdt': result.grossBdt,
      'netBdt': result.netBdt,
      'rowCount': result.rows.length,
    });
    return result;
  }

  Future<List<StatementLineModel>> statements(
      DateTime from, DateTime to) async {
    final isSameDay = _date(from) == _date(to);
    return _api.get<List<StatementLineModel>>(
      isSameDay ? '/statements/daily' : '/statements/range',
      query: isSameDay
          ? {'date': _date(from)}
          : {'from': _date(from), 'to': _date(to)},
      parser: (json) => (json as List<dynamic>)
          .map((item) =>
              StatementLineModel.fromJson(item as Map<String, dynamic>))
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
      parser: (json) =>
          BalanceSheetModel.fromJson(json as Map<String, dynamic>),
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
      parser: (json) =>
          TransactionDetailsModel.fromJson(json as Map<String, dynamic>),
    );
  }

  Future<DayClosePreviewModel> previewDayClose(DateTime date) async {
    return _api.get<DayClosePreviewModel>(
      '/day-close/${_date(date)}',
      parser: (json) =>
          DayClosePreviewModel.fromJson(json as Map<String, dynamic>),
    );
  }

  Future<DayCloseResultModel> confirmDayClose(DateTime date) async {
    return _api.post<DayCloseResultModel>(
      '/day-close/${_date(date)}',
      data: const {},
      parser: (json) =>
          DayCloseResultModel.fromJson(json as Map<String, dynamic>),
    );
  }

  Future<DayCloseResultModel> reopenDay(DateTime date, String reason) async {
    return _api.post<DayCloseResultModel>(
      '/day-close/${_date(date)}/reopen',
      data: {'reason': reason},
      parser: (json) =>
          DayCloseResultModel.fromJson(json as Map<String, dynamic>),
    );
  }

  Future<PartyLedgerModel> partyLedger(
    int partyId, {
    DateTime? from,
    DateTime? to,
    String? type,
    String? search,
    String? sortField,
    String? sortDirection,
  }) async {
    AppLogger.log('repo', 'partyLedger:start', fields: {'partyId': partyId});
    final result = await _api.get<PartyLedgerModel>(
      '/ledgers/party/$partyId',
      query: {
        if (from != null) 'from': _date(from),
        if (to != null) 'to': _date(to),
        if (type != null && type.isNotEmpty) 'type': type,
        if (search != null && search.trim().isNotEmpty) 'search': search.trim(),
        if (sortField != null && sortField.isNotEmpty) 'sortField': sortField,
        if (sortDirection != null && sortDirection.isNotEmpty)
          'sortDirection': sortDirection,
      },
      parser: (json) => PartyLedgerModel.fromJson(json as Map<String, dynamic>),
    );
    AppLogger.log('repo', 'partyLedger:success', fields: {
      'partyId': partyId,
      'receivableBdt': result.balances.receivableBdt,
      'payableBdt': result.balances.payableBdt,
      'netBalanceBdt': result.balances.netBalanceBdt,
      'lineCount': result.lines.length,
    });
    return result;
  }

  Future<List<UserModel>> users() async {
    return _api.get<List<UserModel>>(
      '/users',
      parser: (json) => (json as List<dynamic>)
          .map((item) => UserModel.fromJson(item as Map<String, dynamic>))
          .toList(),
    );
  }

  Future<UserModel> createUser(
      String username, String password, String role) async {
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
    final file = File(
        '${tempDir.path}/balance_sheet_${DateTime.now().millisecondsSinceEpoch}.pdf');
    await file.writeAsBytes(bytes);
    await Share.shareXFiles([XFile(file.path)]);
  }

  Future<void> exportAndShareTransactionDetails({
    required DateTime from,
    required DateTime to,
    String? type,
    int? partyId,
    String? fallbackPartyName,
    String? search,
    String? sortField,
    String? sortDirection,
  }) async {
    final result = await exportTransactionDetailsPdf(
      from: from,
      to: to,
      type: type,
      partyId: partyId,
      fallbackPartyName: fallbackPartyName,
      search: search,
      sortField: sortField,
      sortDirection: sortDirection,
    );
    await Share.shareXFiles([XFile(result.file.path)],
        fileNameOverrides: [result.filename]);
  }

  Future<PdfExportResult> exportTransactionDetailsPdf({
    required DateTime from,
    required DateTime to,
    String? type,
    int? partyId,
    String? fallbackPartyName,
    String? search,
    String? sortField,
    String? sortDirection,
  }) async {
    final download = await _api.downloadWithMetadata(
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
    final fallbackName = _fallbackTransactionFilename(
      from: from,
      to: to,
      fallbackPartyName: fallbackPartyName,
    );
    final filename = _resolveExportFilename(
      contentDisposition: download.headers['content-disposition'],
      fallbackName: fallbackName,
    );
    final file = File('${tempDir.path}/$filename');
    await file.writeAsBytes(download.bytes);
    return PdfExportResult(file: file, filename: filename);
  }

  Future<PdfExportResult> exportCustomEntriesPdf({
    required int companyId,
    required DateTime from,
    required DateTime to,
    String? entryTypeFilter,
  }) async {
    final download = await _api.downloadWithMetadata(
      '/exports/pdf',
      query: {
        'reportType': 'CUSTOM_ENTRIES',
        'companyId': companyId,
        'from': _date(from),
        'to': _date(to),
        if (entryTypeFilter != null && entryTypeFilter.isNotEmpty)
          'entryTypeFilter': entryTypeFilter,
      },
    );
    final tempDir = await getTemporaryDirectory();
    final fallbackName = 'custom_entries_${_date(from)}_${_date(to)}.pdf';
    final filename = _resolveExportFilename(
      contentDisposition: download.headers['content-disposition'],
      fallbackName: fallbackName,
    );
    final file = File('${tempDir.path}/$filename');
    await file.writeAsBytes(download.bytes);
    return PdfExportResult(file: file, filename: filename);
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

  String _resolveExportFilename({
    required String? contentDisposition,
    required String fallbackName,
  }) {
    final parsed = _extractFilenameFromContentDisposition(contentDisposition);
    if (parsed == null || parsed.isEmpty) {
      return _ensurePdfExtension(_sanitizeFilename(fallbackName));
    }
    return _ensurePdfExtension(_sanitizeFilename(parsed));
  }

  String? _extractFilenameFromContentDisposition(String? contentDisposition) {
    if (contentDisposition == null || contentDisposition.trim().isEmpty) {
      return null;
    }
    final utf8Match = RegExp(r"filename\*=UTF-8''([^;]+)", caseSensitive: false)
        .firstMatch(contentDisposition);
    if (utf8Match != null) {
      return Uri.decodeComponent(utf8Match.group(1)!);
    }
    final quoted = RegExp(r'filename="([^"]+)"', caseSensitive: false)
        .firstMatch(contentDisposition);
    if (quoted != null) {
      return quoted.group(1);
    }
    final plain = RegExp(r'filename=([^;]+)', caseSensitive: false)
        .firstMatch(contentDisposition);
    if (plain != null) {
      return plain.group(1)?.trim();
    }
    return null;
  }

  String _fallbackTransactionFilename({
    required DateTime from,
    required DateTime to,
    String? fallbackPartyName,
  }) {
    final dateRange = '${_date(from)}_${_date(to)}';
    final partySlug = _partySlug(fallbackPartyName);
    if (partySlug.isNotEmpty) {
      return '${partySlug}_details_$dateRange.pdf';
    }
    return 'statement_details_$dateRange.pdf';
  }

  String _partySlug(String? name) {
    if (name == null || name.trim().isEmpty) {
      return '';
    }
    final out = name
        .toLowerCase()
        .replaceAll(RegExp(r'[^a-z0-9\s_-]'), '')
        .trim()
        .replaceAll(RegExp(r'\s+'), '_')
        .replaceAll(RegExp(r'_+'), '_')
        .replaceAll(RegExp(r'^[_-]+|[_-]+$'), '');
    return out.length > 60
        ? out.substring(0, 60).replaceAll(RegExp(r'_+$'), '')
        : out;
  }

  String _sanitizeFilename(String value) {
    final cleaned = value
        .replaceAll(RegExp(r'[\\/:*?"<>|]'), '')
        .replaceAll(RegExp(r'\s+'), '_')
        .trim();
    return cleaned.isEmpty ? 'statement_details.pdf' : cleaned;
  }

  String _ensurePdfExtension(String value) {
    return value.toLowerCase().endsWith('.pdf') ? value : '$value.pdf';
  }
}
