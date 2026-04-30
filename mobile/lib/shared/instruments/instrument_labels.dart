const List<String> supportedInstrumentCodes = [
  'USD',
  'USD_SA',
  'USD_ID',
  'USD_MY',
  'USD_HK',
  'USD_CN',
  'USD_MV',
  'EXCHANGE_FEE',
  'RMB',
  'MYR',
  'AED',
  'SGD',
  'GBP',
  'AUD',
  'CAD',
  'SAR',
  'HKD',
  'EUR',
  'INR',
];

const Map<String, String> _instrumentDisplayNames = {
  'USD': 'US DOLLAR 🇺🇸',
  'USD_SA': 'US DOLLAR SAUDI 🇺🇸🇸🇦',
  'USD_ID': 'US DOLLAR INDONESIA 🇺🇸🇮🇩',
  'USD_MY': 'US DOLLAR MALAYSIA 🇺🇸🇲🇾',
  'USD_HK': 'US DOLLAR HONGKONG 🇺🇸🇭🇰',
  'USD_CN': 'US DOLLAR CHINA 🇺🇸🇨🇳',
  'USD_MV': 'US DOLLAR MALDIVES 🇺🇸🇲🇻',
  'EXCHANGE_FEE': 'EXCHANGE FEE',
  'RMB': 'RMB 🇨🇳',
  'MYR': 'RINGGIT 🇲🇾',
  'AED': 'DIRHAM 🇦🇪',
  'SGD': 'SIN DOLLAR 🇸🇬',
  'GBP': 'POUND 🇬🇧',
  'AUD': 'AUS DOLLAR 🇦🇺',
  'CAD': 'CANADIAN DOLLAR 🇨🇦',
  'SAR': 'SAUDI RIYAL 🇸🇦',
  'HKD': 'HONGKONG DOLLAR 🇭🇰',
  'EUR': 'EURO 🇪🇺',
  'INR': 'INDIAN RUPEE 🇮🇳',
};

String instrumentDisplayName(String code) {
  final normalized = code.trim().toUpperCase();
  return _instrumentDisplayNames[normalized] ?? normalized;
}
