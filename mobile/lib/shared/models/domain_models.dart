class PartyModel {
  final int id;
  final String name;
  final String? phone;
  final String? notes;

  const PartyModel({
    required this.id,
    required this.name,
    this.phone,
    this.notes,
  });

  factory PartyModel.fromJson(Map<String, dynamic> json) {
    return PartyModel(
      id: (json["id"] as num).toInt(),
      name: json["name"] as String,
      phone: json["phone"] as String?,
      notes: json["notes"] as String?,
    );
  }
}

class DealSummary {
  final int id;
  final String partyName;
  final String dealType;
  final String instrumentCode;
  final double quantity;
  final double bdtGross;
  final DateTime dealTime;
  final bool lockedByDayClose;

  const DealSummary({
    required this.id,
    required this.partyName,
    required this.dealType,
    required this.instrumentCode,
    required this.quantity,
    required this.bdtGross,
    required this.dealTime,
    required this.lockedByDayClose,
  });

  factory DealSummary.fromJson(Map<String, dynamic> json) {
    return DealSummary(
      id: (json["id"] as num).toInt(),
      partyName: json["partyName"] as String,
      dealType: json["dealType"] as String,
      instrumentCode: json["instrumentCode"] as String? ?? 'USD',
      quantity: (json["quantity"] as num?)?.toDouble() ??
          (json["usdAmount"] as num).toDouble(),
      bdtGross: (json["bdtGross"] as num).toDouble(),
      dealTime: DateTime.parse(json["dealTime"] as String),
      lockedByDayClose: json["lockedByDayClose"] as bool? ?? false,
    );
  }
}

class DashboardMetrics {
  final double receivableBdt;
  final double payableBdt;
  final double totalPositionValuationBdt;
  final double todayPnL;
  final double periodPnL;
  final List<InstrumentPositionModel> positions;

  const DashboardMetrics({
    required this.receivableBdt,
    required this.payableBdt,
    required this.totalPositionValuationBdt,
    required this.todayPnL,
    required this.periodPnL,
    required this.positions,
  });

  factory DashboardMetrics.fromJson(Map<String, dynamic> json) {
    return DashboardMetrics(
      receivableBdt: (json["receivableBdt"] as num).toDouble(),
      payableBdt: (json["payableBdt"] as num).toDouble(),
      totalPositionValuationBdt:
          (json["totalPositionValuationBdt"] as num?)?.toDouble() ??
              (json["usdPosition"] as num? ?? 0).toDouble(),
      todayPnL: (json["todayPnL"] as num).toDouble(),
      periodPnL: (json["periodPnL"] as num).toDouble(),
      positions: (json["positions"] as List<dynamic>? ?? const [])
          .map((item) =>
              InstrumentPositionModel.fromJson(item as Map<String, dynamic>))
          .toList(),
    );
  }
}

class InstrumentPositionModel {
  final String instrumentCode;
  final double quantity;
  final double valuationBdt;

  const InstrumentPositionModel({
    required this.instrumentCode,
    required this.quantity,
    required this.valuationBdt,
  });

  factory InstrumentPositionModel.fromJson(Map<String, dynamic> json) {
    return InstrumentPositionModel(
      instrumentCode: json["instrumentCode"] as String,
      quantity: (json["quantity"] as num).toDouble(),
      valuationBdt: (json["valuationBdt"] as num).toDouble(),
    );
  }
}

class PartyDueRowModel {
  final int partyId;
  final String partyName;
  final String? phone;
  final String? notes;
  final double receivableBdt;
  final double payableBdt;
  final double netBdt;
  final DateTime? lastActivityAt;

  const PartyDueRowModel({
    required this.partyId,
    required this.partyName,
    required this.phone,
    required this.notes,
    required this.receivableBdt,
    required this.payableBdt,
    required this.netBdt,
    required this.lastActivityAt,
  });

  factory PartyDueRowModel.fromJson(Map<String, dynamic> json) {
    return PartyDueRowModel(
      partyId: (json["partyId"] as num).toInt(),
      partyName: json["partyName"] as String,
      phone: json["phone"] as String?,
      notes: json["notes"] as String?,
      receivableBdt: (json["receivableBdt"] as num).toDouble(),
      payableBdt: (json["payableBdt"] as num).toDouble(),
      netBdt: (json["netBdt"] as num).toDouble(),
      lastActivityAt: (json["lastActivityAt"] as String?) == null
          ? null
          : DateTime.parse(json["lastActivityAt"] as String),
    );
  }
}

