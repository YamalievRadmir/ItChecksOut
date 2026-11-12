package ru.sklad.app.views;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.security.core.context.SecurityContextHolder;
import com.vaadin.flow.spring.security.AuthenticationContext;
import org.springframework.security.authentication.BadCredentialsException;
import ru.sklad.app.service.ProfileService;
import ru.sklad.app.util.RoleAccess;

@Route(value = "settings", layout = MainLayout.class)
@PageTitle("Настройки — Всё сходится!")
@RolesAllowed({"ADMIN", "SELLER"})
public class SettingsView extends VerticalLayout {
    public SettingsView(ProfileService profileService, AuthenticationContext authenticationContext, ru.sklad.app.service.PermissionService permissions,
                        ru.sklad.app.service.ErrorLogService errors, ru.sklad.app.service.AccountAdminService accounts,
                        ru.sklad.app.service.AuditService audit, ru.sklad.app.service.BackupService backups,
                        ru.sklad.app.service.SiteParserService parser,
                        @org.springframework.beans.factory.annotation.Qualifier("aiExecutor") org.springframework.core.task.AsyncTaskExecutor executor,
                        ru.sklad.app.config.GlobalExceptionHandler exceptionHandler, ru.sklad.app.service.ShopSellerService sellers) {
        setSizeFull();
        getStyle().set("overflow", "auto");
        add(new H2("Настройки"));
        TextField login = new TextField("Пользователь");
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        login.setValue(authentication == null ? "" : authentication.getName());
        login.setReadOnly(true);
        PasswordField oldPassword = new PasswordField("Старый пароль");
        PasswordField newPassword = new PasswordField("Новый пароль");
        oldPassword.setRequiredIndicatorVisible(true);
        newPassword.setRequiredIndicatorVisible(true);
        oldPassword.setErrorMessage("Введите старый пароль");
        newPassword.setErrorMessage("Введите новый пароль");
        FormLayout form = new FormLayout(login, oldPassword, newPassword);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));
        form.setMaxWidth("480px");
        Button change = new Button("Изменить пароль", event -> {
            oldPassword.setInvalid(oldPassword.isEmpty());
            newPassword.setInvalid(newPassword.isEmpty());
            if (oldPassword.isEmpty() || newPassword.isEmpty()) return;
            try {
                profileService.changePassword(oldPassword.getValue(), newPassword.getValue());
                oldPassword.clear();
                newPassword.clear();
                oldPassword.setInvalid(false);
                newPassword.setInvalid(false);
                Notification.show("Пароль изменен");
            } catch (BadCredentialsException exception) {
                oldPassword.setErrorMessage("Старый пароль указан неверно");
                oldPassword.setInvalid(true);
            } catch (IllegalArgumentException exception) {
                newPassword.setErrorMessage(exception.getMessage());
                newPassword.setInvalid(true);
            }
        });
        change.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button logout = new Button("Выйти из аккаунта", event -> authenticationContext.logout());
        VerticalLayout profile = new VerticalLayout(form, change, logout,
                new Paragraph("Учётные записи и пароли сохраняются в базе данных."));
        profile.addClassName("app-card");
        TabSheet tabs = new TabSheet();
        tabs.add("Профиль и Пароли", profile);
        if (RoleAccess.isAdmin()) {
            tabs.add("Пользователи магазина", new ShopUsersPanel(sellers));
            tabs.add("База знаний", new KnowledgeImportPanel(parser, executor, exceptionHandler));
            VerticalLayout access = new VerticalLayout(new H2("Разрешенные разделы для SELLER"));
            var selected = permissions.allowed("SELLER");
            var checkboxes = new java.util.LinkedHashMap<String, com.vaadin.flow.component.checkbox.Checkbox>();
            ru.sklad.app.service.PermissionService.VIEWS.forEach((route, label) -> {
                var checkbox = new com.vaadin.flow.component.checkbox.Checkbox(label);
                checkbox.setValue(selected.contains(route));
                checkboxes.put(route, checkbox);
                access.add(checkbox);
            });
            access.add(new H2("Права на действия (для SELLER)"));
            var selectedActions = permissions.sellerActions();
            var actionCheckboxes = new java.util.LinkedHashMap<String, com.vaadin.flow.component.checkbox.Checkbox>();
            ru.sklad.app.service.PermissionService.ACTIONS.forEach((action, label) -> {
                var checkbox = new com.vaadin.flow.component.checkbox.Checkbox(label);
                checkbox.setValue(selectedActions.contains(action));
                actionCheckboxes.put(action, checkbox);
                access.add(checkbox);
            });
            Button save = new Button("Сохранить", event -> {
                var routes = new java.util.LinkedHashSet<String>();
                checkboxes.forEach((route, checkbox) -> { if (checkbox.getValue()) routes.add(route); });
                var actions = new java.util.LinkedHashSet<String>();
                actionCheckboxes.forEach((action, checkbox) -> { if (checkbox.getValue()) actions.add(action); });
                permissions.saveSeller(routes, actions);
                Notification.show("Разрешения SELLER сохранены");
            });
            save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            access.add(save);
            access.addClassName("app-card");
        tabs.add("Настройки доступа", access);
        tabs.add("Система", new SystemSettingsPanel(errors, accounts, backups));
        tabs.add("Аудит", new AuditSettingsPanel(audit));
        }
        tabs.setWidthFull();
        add(tabs);
    }
}
