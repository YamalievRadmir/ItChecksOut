package ru.sklad.app.config;

import org.slf4j.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.notification.Notification;
import ru.sklad.app.service.ErrorLogService;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {
    public static final String MESSAGE = "Произошла ошибка, администратор уведомлен";
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final ErrorLogService errors;
    public GlobalExceptionHandler(ErrorLogService errors) { this.errors = errors; }
    public void report(Throwable exception) {

        log.error("Ошибка", exception);
        if (isQuietFailure(exception)) return;
        try { errors.record(exception); }
        catch (Exception loggingFailure) {

            log.error("Не удалось сохранить журнал ошибок", loggingFailure);
        }
    }
    public static boolean isQuietFailure(Throwable exception) {
        var seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Throwable, Boolean>());
        for (Throwable cause = exception; cause != null && seen.add(cause); cause = cause.getCause()) {
            if (cause instanceof org.springframework.security.access.AccessDeniedException
                    || cause instanceof org.springframework.web.client.ResourceAccessException) return true;
        }
        return false;
    }
    public static boolean isAiFailure(Throwable exception) {
        var seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Throwable, Boolean>());
        for (Throwable cause = exception; cause != null && seen.add(cause); cause = cause.getCause()) {
            if (cause instanceof org.springframework.web.client.ResourceAccessException
                    || cause instanceof ru.sklad.app.service.AiService.AiUnavailableException) return true;
            for (StackTraceElement frame : cause.getStackTrace())
                if (frame.getClassName().equals(ru.sklad.app.service.AiService.class.getName())) return true;
        }
        return false;
    }
    public void notifyVaadin(Throwable exception) {
        report(exception);
        if (!isQuietFailure(exception) && !isAiFailure(exception) && UI.getCurrent() != null) Notification.show(MESSAGE, 7000, Notification.Position.MIDDLE);
    }
    @ExceptionHandler(Exception.class)
    @ResponseBody
    public ResponseEntity<Map<String, String>> handle(Exception exception) {
        report(exception);
        HttpStatusCode status = exception instanceof org.springframework.web.ErrorResponse response
                ? response.getStatusCode() : exception instanceof org.springframework.security.access.AccessDeniedException
                ? HttpStatus.FORBIDDEN : HttpStatus.INTERNAL_SERVER_ERROR;
        if (!isQuietFailure(exception) && !isAiFailure(exception) && UI.getCurrent() != null) Notification.show(MESSAGE);
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(Map.of("message", MESSAGE));
    }
}