class DuesSnapshotModel {
  final double totalReceivableBdt;
  final double totalPayableBdt;
  final double grossBdt;
  final double netBdt;
  final List<PartyDueRowModel> rows;

  const DuesSnapshotModel({
    required this.totalReceivableBdt,
    required this.totalPayableBdt,
    required this.grossBdt,
    required this.netBdt,
    required this.rows,
  });

  factory DuesSnapshotModel.fromJson(Map<String, dynamic> json) {
    return DuesSnapshotModel(
      totalReceivableBdt: (json["totalReceivableBdt"] as num).toDouble(),
      totalPayableBdt: (json["totalPayableBdt"] as num).toDouble(),
      grossBdt: (json["grossBdt"] as num).toDouble(),
      netBdt: (json["netBdt"] as num).toDouble(),
      rows: (json["rows"] as List<dynamic>)
          .map(
              (item) => PartyDueRowModel.fromJson(item as Map<String, dynamic>))
          .toList(),
    );
  }
}

class StatementLineModel {
  final DateTime date;
  final double openingCash;
  final double closingCash;
  final double openingUsd;
  final double closingUsd;
  final double openingReceivableBdt;
  final double closingReceivableBdt;
  final double openingPayableBdt;
  final double closingPayableBdt;
  final double openingAdvanceFromPartyBdt;
  final double closingAdvanceFromPartyBdt;
  final double openingAdvanceToPartyBdt;
  final double closingAdvanceToPartyBdt;
  final double openingAgingBdt;
  final double closingAgingBdt;
  final double pnl;

  const StatementLineModel({
    required this.date,
    required this.openingCash,
    required this.closingCash,
    required this.openingUsd,
    required this.closingUsd,
    required this.openingReceivableBdt,
    required this.closingReceivableBdt,
    required this.openingPayableBdt,
    required this.closingPayableBdt,
    required this.openingAdvanceFromPartyBdt,
    required this.closingAdvanceFromPartyBdt,
    required this.openingAdvanceToPartyBdt,
    required this.closingAdvanceToPartyBdt,
    required this.openingAgingBdt,
    required this.closingAgingBdt,
    required this.pnl,
  });

  factory StatementLineModel.fromJson(Map<String, dynamic> json) {
    return StatementLineModel(
      date: DateTime.parse(json["date"] as String),
      openingCash: (json["openingCash"] as num).toDouble(),
      closingCash: (json["closingCash"] as num).toDouble(),
      openingUsd: (json["openingUsd"] as num).toDouble(),
      closingUsd: (json["closingUsd"] as num).toDouble(),
      openingReceivableBdt:
          (json["openingReceivableBdt"] as num? ?? 0).toDouble(),
      closingReceivableBdt:
          (json["closingReceivableBdt"] as num? ?? 0).toDouble(),
      openingPayableBdt: (json["openingPayableBdt"] as num? ?? 0).toDouble(),
      closingPayableBdt: (json["closingPayableBdt"] as num? ?? 0).toDouble(),
      openingAdvanceFromPartyBdt:
          (json["openingAdvanceFromPartyBdt"] as num? ?? 0).toDouble(),
      closingAdvanceFromPartyBdt:
          (json["closingAdvanceFromPartyBdt"] as num? ?? 0).toDouble(),
      openingAdvanceToPartyBdt:
          (json["openingAdvanceToPartyBdt"] as num? ?? 0).toDouble(),
      closingAdvanceToPartyBdt:
          (json["closingAdvanceToPartyBdt"] as num? ?? 0).toDouble(),
      openingAgingBdt: (json["openingAgingBdt"] as num? ?? 0).toDouble(),
      closingAgingBdt: (json["closingAgingBdt"] as num? ?? 0).toDouble(),
      pnl: (json["pnl"] as num).toDouble(),
    );
  }
}

class BalanceSheetModel {
  final String mode;
  final DateTime from;
  final DateTime to;
  final double openingCash;
  final double closingCash;
  final double openingUsd;
  final double closingUsd;
  final double openingReceivableBdt;
  final double closingReceivableBdt;
  final double openingPayableBdt;
  final double closingPayableBdt;
  final double openingAdvanceFromPartyBdt;
  final double closingAdvanceFromPartyBdt;
  final double openingAdvanceToPartyBdt;
  final double closingAdvanceToPartyBdt;
  final double openingAgingBdt;
  final double closingAgingBdt;
  final double totalPnl;
  final List<StatementLineModel> lines;

