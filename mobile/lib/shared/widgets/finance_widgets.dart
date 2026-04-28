import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import '../../app/app_theme.dart';

final _money = NumberFormat.currency(symbol: 'BDT ', decimalDigits: 2);
final _usd = NumberFormat.currency(symbol: 'USD ', decimalDigits: 2);

String formatBdt(double value) => _money.format(value);
String formatUsd(double value) => _usd.format(value);
String formatDate(DateTime value) => DateFormat('dd MMM yyyy').format(value);
String formatDateTime(DateTime value) => DateFormat('dd MMM, hh:mm a').format(value);

void showAppMessage(BuildContext context, String message, {bool isError = false}) {
  ScaffoldMessenger.of(context).showSnackBar(
    SnackBar(
      content: Text(message),
      backgroundColor: isError ? AppTheme.danger : AppTheme.ink,
    ),
  );
}

class FinanceSection extends StatelessWidget {
  const FinanceSection({
    super.key,
    required this.title,
    required this.child,
    this.trailing,
  });

  final String title;
  final Widget child;
  final Widget? trailing;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(
                    title,
                    style: Theme.of(context).textTheme.titleMedium,
                  ),
                ),
                if (trailing != null) trailing!,
              ],
            ),
            const SizedBox(height: 16),
            child,
          ],
        ),
      ),
    );
  }
}

class MetricCard extends StatelessWidget {
  const MetricCard({
    super.key,
    required this.label,
    required this.value,
    required this.caption,
    this.positive,
  });

  final String label;
  final String value;
  final String caption;
  final bool? positive;

  @override
  Widget build(BuildContext context) {
    final accent = positive == null
        ? AppTheme.accent
        : (positive! ? AppTheme.success : AppTheme.danger);
    return Container(
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(22),
        gradient: LinearGradient(
          colors: [Colors.white, accent.withValues(alpha: 0.05)],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
        border: Border.all(color: AppTheme.border),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label, style: Theme.of(context).textTheme.bodySmall),
          const SizedBox(height: 10),
          Text(
            value,
            style: Theme.of(context).textTheme.titleLarge?.copyWith(
                  color: accent == AppTheme.accent ? AppTheme.ink : accent,
                ),
          ),
          const SizedBox(height: 6),
          Text(caption, style: Theme.of(context).textTheme.bodySmall),
        ],
      ),
    );
  }
}

class EmptyStateCard extends StatelessWidget {
  const EmptyStateCard({
    super.key,
    required this.title,
    required this.message,
    this.action,
  });

  final String title;
  final String message;
  final Widget? action;

  @override
  Widget build(BuildContext context) {
    return FinanceSection(
      title: title,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(message, style: Theme.of(context).textTheme.bodyMedium),
          if (action != null) ...[
            const SizedBox(height: 16),
            action!,
          ],
        ],
      ),
    );
  }
}

enum BalancePillTone {
  receivable,
  payable,
  advanceIn,
  advanceOut,
  aging,
  netPositive,
  netNegative,
  neutral,
}

class BalancePill extends StatelessWidget {
  const BalancePill({
    super.key,
    required this.label,
    required this.value,
    required this.tone,
  });

  final String label;
  final String value;
  final BalancePillTone tone;

  @override
  Widget build(BuildContext context) {
    final scheme = _toneScheme(tone);
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(18),
        color: scheme.$1,
        border: Border.all(color: scheme.$2),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(
            label,
            style: Theme.of(context).textTheme.bodySmall?.copyWith(
                  color: scheme.$3.withValues(alpha: 0.82),
                  fontWeight: FontWeight.w700,
                ),
          ),
          const SizedBox(height: 4),
          Text(
            value,
            style: Theme.of(context).textTheme.titleMedium?.copyWith(
                  color: scheme.$3,
                  fontWeight: FontWeight.w800,
                ),
          ),
        ],
      ),
    );
  }

  (Color, Color, Color) _toneScheme(BalancePillTone tone) {
    return switch (tone) {
      BalancePillTone.receivable => (
          const Color(0xFFE8F7EF),
          const Color(0xFFB8E0C5),
          const Color(0xFF166534),
        ),
      BalancePillTone.payable => (
          const Color(0xFFFFF3E6),
          const Color(0xFFF3D0A4),
          const Color(0xFF9A3412),
        ),
      BalancePillTone.advanceIn => (
          const Color(0xFFE6F4FF),
          const Color(0xFFB8D7F2),
          const Color(0xFF1D4ED8),
        ),
      BalancePillTone.advanceOut => (
          const Color(0xFFF3E8FF),
          const Color(0xFFD7B9F7),
          const Color(0xFF7C3AED),
        ),
      BalancePillTone.aging => (
          const Color(0xFFFFF7DB),
          const Color(0xFFF2E2A5),
          const Color(0xFF92400E),
        ),
      BalancePillTone.netPositive => (
          const Color(0xFFDCFCE7),
          const Color(0xFF86EFAC),
          const Color(0xFF166534),
        ),
      BalancePillTone.netNegative => (
          const Color(0xFFFEE2E2),
          const Color(0xFFFCA5A5),
          const Color(0xFFB91C1C),
        ),
      BalancePillTone.neutral => (
          const Color(0xFFF8FAFC),
          AppTheme.border,
          AppTheme.ink,
        ),
    };
  }
}
