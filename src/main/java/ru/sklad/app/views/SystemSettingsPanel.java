package ru.sklad.app.views;

import ru.sklad.app.util.GridColumns;

import com.vaadin.flow.component.button.*;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.*;
import com.vaadin.flow.component.textfield.*;
import ru.sklad.app.entity.ErrorLog;
import ru.sklad.app.service.*;
import java.time.format.DateTimeFormatter;

public class SystemSettingsPanel extends VerticalLayout {
    private int page;
    public SystemSettingsPanel(ErrorLogService errors, AccountAdminService accounts, BackupService backups) {
        setWidthFull(); setPadding(false);
        Grid<AccountAdminService.Account> users = ru.sklad.app.util.GridColumns.compact(new Grid<>());
        users.addColumn(AccountAdminService.Account::username).setHeader("Пользователь");
        users.addColumn(AccountAdminService.Account::roles).setHeader("Роли");
        users.addColumn(a -> a.enabled() ? "Активен" : "Отключен").setHeader("Статус");
        users.addComponentColumn(account -> new Button("Сбросить пароль", event -> {
            Dialog dialog = new Dialog(); dialog.setHeaderTitle("Новый пароль: " + account.username());
            PasswordField password = new PasswordField("Новый пароль"); password.setRequiredIndicatorVisible(true);
            password.setHelperText("До 72 байт UTF-8. Пароль не будет записан в журнал.");
            dialog.add(password);
            Button save = new Button("Сохранить пароль", e -> {
                try { accounts.resetPassword(account.username(), password.getValue()); password.clear(); dialog.close(); Notification.show("Пароль сброшен"); }
                catch (IllegalArgumentException exception) { password.setInvalid(true); password.setErrorMessage(exception.getMessage()); }
            });
            save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            dialog.getFooter().add(new Button("Отмена", e -> { password.clear(); dialog.close(); }), save); dialog.open();
        })).setHeader("Действие");
        GridColumns.configure(users, "Пользователь", "Роли");
        users.setItems(accounts.list()); users.setWidthFull(); users.setHeight("240px");
        Grid<ErrorLog> grid = ru.sklad.app.util.GridColumns.compact(new Grid<>(ErrorLog.class, false));
        grid.addColumn(e -> e.getTimestamp().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"))).setHeader("Время");
        grid.addColumn(ErrorLog::getUsername).setHeader("Пользователь");
        grid.addColumn(ErrorLog::getMessage).setHeader("Текст ошибки").setFlexGrow(3);
        grid.addComponentColumn(error -> new Button("Подробности", event -> {
            Dialog dialog = new Dialog(); dialog.setHeaderTitle("Ошибка №" + error.getId()); dialog.setWidth("900px"); dialog.setMaxWidth("95vw");
            TextArea trace = new TextArea("Stack trace"); trace.setReadOnly(true); trace.setValue(error.getStackTrace()); trace.setWidthFull(); trace.setHeight("450px");
            dialog.add(trace); dialog.getFooter().add(new Button("Закрыть", e -> dialog.close())); dialog.open();
        }));
        GridColumns.configure(grid, "Текст ошибки");
        grid.setWidthFull(); grid.setHeight("360px");
        Span status = new Span(); Button previous = new Button("Назад"); Button next = new Button("Далее");
        Runnable refresh = () -> {
            var rows = errors.list(page); grid.setItems(rows); previous.setEnabled(page > 0); next.setEnabled(rows.size() == 100);
            status.setText("Страница " + (page + 1) + ", записей: " + rows.size());
        };
        previous.addClickListener(e -> { if (page > 0) page--; refresh.run(); }); next.addClickListener(e -> { page++; refresh.run(); });
        Button backup = new Button("Создать бэкап сейчас");
        backup.setVisible(false);
        backup.addThemeVariants(ButtonVariant.LUMO_PRIMARY); backup.setDisableOnClick(true);
        backup.addClickListener(event -> {
            try {
                var file = backups.createManualBackup();
                Notification.show("Бэкап создан: " + file, 7000, Notification.Position.MIDDLE);
            } catch (IllegalStateException exception) { Notification.show(exception.getMessage(), 7000, Notification.Position.MIDDLE); }
            finally { backup.setEnabled(true); }
        });
        add(new H2("Резервное копирование"),
                new Paragraph("Ежедневно в 02:00 по часовому поясу сервера (или backup.zone). Архивы хранятся в backups/ рабочей директории; срок хранения — 30 дней."), backup,
                new H2("Управление пользователями"), users,
                new Paragraph("Учетные записи хранятся в памяти: после перезапуска пароли вернутся к исходным."),
                new H2("Журнал ошибок"), new Button("Обновить журнал", e -> refresh.run()), grid,
                new HorizontalLayout(previous, status, next)); refresh.run();
    }
}
