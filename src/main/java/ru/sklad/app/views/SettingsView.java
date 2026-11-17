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
public class SettingsView extends VerticalLayout implements com.vaadin.flow.router.BeforeEnterObserver {
    private final VerticalLayout contentLayout = new VerticalLayout();
    private final Runnable initializeView;
    public SettingsView(ProfileService profileService, AuthenticationContext authenticationContext, ru.sklad.app.service.PermissionService permissions,
                        ru.sklad.app.service.ErrorLogService errors, ru.sklad.app.service.AccountAdminService accounts,
                        ru.sklad.app.service.AuditService audit, ru.sklad.app.service.BackupService backups,
                        ru.sklad.app.service.SiteParserService parser,
                        @org.springframework.beans.factory.annotation.Qualifier("aiExecutor") org.springframework.core.task.AsyncTaskExecutor executor,
                        ru.sklad.app.config.GlobalExceptionHandler exceptionHandler, ru.sklad.app.service.ShopSellerService sellers, ru.sklad.app.service.ImpersonationService impersonation) {
        setSizeFull();
        contentLayout.setPadding(false); contentLayout.setWidthFull();
        add(contentLayout);
        initializeView = () -> {
        setSizeFull();
        getStyle().set("overflow", "auto");
        contentLayout.add(new H2("Настройки"));
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
            tabs.add("Пользователи магазина", new ShopUsersPanel(sellers, impersonation));
            tabs.add("База знаний", new KnowledgeImportPanel(parser, executor, exceptionHandler));
            if (ru.sklad.app.util.SecurityUtils.currentShop() == null) {
                tabs.add("Настройки доступа", new com.vaadin.flow.component.html.Span("Ошибка: магазин не найден"));
            } else {
            var initialSettings = permissions.loadSellerAccessSettings();
            VerticalLayout access = new VerticalLayout(); access.setWidthFull(); access.setSpacing(false); access.getStyle().set("gap", "8px");
            VerticalLayout sections = new VerticalLayout(new H2("Разрешенные разделы"));
            VerticalLayout actionsPanel = new VerticalLayout(new H2("Права на действия"));
            for (VerticalLayout column : java.util.List.of(sections, actionsPanel)) {
                column.setSpacing(false); column.getStyle().set("gap", "4px"); column.getChildren().forEach(heading -> heading.getStyle().set("margin", "0 0 8px").set("font-size", "var(--lumo-font-size-l)")); column.setPadding(false); column.setWidthFull(); column.getStyle().set("min-width", "0");
            }
            com.vaadin.flow.component.html.Div columns = new com.vaadin.flow.component.html.Div(sections, actionsPanel);
            columns.setWidthFull();
            columns.getStyle().set("display", "grid").set("grid-template-columns", "repeat(auto-fit, minmax(min(100%, 320px), 1fr))").set("gap", "16px").set("align-items", "start"); access.add(columns);
            var levels = new java.util.LinkedHashMap<String, com.vaadin.flow.component.combobox.ComboBox<ru.sklad.app.entity.AccessLevel>>();
            java.util.List.of("products", "supplies", "deliveries", "notes", "animals", "sales").forEach(route -> {
                var field = new com.vaadin.flow.component.combobox.ComboBox<ru.sklad.app.entity.AccessLevel>(ru.sklad.app.service.PermissionService.VIEWS.get(route));
                field.setItems(ru.sklad.app.entity.AccessLevel.values());
                field.setItemLabelGenerator(level -> switch(level) { case NONE -> "Нет доступа"; case VIEW -> "Только просмотр"; case EDIT -> "Полный доступ"; });
                field.setValue(permissions.sellerLevel(initialSettings, route)); field.setWidthFull();
                field.getElement().getThemeList().add("small");
                field.addValueChangeListener(event -> {
                    if (event.getValue() != null && field != null) field.setTooltipText(accessTooltip(route, event.getValue()));
                });
                field.setTooltipText(accessTooltip(route, field.getValue()));
                levels.put(route, field); sections.add(field);
            });
            var checks = new java.util.LinkedHashMap<String, com.vaadin.flow.component.checkbox.Checkbox>();
            java.util.List.of("inventory", "reports", "sales-history", "employees").forEach(route -> {
                var field = new com.vaadin.flow.component.checkbox.Checkbox(ru.sklad.app.service.PermissionService.VIEWS.get(route));
                field.setValue(permissions.sellerLevel(initialSettings, route) != ru.sklad.app.entity.AccessLevel.NONE);
                field.getElement().getThemeList().add("small");
                field.addValueChangeListener(event -> field.setTooltipText(actionTooltip(route, event.getValue())));
                field.setTooltipText(actionTooltip(route, field.getValue()));
                checks.put(route, field); actionsPanel.add(field);
            });
            var discount = new com.vaadin.flow.component.checkbox.Checkbox("Скидки в кассе");
            discount.setValue(Boolean.TRUE.equals(initialSettings.getCanGiveDiscount()));
            discount.getElement().getThemeList().add("small"); discount.addValueChangeListener(event -> discount.setTooltipText(actionTooltip("discount", event.getValue()))); discount.setTooltipText(actionTooltip("discount", discount.getValue())); actionsPanel.add(discount);
            Runnable reloadAccess = () -> {
                var settings = permissions.loadSellerAccessSettings();
                levels.forEach((route, field) -> field.setValue(permissions.sellerLevel(settings, route)));
                checks.forEach((route, field) -> field.setValue(permissions.sellerLevel(settings, route) != ru.sklad.app.entity.AccessLevel.NONE));
                discount.setValue(Boolean.TRUE.equals(settings.getCanGiveDiscount()));
            };
            access.addAttachListener(event -> reloadAccess.run());
            tabs.addSelectedChangeListener(event -> {
                if (tabs.getSelectedTab() != null && "Настройки доступа".equals(tabs.getSelectedTab().getLabel())) reloadAccess.run();
            });

            sections.getChildren().forEach(component -> component.getStyle().set("flex-shrink", "0"));
            Button save = new Button("Сохранить", event -> {
                var values = new java.util.LinkedHashMap<String, ru.sklad.app.entity.AccessLevel>(); levels.forEach((route, field) -> values.put(route, field.getValue()));
                var flags = new java.util.LinkedHashSet<String>(); checks.forEach((route, field) -> { if(field.getValue()) flags.add(route); });
                permissions.saveSellerAccess(values, flags, discount.getValue()); Notification.show("Разрешения SELLER сохранены");
            });
            save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            access.add(save);
            access.addClassName("app-card");
        tabs.add("Настройки доступа", access);
            }
        tabs.add("Система", new SystemSettingsPanel(errors, accounts, backups));
        tabs.add("Аудит", new AuditSettingsPanel(audit));
        }
        tabs.setWidthFull();
        contentLayout.add(tabs);
        };
    }
    @Override
    public void beforeEnter(com.vaadin.flow.router.BeforeEnterEvent event) {
        initView();
    }
    private void initView() {
        contentLayout.removeAll();
        initializeView.run();
    }
    private static String accessTooltip(String route, ru.sklad.app.entity.AccessLevel level) {
        if (level == null || level == ru.sklad.app.entity.AccessLevel.NONE) {
            return switch (route) {
                case "products" -> "Продавец не видит вкладку 'Товары' в меню.";
                case "sales" -> "Нет доступа к кассе";
                default -> "Нет доступа";
            };
        }
        boolean view = level == ru.sklad.app.entity.AccessLevel.VIEW;
        return switch (route) {
            case "products" -> view
                    ? "Продавец видит таблицу товаров и остатки. Закупочная цена скрыта. Редактирование запрещено."
                    : "Продавец может создавать, редактировать и удалять товары, а также видеть закупочную цену.";
            case "supplies" -> view ? "Видит историю приемок" : "Может принимать товар, цена закупа только для чтения";
            case "deliveries" -> view ? "Только просмотр списка доставок" : "Создание заявок и смена статуса";
            case "notes" -> view ? "Чтение заметок" : "Создание долгов и инструкций";
            case "animals" -> view ? "Только чтение базы знаний" : "Редактирование статей";
            case "sales" -> view ? "Только просмотр, продавать нельзя" : "Может пробивать чеки";
            default -> throw new IllegalArgumentException("Неизвестный раздел: " + route);
        };
    }

    private static String actionTooltip(String route, boolean enabled) {
        if ("discount".equals(route)) return enabled
                ? "Продавец может применять скидку в кассе." : "Блок скидки в кассе скрыт.";
        if ("employees".equals(route)) return enabled
                ? "Продавец видит список сотрудников и график, но не видит зарплаты." : "Вкладка 'Сотрудники' скрыта.";
        if (!enabled) return "Доступ запрещен (вкладка/функция скрыта).";
        return "Доступ разрешен. " + switch (route) {
            case "inventory" -> "Продавец может открыть вкладку инвентаризации.";
            case "reports" -> "Продавец видит отчеты магазина.";
            case "sales-history" -> "Продавец может просматривать историю чеков.";
            default -> throw new IllegalArgumentException("Неизвестная функция: " + route);
        };
    }}
