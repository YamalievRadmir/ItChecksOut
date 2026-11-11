package ru.sklad.app.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.PageRequest;
import ru.sklad.app.entity.ErrorLog;
import ru.sklad.app.repository.ErrorLogRepository;
import java.io.*;
import java.util.List;

@Service
public class ErrorLogService {
    private final ErrorLogRepository repository;
    public ErrorLogService(ErrorLogRepository repository) { this.repository = repository; }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Throwable exception) {
        var trace = new StringWriter(); exception.printStackTrace(new PrintWriter(trace));
        ErrorLog log = new ErrorLog(); log.setUsername(AuditService.username());
        log.setMessage(exception.getClass().getSimpleName() + ": " + java.util.Objects.toString(exception.getMessage(), ""));
        log.setStackTrace(trace.toString()); repository.saveAndFlush(log);
    }
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public List<ErrorLog> list(int page) {
        return repository.findAllByOrderByTimestampDescIdDesc(PageRequest.of(Math.max(0, page), 100));
    }
}
