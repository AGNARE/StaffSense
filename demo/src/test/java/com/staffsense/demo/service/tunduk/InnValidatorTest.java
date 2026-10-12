package com.staffsense.demo.service.tunduk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Проверка формата ИНН (Q-015: ровно 14 цифр, без контрольной суммы).
 */
class InnValidatorTest {

    private final InnValidator validator = new InnValidator();

    @Test
    void acceptsFourteenDigits() {
        assertTrue(validator.isValid("12345678901234"));
        assertTrue(validator.isValid("00000000000000"));
    }

    @Test
    void trimsSurroundingWhitespace() {
        assertTrue(validator.isValid("  12345678901234  "));
    }

    @Test
    void rejectsWrongLength() {
        assertFalse(validator.isValid("123"));
        assertFalse(validator.isValid("1234567890123"));   // 13 цифр
        assertFalse(validator.isValid("123456789012345")); // 15 цифр
    }

    @Test
    void rejectsNonDigits() {
        assertFalse(validator.isValid("1234567890123a"));
        assertFalse(validator.isValid("1234 5678 901234"));
    }

    @Test
    void rejectsNullAndBlank() {
        assertFalse(validator.isValid(null));
        assertFalse(validator.isValid(""));
        assertFalse(validator.isValid("   "));
    }
}
