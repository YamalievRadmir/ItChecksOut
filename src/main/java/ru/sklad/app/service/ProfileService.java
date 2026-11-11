package ru.sklad.app.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.UserDetailsManager;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;

@Service
public class ProfileService {
    private final UserDetailsManager users;
    private final PasswordEncoder encoder;
    private final AuditService audit;

    public ProfileService(UserDetailsManager users, PasswordEncoder encoder, AuditService audit) {
        this.users = users;
        this.encoder = encoder;
        this.audit = audit;
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'SELLER')")
    @org.springframework.transaction.annotation.Transactional
    public synchronized void changePassword(String oldPassword, String newPassword) {
        if (oldPassword == null || oldPassword.isEmpty() || newPassword == null || newPassword.isBlank()) {
            throw new IllegalArgumentException("Заполните старый и новый пароль");
        }
        if (newPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Новый пароль слишком длинный: максимум 72 байта UTF-8");
        }
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        var user = users.loadUserByUsername(username);
        if (!encoder.matches(oldPassword, user.getPassword())) {
            throw new BadCredentialsException("Старый пароль указан неверно");
        }
        users.changePassword(oldPassword, encoder.encode(newPassword));
        audit.logAction("ИЗМЕНЕНИЕ", "Пользователь", username, "Пользователь изменил свой пароль");
    }
}
