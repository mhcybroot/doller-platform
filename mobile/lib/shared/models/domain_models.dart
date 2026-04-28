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
  final double usdAmount;
  final double bdtGross;
  final DateTime dealTime;
  final bool lockedByDayClose;

  const DealSummary({
    required this.id,
    required this.partyName,
    required this.dealType,
    required this.usdAmount,
    required this.bdtGross,
    required this.dealTime,
    required this.lockedByDayClose,
  });

  factory DealSummary.fromJson(Map<String, dynamic> json) {
    return DealSummary(
      id: (json["id"] as num).toInt(),
      partyName: json["partyName"] as String,
      dealType: json["dealType"] as String,
      usdAmount: (json["usdAmount"] as num).toDouble(),
      bdtGross: (json["bdtGross"] as num).toDouble(),
      dealTime: DateTime.parse(json["dealTime"] as String),
      lockedByDayClose: json["lockedByDayClose"] as bool? ?? false,
    );
  }
}

class DashboardMetrics {
  final double receivableBdt;
  final double payableBdt;
  final double usdPosition;
  final double todayPnL;
  final double periodPnL;

  const DashboardMetrics({
    required this.receivableBdt,
    required this.payableBdt,
    required this.usdPosition,
    required this.todayPnL,
    required this.periodPnL,
  });

  factory DashboardMetrics.fromJson(Map<String, dynamic> json) {
    return DashboardMetrics(
      receivableBdt: (json["receivableBdt"] as num).toDouble(),
      payableBdt: (json["payableBdt"] as num).toDouble(),
      usdPosition: (json["usdPosition"] as num).toDouble(),
      todayPnL: (json["todayPnL"] as num).toDouble(),
      periodPnL: (json["periodPnL"] as num).toDouble(),
    );
  }
}

class StatementLineModel {
  final DateTime date;
  final double openingCash;
  final double closingCash;
  final double openingUsd;
  final double closingUsd;
  final double pnl;

  const StatementLineModel({
    required this.date,
    required this.openingCash,
    required this.closingCash,
    required this.openingUsd,
    required this.closingUsd,
    required this.pnl,
  });

  factory StatementLineModel.fromJson(Map<String, dynamic> json) {
    return StatementLineModel(
      date: DateTime.parse(json["date"] as String),
      openingCash: (json["openingCash"] as num).toDouble(),
      closingCash: (json["closingCash"] as num).toDouble(),
      openingUsd: (json["openingUsd"] as num).toDouble(),
      closingUsd: (json["closingUsd"] as num).toDouble(),
      pnl: (json["pnl"] as num).toDouble(),
    );
  }
}

class DayClosePreviewModel {
  final DateTime date;
  final double totalBuyBdt;
  final double totalSellBdt;
  final double totalExpenseBdt;
  final double realizedProfitLossBdt;

  const DayClosePreviewModel({
    required this.date,
    required this.totalBuyBdt,
    required this.totalSellBdt,
    required this.totalExpenseBdt,
    required this.realizedProfitLossBdt,
  });

  factory DayClosePreviewModel.fromJson(Map<String, dynamic> json) {
    return DayClosePreviewModel(
      date: DateTime.parse(json["date"] as String),
      totalBuyBdt: (json["totalBuyBdt"] as num).toDouble(),
      totalSellBdt: (json["totalSellBdt"] as num).toDouble(),
      totalExpenseBdt: (json["totalExpenseBdt"] as num).toDouble(),
      realizedProfitLossBdt: (json["realizedProfitLossBdt"] as num).toDouble(),
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
      balances: PartyBalanceSummaryModel.fromJson(json["balances"] as Map<String, dynamic>),
      lines: (json["lines"] as List<dynamic>)
          .map((line) => PartyLedgerLineModel.fromJson(line as Map<String, dynamic>))
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
      current: PartyBalanceSummaryModel.fromJson(json["current"] as Map<String, dynamic>),
      projected: PartyBalanceSummaryModel.fromJson(json["projected"] as Map<String, dynamic>),
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
