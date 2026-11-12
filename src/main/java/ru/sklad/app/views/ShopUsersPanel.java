package ru.sklad.app.views;

import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.button.*;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.textfield.*;
import ru.sklad.app.entity.User;
import ru.sklad.app.service.ShopSellerService;

public class ShopUsersPanel extends VerticalLayout {
    private final ShopSellerService service;
    private final Grid<User> grid = ru.sklad.app.util.GridColumns.compact(new Grid<>(User.class, false));
    public ShopUsersPanel(ShopSellerService service) {
        this.service = service;
        setWidthFull(); setPadding(true);
        Button add = new Button("Добавить продавца", event -> edit(null));
        add.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        grid.addColumn(User::getUsername).setHeader("Логин").setSortable(true);
        grid.addColumn(User::getRole).setHeader("Роль");
        grid.addComponentColumn(user -> new Button("Редактировать", event -> edit(user))).setHeader("Действия");
        grid.addItemDoubleClickListener(event -> edit(event.getItem()));
        grid.setWidthFull(); grid.setHeight("420px");
        add(add, new Paragraph("Дважды нажмите на продавца для изменения логина или пароля."), grid);
        refresh();
    }
    private void refresh() { grid.setItems(service.list()); }
    private void edit(User user) {
        Dialog dialog = new Dialog(); dialog.setHeaderTitle(user == null ? "Добавить продавца" : "Редактировать продавца");
        dialog.setWidth("440px"); dialog.setMaxWidth("95vw");
        TextField login = new TextField("Логин"); login.setMaxLength(50); login.setRequiredIndicatorVisible(true); login.setWidthFull();
        if (user != null) login.setValue(user.getUsername());
        PasswordField password = new PasswordField(user == null ? "Пароль" : "Новый пароль");
        password.setWidthFull(); password.setRevealButtonVisible(true); password.setAutocomplete(Autocomplete.NEW_PASSWORD);
        password.setRequiredIndicatorVisible(user == null);
        if (user != null) password.setHelperText("Оставьте пустым, чтобы сохранить текущий пароль.");
        VerticalLayout form = new VerticalLayout(login, password); form.setPadding(false); dialog.add(form);
        Button save = new Button("Сохранить", event -> {
            try {
                service.save(user == null ? null : user.getId(), login.getValue(), password.getValue());
                password.clear(); refresh(); dialog.close(); Notification.show("Продавец сохранён");
            } catch (IllegalArgumentException ex) { error(ex.getMessage()); }
            catch (org.springframework.dao.DataIntegrityViolationException ex) { error("Логин уже занят"); }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(new Button("Отмена", event -> dialog.close()), save);
        dialog.addOpenedChangeListener(event -> { if (!event.isOpened()) password.clear(); });
        dialog.open();
    }
    private void error(String message) { Notification.show(message, 6000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_ERROR); }
}