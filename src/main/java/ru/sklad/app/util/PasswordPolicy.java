package ru.sklad.app.util;
/** Passwords are validated before BCrypt encoding, including its UTF-8 byte limit. */
public final class PasswordPolicy {
    private PasswordPolicy() {}
    public static void validate(String password) {
        if (password == null || password.isBlank() || password.length() < 12
                || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
            throw new IllegalArgumentException("Пароль: минимум 12 символов, максимум 72 байта UTF-8");
    }
}