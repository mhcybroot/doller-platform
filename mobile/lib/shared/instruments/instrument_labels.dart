import '../models/domain_models.dart';

String currencyDisplayName(
  String code, {
  Map<String, String> labels = const {},
}) {
  final normalized = code.trim().toUpperCase();
  if (normalized.isEmpty) {
    return '';
  }
  return labels[normalized] ?? normalized.replaceAll('_', ' ');
}

Map<String, String> currencyLabelMap(Iterable<CurrencyModel> currencies) {
  return {
    for (final currency in currencies)
      currency.code.trim().toUpperCase(): currency.displayName.trim(),
  };
}
