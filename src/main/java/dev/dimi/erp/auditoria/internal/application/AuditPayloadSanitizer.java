package dev.dimi.erp.auditoria.internal.application;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Remove credenciais conhecidas de payloads antes da persistência no log de auditoria. */
final class AuditPayloadSanitizer {

  static final String REDACTED = "[REDACTED]";

  private static final Set<String> SECRET_KEYS =
      Set.of(
          "password",
          "senha",
          "secret",
          "clientsecret",
          "accesstoken",
          "refreshtoken",
          "bootstraptoken",
          "pfx",
          "pfxb64",
          "privatekey",
          "authorization",
          "cookie");

  private AuditPayloadSanitizer() {}

  static Map<String, Object> sanitize(Map<String, Object> payload) {
    Map<String, Object> sanitized = new LinkedHashMap<>();
    payload.forEach(
        (key, value) -> sanitized.put(key, isSecretKey(key) ? REDACTED : sanitizeValue(value)));
    return sanitized;
  }

  private static Object sanitizeValue(Object value) {
    if (value instanceof Map<?, ?> nested) {
      Map<String, Object> converted = new LinkedHashMap<>();
      nested.forEach(
          (key, nestedValue) -> {
            String stringKey = String.valueOf(key);
            converted.put(
                stringKey, isSecretKey(stringKey) ? REDACTED : sanitizeValue(nestedValue));
          });
      return converted;
    }
    if (value instanceof Collection<?> collection) {
      return collection.stream().map(AuditPayloadSanitizer::sanitizeValue).toList();
    }
    if (value != null && value.getClass().isArray()) {
      int length = Array.getLength(value);
      List<Object> converted = new ArrayList<>(length);
      for (int i = 0; i < length; i++) {
        converted.add(sanitizeValue(Array.get(value, i)));
      }
      return converted;
    }
    return value;
  }

  private static boolean isSecretKey(String key) {
    if (key == null) return false;
    String normalized = key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    return SECRET_KEYS.contains(normalized)
        || normalized.endsWith("password")
        || normalized.endsWith("secret")
        || normalized.endsWith("token")
        || normalized.endsWith("tokenhash");
  }
}
