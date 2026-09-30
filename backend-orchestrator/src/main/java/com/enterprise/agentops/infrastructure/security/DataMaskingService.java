package com.enterprise.agentops.infrastructure.security;

import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Servicio de Sanitización y Enmascaramiento de Datos Sensibles (PII / PCI-DSS / Credenciales).
 * Previene la fuga de información confidencial hacia los proveedores de LLM y los registros de auditoría.
 */
@Service
public class DataMaskingService {

    // Regex para números de tarjeta de crédito (Visa, Mastercard, Amex, Discover)
    private static final Pattern CREDIT_CARD_PATTERN = Pattern.compile(
            "\\b(?:4[0-9]{12}(?:[0-9]{3})?|5[1-5][0-9]{14}|3[47][0-9]{13}|6(?:011|5[0-9]{2})[0-9]{12})\\b"
    );

    // Regex para Números de Identificación / Seguridad Social / Tax IDs (formatos comunes)
    private static final Pattern SSN_TAX_ID_PATTERN = Pattern.compile(
            "\\b\\d{3}-\\d{2}-\\d{4}\\b|\\b\\d{9}\\b"
    );

    // Regex para cadenas de conexión JDBC y contraseñas SQL embebidas
    private static final Pattern DB_CONNECTION_STRING_PATTERN = Pattern.compile(
            "(?i)(jdbc:[a-zA-Z0-9:]+://[^\\s'\"]+)|(password\\s*=\\s*['\"][^'\"]+['\"])"
    );

    // Regex para Tokens JWT y claves privadas
    private static final Pattern SECRET_KEY_PATTERN = Pattern.compile(
            "(?i)(bearer\\s+[a-zA-Z0-9_\\-\\.]+\\.[a-zA-Z0-9_\\-\\.]+\\.[a-zA-Z0-9_\\-\\.]+)|(-----BEGIN[A-Z\\s]+PRIVATE KEY-----)"
    );

    public String maskSensitiveData(String input) {
        if (input == null || input.isBlank()) {
            return input;
        }

        String masked = input;
        masked = CREDIT_CARD_PATTERN.matcher(masked).replaceAll("[REDACTED_PCI_CARD]");
        masked = DB_CONNECTION_STRING_PATTERN.matcher(masked).replaceAll("password='[REDACTED_SECRET]'");
        masked = SECRET_KEY_PATTERN.matcher(masked).replaceAll("[REDACTED_TOKEN_OR_KEY]");
        masked = maskTaxIds(masked);

        return masked;
    }

    private String maskTaxIds(String text) {
        Matcher matcher = SSN_TAX_ID_PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String val = matcher.group();
            // Preservar solo los últimos 4 dígitos para trazabilidad operativa sin comprometer PII
            String maskedVal = "***-**-" + val.substring(Math.max(0, val.length() - 4));
            matcher.appendReplacement(sb, Matcher.quoteReplacement(maskedVal));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
