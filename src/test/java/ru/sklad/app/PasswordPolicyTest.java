package ru.sklad.app;

import org.junit.jupiter.api.Test;
import ru.sklad.app.util.PasswordPolicy;
import static org.junit.jupiter.api.Assertions.*;

class PasswordPolicyTest {
    @Test void acceptsEightCharactersButLimitsUtf8Bytes() {
        assertDoesNotThrow(() -> PasswordPolicy.validate("12345678"));
        assertDoesNotThrow(() -> PasswordPolicy.validate("я".repeat(36)));
        assertThrows(IllegalArgumentException.class, () -> PasswordPolicy.validate("1234567"));
        assertThrows(IllegalArgumentException.class, () -> PasswordPolicy.validate("я".repeat(37)));
        assertThrows(IllegalArgumentException.class, () -> PasswordPolicy.validate(" ".repeat(8)));
    }
}
