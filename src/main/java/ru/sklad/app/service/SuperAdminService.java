package ru.sklad.app.service;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import ru.sklad.app.entity.*;
import ru.sklad.app.repository.UserRepository;
import java.util.List;

@Service
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Transactional(readOnly = true)
public class SuperAdminService {
    private final ru.sklad.app.repository.AuditLogRepository auditLogs;
    private final ru.sklad.app.repository.ErrorLogRepository errorLogs;
    private final EntityManager em;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    public SuperAdminService(EntityManager em, UserRepository users, PasswordEncoder encoder,
            ru.sklad.app.repository.AuditLogRepository auditLogs, ru.sklad.app.repository.ErrorLogRepository errorLogs) {
        this.auditLogs = auditLogs; this.errorLogs = errorLogs;
        this.em = em; this.users = users; this.encoder = encoder;
    }
    public List<User> users() {
        return em.createQuery("select u from User u left join fetch u.shop order by u.username", User.class).getResultList();
    }
    public List<ErrorLog> errors(int page) {
        return errorLogs.findAll(logPage(page)).getContent();
    }
    public List<AuditLog> audit(int page) {
        return auditLogs.findAll(logPage(page)).getContent();
    }
    private org.springframework.data.domain.Pageable logPage(int page) {
        return org.springframework.data.domain.PageRequest.of(Math.max(0, page), 100, org.springframework.data.domain.Sort.by("timestamp", "id").descending());
    }
    @Transactional
    public void updateUser(Long id, String username, String newPassword) {
        String login = username == null ? "" : username.trim();
        if (login.isBlank() || login.length() > 50) throw new IllegalArgumentException("Логин должен содержать от 1 до 50 символов");
        if (newPassword != null && !newPassword.isEmpty() && (newPassword.isBlank()
                || newPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72))
            throw new IllegalArgumentException("Пароль должен содержать от 1 до 72 байт UTF-8");
        if (newPassword != null && !newPassword.isEmpty()) ru.sklad.app.util.PasswordPolicy.validate(newPassword);
        User user = users.findById(id).orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));
        if (users.findByUsername(login).filter(other -> !other.getId().equals(id)).isPresent())
            throw new IllegalArgumentException("Логин уже занят");
        user.setUsername(login);
        if (newPassword != null && !newPassword.isEmpty()) user.setPassword(encoder.encode(newPassword));
        users.saveAndFlush(user);
    }
    @Transactional
    public String resetPassword(Long id) {
        User user = users.findById(id).orElseThrow();
        byte[] bytes = new byte[12]; new java.security.SecureRandom().nextBytes(bytes);
        String password = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        user.setPassword(encoder.encode(password)); users.saveAndFlush(user);
        return password;
    }
    @Transactional
    public void blockUser(Long id) {
        User user = users.findById(id).orElseThrow();
        if (user.getUsername().equals(org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName()))
            throw new IllegalArgumentException("Нельзя заблокировать свою учетную запись");
        user.setEnabled(false); users.saveAndFlush(user);
    }}