  const BalanceSheetModel({
    required this.mode,
    required this.from,
    required this.to,
    required this.openingCash,
    required this.closingCash,
    required this.openingUsd,
    required this.closingUsd,
    required this.openingReceivableBdt,
    required this.closingReceivableBdt,
    required this.openingPayableBdt,
    required this.closingPayableBdt,
    required this.openingAdvanceFromPartyBdt,
    required this.closingAdvanceFromPartyBdt,
    required this.openingAdvanceToPartyBdt,
    required this.closingAdvanceToPartyBdt,
    required this.openingAgingBdt,
    required this.closingAgingBdt,
    required this.totalPnl,
    required this.lines,
  });

  factory BalanceSheetModel.fromJson(Map<String, dynamic> json) {
    return BalanceSheetModel(
      mode: json["mode"] as String,
      from: DateTime.parse(json["from"] as String),
      to: DateTime.parse(json["to"] as String),
      openingCash: (json["openingCash"] as num).toDouble(),
      closingCash: (json["closingCash"] as num).toDouble(),
      openingUsd: (json["openingUsd"] as num).toDouble(),
      closingUsd: (json["closingUsd"] as num).toDouble(),
      openingReceivableBdt:
          (json["openingReceivableBdt"] as num? ?? 0).toDouble(),
      closingReceivableBdt:
          (json["closingReceivableBdt"] as num? ?? 0).toDouble(),
      openingPayableBdt: (json["openingPayableBdt"] as num? ?? 0).toDouble(),
      closingPayableBdt: (json["closingPayableBdt"] as num? ?? 0).toDouble(),
      openingAdvanceFromPartyBdt:
          (json["openingAdvanceFromPartyBdt"] as num? ?? 0).toDouble(),
      closingAdvanceFromPartyBdt:
          (json["closingAdvanceFromPartyBdt"] as num? ?? 0).toDouble(),
      openingAdvanceToPartyBdt:
          (json["openingAdvanceToPartyBdt"] as num? ?? 0).toDouble(),
      closingAdvanceToPartyBdt:
          (json["closingAdvanceToPartyBdt"] as num? ?? 0).toDouble(),
      openingAgingBdt: (json["openingAgingBdt"] as num? ?? 0).toDouble(),
      closingAgingBdt: (json["closingAgingBdt"] as num? ?? 0).toDouble(),
      totalPnl: (json["totalPnl"] as num).toDouble(),
      lines: (json["lines"] as List<dynamic>)
          .map((item) =>
              StatementLineModel.fromJson(item as Map<String, dynamic>))
          .toList(),
    );
  }
}

class TransactionDetailsModel {
  final DateTime from;
  final DateTime to;
  final String typeFilter;
  final int? partyId;
  final String? search;
  final String sortField;
  final String sortDirection;
  final List<TransactionDetailRowModel> rows;

  const TransactionDetailsModel({
    required this.from,
    required this.to,
    required this.typeFilter,
    required this.partyId,
    required this.search,
    required this.sortField,
    required this.sortDirection,
    required this.rows,
  });

  factory TransactionDetailsModel.fromJson(Map<String, dynamic> json) {
    return TransactionDetailsModel(
      from: DateTime.parse(json["from"] as String),
      to: DateTime.parse(json["to"] as String),
      typeFilter: (json["typeFilter"] as String?) ?? '',
      partyId: (json["partyId"] as num?)?.toInt(),
      search: json["search"] as String?,
      sortField: json["sortField"] as String? ?? 'occurredAt',
      sortDirection: json["sortDirection"] as String? ?? 'desc',
      rows: (json["rows"] as List<dynamic>)
          .map((item) =>
              TransactionDetailRowModel.fromJson(item as Map<String, dynamic>))
          .toList(),
    );
  }
}

class TransactionDetailRowModel {
  final String entryType;
  final int entryId;
  final DateTime occurredAt;
  final int? partyId;
  final String? partyName;
  final String? instrumentCode;
  final double? quantity;
  final double amountBdt;
  final double? bdtRate;
  final String? directionLabel;
  final String? referenceLabel;
  final String? paymentMethod;
  final String? paymentReference;
  final String? notes;
  final String? category;

