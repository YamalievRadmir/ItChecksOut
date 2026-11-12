package ru.sklad.app.views;

import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.component.orderedlayout.*;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.button.*;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.textfield.*;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.tabs.Tab;
import jakarta.annotation.security.RolesAllowed;
import ru.sklad.app.entity.*;
import ru.sklad.app.service.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Route(value = "super-admin", layout = MainLayout.class)
@PageTitle("Управление магазинами")
@RolesAllowed("SUPER_ADMIN")
public class SuperAdminView extends VerticalLayout {
    private final ShopAdminService shops;
    private final SuperAdminService admin;
    private final Grid<Shop> shopGrid = ru.sklad.app.util.GridColumns.compact(new Grid<>(Shop.class, false));
    private final Grid<User> userGrid = ru.sklad.app.util.GridColumns.compact(new Grid<>(User.class, false));
    private Map<Long, Long> userCounts = Map.of();
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    public SuperAdminView(ShopAdminService shops, SuperAdminService admin) {
        this.shops = shops; this.admin = admin;
        setSizeFull(); setPadding(true); setSpacing(true);
        getStyle().set("box-sizing", "border-box").set("max-width", "1440px").set("margin", "0 auto");
        H1 title = new H1("Управление магазинами");
        title.getStyle().set("font-size", "var(--lumo-font-size-xxl)").set("margin", "0");
        TabSheet tabs = new TabSheet(); tabs.setSizeFull(); tabs.getStyle().set("min-height", "0");
        shopGrid.addColumn(Shop::getName).setHeader("Название").setFlexGrow(2).setSortable(true);
        shopGrid.addColumn(shop -> shop.isActive() ? "Активен" : "Неактивен").setHeader("Статус");
        shopGrid.addColumn(shop -> userCounts.getOrDefault(shop.getId(), 0L)).setHeader("Количество пользователей");
        Button create = new Button("+ Создать магазин", event -> createShop());
        create.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        VerticalLayout shopTab = panel(); shopTab.add(create, shopGrid); shopTab.setFlexGrow(0, shopGrid);
        userGrid.addColumn(User::getUsername).setHeader("Логин").setSortable(true);
        userGrid.addColumn(User::getRole).setHeader("Роль").setSortable(true);
        userGrid.addColumn(user -> shopName(user.getShop())).setHeader("Магазин").setFlexGrow(2);
        userGrid.addComponentColumn(user -> new Button("Редактировать", event -> editUser(user))).setHeader("Действия");
        userGrid.addColumn(user -> user.isEnabled() ? "Активен" : "Заблокирован").setHeader("Статус");
        userGrid.addComponentColumn(user -> new Button("Сбросить пароль", event -> {
            String generated = admin.resetPassword(user.getId());
            Dialog result = dialog("Новый пароль: " + user.getUsername());
            TextField value = new TextField("Сохраните пароль"); value.setValue(generated); value.setReadOnly(true); value.setWidthFull();
            result.add(value); result.getFooter().add(new Button("Закрыть", click -> result.close()));
            result.addOpenedChangeListener(change -> { if (!change.isOpened()) value.clear(); }); result.open();
        }));
        userGrid.addComponentColumn(user -> {
            Button block = new Button("Заблокировать", event -> {
                try { admin.blockUser(user.getId()); refresh(); }
                catch (IllegalArgumentException ex) { Notification.show(ex.getMessage()); }
            });
            block.addThemeVariants(ButtonVariant.LUMO_ERROR); block.setEnabled(user.isEnabled()); return block;
        });        userGrid.addItemDoubleClickListener(event -> editUser(event.getItem()));
        VerticalLayout userTab = panel();
        userTab.add(new Span("Дважды нажмите на пользователя, чтобы изменить логин или пароль."), userGrid); userTab.setFlexGrow(0, userGrid);
        VerticalLayout logsContent = panel();
        VerticalLayout errors = errorLogs();
        VerticalLayout audit = auditLogs();
        errors.setHeight("50%"); audit.setHeight("50%");
        errors.addComponentAsFirst(new H2("Журнал ошибок"));
        audit.addComponentAsFirst(new H2("Журнал аудита"));
        logsContent.add(errors, audit);
        logsContent.expand(errors, audit);
        Tab shopsTab = tabs.add("Магазины", shopTab);
        Tab usersTab = tabs.add("Пользователи", userTab);
        Tab logsTab = tabs.add("Системные логи", logsContent);
        tabs.setSelectedTab(shopsTab);
        shopGrid.setWidthFull(); shopGrid.setHeight("65vh"); userGrid.setWidthFull(); userGrid.setHeight("65vh");
        add(title, tabs); expand(tabs); refresh();
    }
    private VerticalLayout panel() {
        VerticalLayout layout = new VerticalLayout(); layout.setSizeFull(); layout.setPadding(true);
        layout.getStyle().set("min-height", "0"); return layout;
    }
    private static String shopName(Shop shop) { return shop == null ? "Система" : shop.getName(); }
    private void refresh() {
        List<User> users = admin.users();
        Map<Long, Long> counts = new HashMap<>();
        for (User user : users) if (user.getShop() != null) counts.merge(user.getShop().getId(), 1L, Long::sum);
        userCounts = counts; shopGrid.setItems(shops.list()); userGrid.setItems(users);
    }
    private Dialog dialog(String title) {
        Dialog dialog = new Dialog(); dialog.setHeaderTitle(title); dialog.setWidth("440px"); dialog.setMaxWidth("95vw");
        return dialog;
    }
    private PasswordField password(String label) {
        PasswordField field = new PasswordField(label); field.setRevealButtonVisible(true); field.setWidthFull();
        field.setAutocomplete(Autocomplete.NEW_PASSWORD); return field;
    }
    private void createShop() {
        Dialog dialog = dialog("Создать магазин");
        TextField name = new TextField("Название магазина"); name.setMaxLength(255); name.setWidthFull();
        TextField username = new TextField("Логин администратора"); username.setMaxLength(50); username.setWidthFull();
        PasswordField password = password("Пароль администратора");
        name.setRequiredIndicatorVisible(true); username.setRequiredIndicatorVisible(true); password.setRequiredIndicatorVisible(true);
        VerticalLayout form = new VerticalLayout(name, username, password); form.setPadding(false); dialog.add(form);
        Button save = new Button("Создать", event -> {
            try { shops.create(name.getValue(), username.getValue(), password.getValue()); password.clear(); refresh(); dialog.close(); }
            catch (IllegalArgumentException ex) { Notification.show(ex.getMessage()); }
            catch (org.springframework.dao.DataIntegrityViolationException ex) { Notification.show("Не удалось сохранить: проверьте уникальность логина"); }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(new Button("Отмена", event -> dialog.close()), save);
        dialog.addOpenedChangeListener(event -> { if (!event.isOpened()) password.clear(); }); dialog.open();
    }
    private void editUser(User user) {
        Dialog dialog = dialog("Редактировать пользователя");
        TextField username = new TextField("Логин"); username.setValue(user.getUsername()); username.setMaxLength(50); username.setWidthFull();
        PasswordField password = password("Новый пароль");
        password.setHelperText("Оставьте пустым, чтобы сохранить текущий пароль.");
        VerticalLayout form = new VerticalLayout(username, password); form.setPadding(false); dialog.add(form);
        Button save = new Button("Сохранить", event -> {
            try { admin.updateUser(user.getId(), username.getValue(), password.getValue()); password.clear(); refresh(); dialog.close(); }
            catch (IllegalArgumentException ex) { Notification.show(ex.getMessage()); }
            catch (org.springframework.dao.DataIntegrityViolationException ex) { Notification.show("Логин уже занят"); }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(new Button("Отмена", event -> dialog.close()), save);
        dialog.addOpenedChangeListener(event -> { if (!event.isOpened()) password.clear(); }); dialog.open();
    }
    private VerticalLayout errorLogs() {
        Grid<ErrorLog> grid = ru.sklad.app.util.GridColumns.compact(new Grid<>(ErrorLog.class, false)); grid.setWidthFull(); grid.setHeight("65vh");
        grid.addColumn(log -> DATE.format(log.getTimestamp())).setHeader("Время").setAutoWidth(true);
        grid.addColumn(log -> shopName(log.getShop())).setHeader("Магазин");
        grid.addColumn(ErrorLog::getUsername).setHeader("Пользователь");
        grid.addColumn(ErrorLog::getMessage).setHeader("Ошибка").setFlexGrow(3);
        grid.addComponentColumn(log -> new Button("Подробнее", event -> {
            Dialog dialog = dialog("Ошибка №" + log.getId()); dialog.setWidth("900px");
            TextArea trace = new TextArea("Подробности"); trace.setValue(Objects.toString(log.getStackTrace(), ""));
            trace.setReadOnly(true); trace.setWidthFull(); trace.setHeight("400px"); dialog.add(trace);
            dialog.getFooter().add(new Button("Закрыть", click -> dialog.close())); dialog.open();
        }));
        return logPanel(grid, page -> admin.errors(page));
    }
    private VerticalLayout auditLogs() {
        Grid<AuditLog> grid = ru.sklad.app.util.GridColumns.compact(new Grid<>(AuditLog.class, false)); grid.setWidthFull(); grid.setHeight("65vh");
        grid.addColumn(log -> DATE.format(log.getTimestamp())).setHeader("Время").setAutoWidth(true);
        grid.addColumn(log -> shopName(log.getShop())).setHeader("Магазин");
        grid.addColumn(AuditLog::getUsername).setHeader("Пользователь");
        grid.addColumn(AuditLog::getAction).setHeader("Действие");
        grid.addColumn(AuditLog::getEntityType).setHeader("Тип");
        grid.addColumn(AuditLog::getEntityName).setHeader("Объект");
        grid.addColumn(AuditLog::getDetails).setHeader("Подробности").setFlexGrow(3);
        return logPanel(grid, page -> admin.audit(page));
    }
    private <T> VerticalLayout logPanel(Grid<T> grid, java.util.function.IntFunction<List<T>> loader) {
        int[] page = {0}; Span status = new Span(); Button previous = new Button("Назад"); Button next = new Button("Далее");
        Runnable refresh = () -> {
            List<T> rows = loader.apply(page[0]); grid.setItems(rows); previous.setEnabled(page[0] > 0); next.setEnabled(rows.size() == 100);
            status.setText("Страница " + (page[0] + 1));
        };
        previous.addClickListener(event -> { if (page[0] > 0) page[0]--; refresh.run(); });
        next.addClickListener(event -> { page[0]++; refresh.run(); });
        Button reload = new Button("Обновить", event -> { page[0] = 0; refresh.run(); });
        HorizontalLayout controls = new HorizontalLayout(previous, status, next, reload); controls.setAlignItems(Alignment.CENTER);
        VerticalLayout layout = panel(); layout.add(controls, grid); layout.setFlexGrow(0, grid); refresh.run(); return layout;
    }
}