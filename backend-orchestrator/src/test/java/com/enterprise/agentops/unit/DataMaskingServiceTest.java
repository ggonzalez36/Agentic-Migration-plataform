package com.enterprise.agentops.unit;

import com.enterprise.agentops.infrastructure.security.DataMaskingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Unit Test: DataMaskingService - PII & PCI-DSS Redaction")
class DataMaskingServiceTest {

    private DataMaskingService maskingService;

    @BeforeEach
    void setUp() {
        maskingService = new DataMaskingService();
    }

    @Test
    @DisplayName("Should redact Visa and Mastercard numbers from legacy PL/SQL comments or payloads")
    void shouldMaskCreditCardNumbers() {
        String legacyCodeWithCards = """
            -- Test case with customer card 4111111111111111 and backup card 5500000000000004
            PROCEDURE charge_account(p_account_id IN VARCHAR2) IS ...
            """;

        String sanitized = maskingService.maskSensitiveData(legacyCodeWithCards);

        assertFalse(sanitized.contains("4111111111111111"), "Credit card 1 should be redacted");
        assertFalse(sanitized.contains("5500000000000004"), "Credit card 2 should be redacted");
        assertTrue(sanitized.contains("[REDACTED_PCI_CARD]"));
    }

    @Test
    @DisplayName("Should mask Tax IDs and SSN keeping only the last 4 digits for operational audit")
    void shouldMaskSsnAndTaxIds() {
        String payload = "Client identifier: 123-45-6789 registered in core ledger";

        String sanitized = maskingService.maskSensitiveData(payload);

        assertFalse(sanitized.contains("123-45-6789"));
        assertTrue(sanitized.contains("***-**-6789"));
    }

    @Test
    @DisplayName("Should mask JDBC connection strings and database passwords")
    void shouldMaskDatabaseCredentials() {
        String config = "DB_CONN = 'jdbc:oracle:thin:@prod-db.corp:1521:ORCL' password = 'SuperSecretDbPassword123!'";

        String sanitized = maskingService.maskSensitiveData(config);

        assertFalse(sanitized.contains("SuperSecretDbPassword123!"));
        assertTrue(sanitized.contains("password='[REDACTED_SECRET]'"));
    }

    @Test
    @DisplayName("Should handle null and empty strings gracefully")
    void shouldHandleNullOrEmpty() {
        assertNull(maskingService.maskSensitiveData(null));
        assertEquals("", maskingService.maskSensitiveData(""));
        assertEquals("   ", maskingService.maskSensitiveData("   "));
    }
}