  const TransactionDetailRowModel({
    required this.entryType,
    required this.entryId,
    required this.occurredAt,
    required this.partyId,
    required this.partyName,
    required this.instrumentCode,
    required this.quantity,
    required this.amountBdt,
    required this.bdtRate,
    required this.directionLabel,
    required this.referenceLabel,
    required this.paymentMethod,
    required this.paymentReference,
    required this.notes,
    required this.category,
  });

  factory TransactionDetailRowModel.fromJson(Map<String, dynamic> json) {
    return TransactionDetailRowModel(
      entryType: json["entryType"] as String,
      entryId: (json["entryId"] as num).toInt(),
      occurredAt: DateTime.parse(json["occurredAt"] as String),
      partyId: (json["partyId"] as num?)?.toInt(),
      partyName: json["partyName"] as String?,
      instrumentCode: json["instrumentCode"] as String?,
      quantity: (json["quantity"] as num?)?.toDouble(),
      amountBdt: (json["amountBdt"] as num).toDouble(),
      bdtRate: (json["bdtRate"] as num?)?.toDouble(),
      directionLabel: json["directionLabel"] as String?,
      referenceLabel: json["referenceLabel"] as String?,
      paymentMethod: json["paymentMethod"] as String?,
      paymentReference: json["paymentReference"] as String?,
      notes: json["notes"] as String?,
      category: json["category"] as String?,
    );
  }
}

class DayClosePreviewModel {
  final DateTime date;
  final double totalBuyBdt;
  final double totalSellBdt;
  final double totalExpenseBdt;
  final double realizedProfitLossBdt;
  final bool closed;

  const DayClosePreviewModel({
    required this.date,
    required this.totalBuyBdt,
    required this.totalSellBdt,
    required this.totalExpenseBdt,
    required this.realizedProfitLossBdt,
    required this.closed,
  });

  factory DayClosePreviewModel.fromJson(Map<String, dynamic> json) {
    return DayClosePreviewModel(
      date: DateTime.parse(json["date"] as String),
      totalBuyBdt: (json["totalBuyBdt"] as num).toDouble(),
      totalSellBdt: (json["totalSellBdt"] as num).toDouble(),
      totalExpenseBdt: (json["totalExpenseBdt"] as num).toDouble(),
      realizedProfitLossBdt: (json["realizedProfitLossBdt"] as num).toDouble(),
      closed: json["closed"] as bool? ?? false,
    );
  }
}

class DayCloseResultModel {
  final DateTime date;
  final bool locked;
  final String auditRef;
  final double openingCash;
  final double closingCash;
  final double openingUsd;
  final double closingUsd;
  final double pnl;

  const DayCloseResultModel({
    required this.date,
    required this.locked,
    required this.auditRef,
    required this.openingCash,
    required this.closingCash,
    required this.openingUsd,
    required this.closingUsd,
    required this.pnl,
  });

  factory DayCloseResultModel.fromJson(Map<String, dynamic> json) {
    return DayCloseResultModel(
      date: DateTime.parse(json["date"] as String),
      locked: json["locked"] as bool,
      auditRef: json["auditRef"] as String,
      openingCash: (json["openingCash"] as num).toDouble(),
      closingCash: (json["closingCash"] as num).toDouble(),
      openingUsd: (json["openingUsd"] as num).toDouble(),
      closingUsd: (json["closingUsd"] as num).toDouble(),
      pnl: (json["pnl"] as num).toDouble(),
    );
  }
}

class PartyLedgerLineModel {
  final String kind;
  final DateTime time;
  final double amount;
  final String? note;

  const PartyLedgerLineModel({
    required this.kind,
    required this.time,
    required this.amount,
    this.note,
  });

  factory PartyLedgerLineModel.fromJson(Map<String, dynamic> json) {
    return PartyLedgerLineModel(
      kind: json["kind"] as String,
      time: DateTime.parse(json["time"] as String),
      amount: (json["amount"] as num).toDouble(),
      note: json["note"] as String?,
    );
  }
}

class PartyBalanceSummaryModel {
  final double receivableBdt;
  final double payableBdt;
  final double advanceFromPartyBdt;
  final double advanceToPartyBdt;
  final double netBalanceBdt;
  final double agingDueBdt;

