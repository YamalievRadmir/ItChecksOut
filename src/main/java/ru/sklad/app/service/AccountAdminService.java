package ru.sklad.app.service;

import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.User;
import ru.sklad.app.config.ManagedUserDetailsManager;
import java.util.List;
import java.nio.charset.StandardCharsets;

@Service
@PreAuthorize("hasRole('ADMIN')")
public class AccountAdminService {
    public record Account(String username, String roles, boolean enabled) {}
    private final ManagedUserDetailsManager users;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    public AccountAdminService(ManagedUserDetailsManager users, PasswordEncoder encoder, AuditService audit) {
        this.users = users; this.encoder = encoder; this.audit = audit;
    }
    public List<Account> list() {
        return users.usernames().stream().map(users::loadUserByUsername).map(u -> new Account(u.getUsername(),
                u.getAuthorities().stream().map(a -> a.getAuthority().replace("ROLE_", "")).collect(java.util.stream.Collectors.joining(", ")), u.isEnabled())).toList();
    }
    @org.springframework.transaction.annotation.Transactional
    public synchronized void resetPassword(String username, String newPassword) {
        if (newPassword == null || newPassword.isBlank() || newPassword.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new IllegalArgumentException("Введите новый пароль, не более 72 байт UTF-8");
        var user = users.loadUserByUsername(username);
        users.updateUser(User.withUserDetails(user).password(encoder.encode(newPassword)).build());
        audit.logAction("ИЗМЕНЕНИЕ", "Пользователь", username, "Администратор сбросил пароль");
    }
}
