class AuthSession {
  final String accessToken;
  final String refreshToken;
  final int accessExpiresInSeconds;
  final int refreshExpiresInSeconds;
  final String role;
  final bool mustChangePassword;

  const AuthSession({
    required this.accessToken,
    required this.refreshToken,
    required this.accessExpiresInSeconds,
    required this.refreshExpiresInSeconds,
    required this.role,
    required this.mustChangePassword,
  });

  bool get isOwner => role == "OWNER";

  factory AuthSession.fromJson(Map<String, dynamic> json) {
    return AuthSession(
      accessToken: json["accessToken"] as String,
      refreshToken: json["refreshToken"] as String,
      accessExpiresInSeconds: (json["accessExpiresInSeconds"] as num).toInt(),
      refreshExpiresInSeconds: (json["refreshExpiresInSeconds"] as num).toInt(),
      role: json["role"] as String,
      mustChangePassword: json["mustChangePassword"] as bool? ?? false,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      "accessToken": accessToken,
      "refreshToken": refreshToken,
      "accessExpiresInSeconds": accessExpiresInSeconds,
      "refreshExpiresInSeconds": refreshExpiresInSeconds,
      "role": role,
      "mustChangePassword": mustChangePassword,
    };
  }
}
