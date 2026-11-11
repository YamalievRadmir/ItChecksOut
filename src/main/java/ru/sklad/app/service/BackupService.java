package ru.sklad.app.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.scheduling.annotation.Scheduled;
import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.regex.Pattern;
import java.util.zip.ZipInputStream;

@Service
public class BackupService {
    private static final Logger log = LoggerFactory.getLogger(BackupService.class);
    private static final Pattern BACKUP_NAME = Pattern.compile("backup_\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}(?:_\\d+)?\\.zip");
    private static final DateTimeFormatter NAME_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm");
    private final Path directory = Paths.get("backups");
    private final DataSource dataSource;
    private final AuditService audit;
    private final ErrorLogService errors;

    public BackupService(DataSource dataSource, AuditService audit, ErrorLogService errors) {
        this.dataSource = dataSource; this.audit = audit; this.errors = errors;
    }

    @PostConstruct
    public void initializeDirectory() {
        try {
            Files.createDirectories(directory);
            log.info("Каталог резервных копий: {}. Ежедневный запуск в 02:00; backup.zone либо часовой пояс JVM ({})",
                    directory.toAbsolutePath().normalize(), ZoneId.systemDefault());
        } catch (IOException exception) {
            throw new IllegalStateException("Не удалось создать каталог backups. Проверьте рабочую директорию и права записи", exception);
        }
    }

    @Scheduled(cron = "0 0 2 * * *", zone = "${backup.zone:}")
    public void scheduledBackup() {
        try { createBackup(); }
        catch (IllegalStateException exception) { log.error("Ежедневный бэкап не создан; следующий запуск по расписанию", exception); }
    }

    @PreAuthorize("hasRole('ADMIN')")
    public Path createManualBackup() { return createBackup(); }

    // Serialize scheduled and manual requests so that archives cannot overwrite one another.
    private synchronized Path createBackup() {
        Path temporary = null;
        try {
            Files.createDirectories(directory);
            String base = "backup_" + LocalDateTime.now().format(NAME_DATE);
            Path target = directory.resolve(base + ".zip");
            for (int suffix = 2; Files.exists(target); suffix++) target = directory.resolve(base + "_" + suffix + ".zip");
            temporary = Files.createTempFile(directory, ".backup-", ".zip.part");
            // A live H2 BACKUP is consistent; copying the .mv.db file directly is unsafe.
            try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
                if (!"H2".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName()))
                    throw new IllegalStateException("Этот механизм резервного копирования поддерживает только H2");
                String sqlPath = temporary.toString().replace('\\', '/').replace("'", "''");
                statement.execute("BACKUP TO '" + sqlPath + "'");
            }
            verifyArchive(temporary);
            try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException exception) { Files.move(temporary, target); }
            temporary = null;
            log.info("Бэкап создан: {} ({} байт)", target.toAbsolutePath().normalize(), Files.size(target));
            try { audit.logAction("СОЗДАНИЕ", "Резервная копия", target.getFileName().toString(), "Резервная копия H2 создана и проверена"); }
            catch (Exception exception) { report("Архив создан, но запись аудита не сохранена", exception); }
            cleanOldBackups(target);
            return target;
        } catch (Exception exception) {
            report("Не удалось создать резервную копию H2", exception);
            throw new IllegalStateException("Не удалось создать бэкап. Проверьте журнал ошибок и права записи в backups", exception);
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); }
                catch (IOException exception) { log.warn("Не удалось удалить незавершенный архив {}", temporary.getFileName(), exception); }
            }
        }
    }

    private void verifyArchive(Path file) throws IOException {
        boolean databaseFound = false;
        try (var zip = new ZipInputStream(Files.newInputStream(file))) {
            var entry = zip.getNextEntry();
            byte[] buffer = new byte[8192];
            while (entry != null) {
                boolean nonEmpty = false;
                int read;
                while ((read = zip.read(buffer)) != -1) if (read > 0) nonEmpty = true;
                if (!entry.isDirectory() && entry.getName().endsWith(".mv.db") && nonEmpty) databaseFound = true;
                zip.closeEntry(); entry = zip.getNextEntry();
            }
        }
        if (!databaseFound) throw new IOException("Архив не содержит непустой файл базы H2");
    }

    private void cleanOldBackups(Path newest) {
        Instant cutoff = Instant.now().minus(30, ChronoUnit.DAYS);
        Path root = directory.toAbsolutePath().normalize();
        try (var files = Files.newDirectoryStream(directory, "backup_*.zip")) {
            for (Path file : files) {
                if (file.equals(newest) || !BACKUP_NAME.matcher(file.getFileName().toString()).matches()
                        || !file.toAbsolutePath().normalize().getParent().equals(root)
                        || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) continue;
                try {
                    if (Files.getLastModifiedTime(file, LinkOption.NOFOLLOW_LINKS).toInstant().isBefore(cutoff)) {
                        Files.delete(file); log.info("Удален бэкап старше 30 дней: {}", file.getFileName());
                    }
                } catch (IOException exception) { report("Не удалось удалить старый бэкап " + file.getFileName(), exception); }
            }
        } catch (IOException exception) { report("Не удалось очистить старые резервные копии", exception); }
    }

    private void report(String message, Exception exception) {
        log.error(message, exception);
        try { errors.record(exception); }
        catch (Exception loggingFailure) { log.error("Не удалось записать ошибку бэкапа в журнал", loggingFailure); }
    }
}