  const PartyBalanceSummaryModel({
    required this.receivableBdt,
    required this.payableBdt,
    required this.advanceFromPartyBdt,
    required this.advanceToPartyBdt,
    required this.netBalanceBdt,
    required this.agingDueBdt,
  });

  factory PartyBalanceSummaryModel.fromJson(Map<String, dynamic> json) {
    return PartyBalanceSummaryModel(
      receivableBdt: (json["receivableBdt"] as num).toDouble(),
      payableBdt: (json["payableBdt"] as num).toDouble(),
      advanceFromPartyBdt: (json["advanceFromPartyBdt"] as num).toDouble(),
      advanceToPartyBdt: (json["advanceToPartyBdt"] as num).toDouble(),
      netBalanceBdt: (json["netBalanceBdt"] as num).toDouble(),
      agingDueBdt: (json["agingDueBdt"] as num).toDouble(),
    );
  }
}

class PartyLedgerModel {
  final int partyId;
  final String partyName;
  final PartyBalanceSummaryModel balances;
  final List<PartyLedgerLineModel> lines;

  const PartyLedgerModel({
    required this.partyId,
    required this.partyName,
    required this.balances,
    required this.lines,
  });

  factory PartyLedgerModel.fromJson(Map<String, dynamic> json) {
    return PartyLedgerModel(
      partyId: (json["partyId"] as num).toInt(),
      partyName: json["partyName"] as String,
      balances: PartyBalanceSummaryModel.fromJson(
          json["balances"] as Map<String, dynamic>),
      lines: (json["lines"] as List<dynamic>)
          .map((line) =>
              PartyLedgerLineModel.fromJson(line as Map<String, dynamic>))
          .toList(),
    );
  }
}

class SettlementInferenceModel {
  final int partyId;
  final int? tradeDealId;
  final PartyBalanceSummaryModel current;
  final PartyBalanceSummaryModel projected;
  final String direction;
  final String basis;
  final double appliedAmount;
  final double advanceAmount;
  final String amountLabel;
  final String summary;

  const SettlementInferenceModel({
    required this.partyId,
    required this.tradeDealId,
    required this.current,
    required this.projected,
    required this.direction,
    required this.basis,
    required this.appliedAmount,
    required this.advanceAmount,
    required this.amountLabel,
    required this.summary,
  });

  factory SettlementInferenceModel.fromJson(Map<String, dynamic> json) {
    return SettlementInferenceModel(
      partyId: (json["partyId"] as num).toInt(),
      tradeDealId: (json["tradeDealId"] as num?)?.toInt(),
      current: PartyBalanceSummaryModel.fromJson(
          json["current"] as Map<String, dynamic>),
      projected: PartyBalanceSummaryModel.fromJson(
          json["projected"] as Map<String, dynamic>),
      direction: json["direction"] as String,
      basis: json["basis"] as String,
      appliedAmount: (json["appliedAmount"] as num).toDouble(),
      advanceAmount: (json["advanceAmount"] as num).toDouble(),
      amountLabel: json["amountLabel"] as String,
      summary: json["summary"] as String,
    );
  }
}

class UserModel {
  final int id;
  final String username;
  final String role;
  final bool active;
  final bool mustChangePassword;

  const UserModel({
    required this.id,
    required this.username,
    required this.role,
    required this.active,
    required this.mustChangePassword,
  });

  factory UserModel.fromJson(Map<String, dynamic> json) {
    return UserModel(
      id: (json["id"] as num).toInt(),
      username: json["username"] as String,
      role: json["role"] as String,
      active: json["active"] as bool? ?? true,
      mustChangePassword: json["mustChangePassword"] as bool? ?? false,
    );
  }
}

class AuditLogModel {
  final int id;
  final String action;
  final String actor;
  final String requestPath;
  final String? metadata;
  final String? reason;
  final DateTime createdAt;

  const AuditLogModel({
    required this.id,
    required this.action,
    required this.actor,
    required this.requestPath,
    required this.metadata,
    required this.reason,
    required this.createdAt,
  });

  factory AuditLogModel.fromJson(Map<String, dynamic> json) {
    return AuditLogModel(
      id: (json["id"] as num).toInt(),
      action: json["action"] as String,
      actor: json["actor"] as String,
      requestPath: json["requestPath"] as String,
      metadata: json["metadata"] as String?,
      reason: json["reason"] as String?,
      createdAt: DateTime.parse(json["createdAt"] as String),
    );
  }
}
