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
    private final ru.sklad.app.util.SecurityUtils security;
    public AuditService(AuditLogRepository repository, ru.sklad.app.util.SecurityUtils security) { this.repository = repository; this.security = security; }
    public static String username() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? "system" : auth.getName();
    }
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.beans.factory.ObjectProvider<AuditService> self;
    public void safeLogAction(String action, String entityType, String entityName, String details) {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()
                && org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override public void afterCommit() { writeSafely(action, entityType, entityName, details); }
                    });
        } else { writeSafely(action, entityType, entityName, details); }
    }
    private void writeSafely(String action, String entityType, String entityName, String details) {
        try { self.getObject().logAction(action, entityType, entityName, details); }
        catch (Exception failure) {

            org.slf4j.LoggerFactory.getLogger(AuditService.class).error("Не удалось сохранить аудит", failure);
        }
    }
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void logAction(String action, String entityType, String entityName, String details) {
        AuditLog log = new AuditLog(); log.setUsername(username()); log.setAction(action);
        log.setEntityType(entityType); log.setEntityName(entityName); log.setDetails(details);
        log.setShop(logShop());
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
    private ru.sklad.app.entity.Shop logShop() {
        try { return security.getCurrentShop(); }
        catch (org.springframework.security.access.AccessDeniedException unavailable) { return null; }
    }
}