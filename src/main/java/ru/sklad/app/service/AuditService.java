package ru.sklad.app.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.data.domain.PageRequest;
import ru.sklad.app.entity.AuditLog;
import ru.sklad.app.repository.AuditLogRepository;
import java.time.*;
import java.util.*;

@Service
public class AuditService {
    private final AuditLogRepository repository;
    public AuditService(AuditLogRepository repository) { this.repository = repository; }
    public static String username() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? "system" : auth.getName();
    }
    @Transactional
    public void logAction(String action, String entityType, String entityName, String details) {
        AuditLog log = new AuditLog(); log.setUsername(username()); log.setAction(action);
        log.setEntityType(entityType); log.setEntityName(entityName); log.setDetails(details);
        repository.save(log);
    }
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public List<AuditLog> search(LocalDate from, LocalDate to, String username, int page) {
        if (from == null || to == null || from.isAfter(to) || to.getYear() > 9998 || from.getYear() < 1)
            throw new IllegalArgumentException("Укажите корректный период");
        return repository.search(from.atStartOfDay(), to.plusDays(1).atStartOfDay(),
                username == null ? "" : username.trim(), PageRequest.of(Math.max(0, page), 100));
    }
}
