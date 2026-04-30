import 'package:flutter_dotenv/flutter_dotenv.dart';

class AppEnv {
  static const String _prodBaseUrl = 'http://76.13.221.43:8089';
  static const String _defaultLocalBaseUrl = 'http://10.0.2.2:8089';

  static bool get isLocal {
    final raw = dotenv.env['_local']?.trim().toLowerCase() ?? 'false';
    return raw == 'true' || raw == '1' || raw == 'yes';
  }

  static String get baseUrl {
    if (isLocal) {
      return dotenv.env['BASE_URL_LOCAL']?.trim().isNotEmpty == true
          ? dotenv.env['BASE_URL_LOCAL']!.trim()
          : _defaultLocalBaseUrl;
    }
    return _prodBaseUrl;
  }
}
