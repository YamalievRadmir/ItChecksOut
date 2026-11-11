package ru.sklad.app.views;

import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import jakarta.annotation.security.PermitAll;
import org.springframework.security.core.context.SecurityContextHolder;
import com.vaadin.flow.component.contextmenu.ContextMenu;
import com.vaadin.flow.spring.security.AuthenticationContext;

@PermitAll
public class MainLayout extends AppLayout {
    private final com.vaadin.flow.component.html.Span deliveryBadge = new com.vaadin.flow.component.html.Span();
    private final ru.sklad.app.service.DeliveryService deliveries;
    private final ru.sklad.app.service.PermissionService permissions;

    public MainLayout(AuthenticationContext authenticationContext, ru.sklad.app.service.PermissionService permissions,
                      ru.sklad.app.service.DeliveryService deliveries,
                      ru.sklad.app.service.AiService ai,
                      @org.springframework.beans.factory.annotation.Qualifier("aiExecutor") org.springframework.core.task.AsyncTaskExecutor aiExecutor,
                      ru.sklad.app.config.GlobalExceptionHandler errors) {
        this.deliveries = deliveries;
        this.permissions = permissions;
        deliveryBadge.setVisible(false);
        deliveryBadge.getStyle().set("display", "inline-flex").set("align-items", "center")
                .set("justify-content", "center").set("background-color", "#b91c1c")
                .set("color", "white").set("border-radius", "999px").set("min-width", "1.5em")
                .set("height", "1.5em").set("padding", "0 0.3em").set("font-weight", "700")
                .set("box-sizing", "border-box");
        addClassName("main-layout");
        setPrimarySection(Section.DRAWER);

        H1 title = new H1("Всё сходится!");
        title.getStyle().set("font-size", "var(--lumo-font-size-l)")
                .set("margin", "0");
        DrawerToggle toggle = new DrawerToggle();
        toggle.setAriaLabel("Открыть меню");
        Button theme = new Button();
        theme.addClickListener(event -> theme.getUI().ifPresent(ui -> {
            boolean dark = !Boolean.TRUE.equals(ui.getSession().getAttribute("darkTheme"));
            ui.getSession().setAttribute("darkTheme", dark);
            applyTheme(ui, theme, dark);
        }));
        theme.addAttachListener(event -> applyTheme(event.getUI(), theme,
                Boolean.TRUE.equals(event.getUI().getSession().getAttribute("darkTheme"))));
        Button account = new Button(VaadinIcon.USER.create());
        account.setAriaLabel("Меню пользователя");
        var current = SecurityContextHolder.getContext().getAuthentication();
        account.setTooltipText(current == null ? "Аккаунт" : current.getName());
        ContextMenu accountMenu = new ContextMenu(account);
        accountMenu.setOpenOnClick(true);
        var schemeMenu = accountMenu.addItem("Цветовая схема").getSubMenu();
        var schemeItems = new java.util.LinkedHashMap<String, com.vaadin.flow.component.contextmenu.MenuItem>();
        String[][] schemes = {{"corporate", "Корпоративный", "#2563eb"}, {"nature", "Природа", "#2e7d32"},
                {"ocean", "Океан", "#1565c0"}, {"sunset", "Закат", "#e65100"}};
        for (String[] scheme : schemes) {
            var dot = new com.vaadin.flow.component.html.Span();
            dot.getStyle().set("background", scheme[2]).set("width", "12px").set("height", "12px")
                    .set("border-radius", "50%").set("display", "inline-block");
            dot.getElement().setAttribute("aria-hidden", "true");
            var label = new HorizontalLayout(dot, new com.vaadin.flow.component.html.Span(scheme[1]));
            label.setAlignItems(Alignment.CENTER); label.setSpacing(true);
            var item = schemeMenu.addItem(label, event -> account.getUI().ifPresent(ui -> {
                ui.getSession().setAttribute("colorScheme", scheme[0]);
                applyTheme(ui, theme, Boolean.TRUE.equals(ui.getSession().getAttribute("darkTheme")));
                schemeItems.forEach((key, menuItem) -> menuItem.setChecked(key.equals(scheme[0])));
            }));
            item.setCheckable(true);
            item.setChecked("corporate".equals(scheme[0]));
            schemeItems.put(scheme[0], item);
        }
        accountMenu.addOpenedChangeListener(event -> {
            if (event.isOpened()) account.getUI().ifPresent(ui -> {
                String selected = java.util.Objects.toString(ui.getSession().getAttribute("colorScheme"), "corporate");
                schemeItems.forEach((key, item) -> item.setChecked(key.equals(selected)));
            });
        });
        accountMenu.addItem("Выйти", event -> authenticationContext.logout());
        HorizontalLayout header = new HorizontalLayout(toggle, title, theme, account);
        header.setWidthFull();
        header.setAlignItems(Alignment.CENTER);
        header.setFlexGrow(1, title);
        header.addClassName("app-header");
        addToNavbar(header);
        AiChatDialog chat = new AiChatDialog(ai, aiExecutor, errors);
        Button chatButton = new Button(VaadinIcon.CHAT.create(), event -> chat.open());
        chatButton.setAriaLabel("Открыть чат с консультантом");
        chatButton.setTooltipText("Консультант по кормам");
        chatButton.addThemeVariants(com.vaadin.flow.component.button.ButtonVariant.LUMO_PRIMARY);
        chatButton.getStyle().set("position", "fixed").set("bottom", "80px").set("right", "20px")
                .set("z-index", "100").set("border-radius", "50%").set("width", "48px").set("height", "48px");
        addToNavbar(chatButton);

        SideNav navigation = new SideNav();
        navigation.setWidthFull();
        navigation.getElement().setAttribute("aria-label", "Основное меню");
        var icons = new java.util.HashMap<>(java.util.Map.of(
                "products", VaadinIcon.PACKAGE, "supplies", VaadinIcon.TRUCK,
                "sales", VaadinIcon.CART, "animals", VaadinIcon.BOOK, "deliveries", VaadinIcon.TRUCK,
                "reorder", VaadinIcon.CART_O, "sales-history", VaadinIcon.LIST,
                "reports", VaadinIcon.CHART, "employees", VaadinIcon.USERS, "settings", VaadinIcon.COG));
        icons.put("notes", VaadinIcon.NOTEBOOK);
        icons.put("inventory", VaadinIcon.CLIPBOARD_CHECK);
        ru.sklad.app.service.PermissionService.VIEWS.forEach((route, label) -> {
            if (permissions.canAccess(route)) {
                SideNavItem item = new SideNavItem(label, route, icons.get(route).create());
                if ("deliveries".equals(route)) item.setSuffixComponent(deliveryBadge);
                navigation.addItem(item);
            }
        });
        SideNavItem website = new SideNavItem("Наш сайт", "https://agro-korma-shop.ru/", VaadinIcon.EXTERNAL_LINK.create());
        website.setOpenInNewBrowserTab(true);
        website.getStyle().set("margin-top", "var(--lumo-space-m)");
        navigation.addItem(website);
        addToDrawer(navigation);
    }

    @Override
    protected void afterNavigation() {
        super.afterNavigation();
        refreshDeliveryBadge();
        if (isOverlay()) setDrawerOpened(false);
    }

    public void refreshDeliveryBadge() {
        if (!permissions.canAccess("deliveries")) {
            deliveryBadge.setVisible(false);
            return;
        }
        long count = deliveries.countNewToday();
        deliveryBadge.setText(Long.toString(count));
        deliveryBadge.getElement().setAttribute("aria-label", "Новых доставок на сегодня: " + count);
        deliveryBadge.setVisible(count > 0);
    }

    private void applyTheme(UI ui, Button button, boolean dark) {
        String scheme = java.util.Objects.toString(ui.getSession().getAttribute("colorScheme"), "corporate");
        if (!java.util.Set.of("corporate", "nature", "ocean", "sunset").contains(scheme)) scheme = "corporate";
        ui.getPage().executeJs("document.documentElement.setAttribute('theme', $0)", scheme + (dark ? " dark" : ""));
        button.setIcon((dark ? VaadinIcon.SUN_O : VaadinIcon.MOON_O).create());
        button.setAriaLabel(dark ? "Включить светлую тему" : "Включить темную тему");
        button.setTooltipText(dark ? "Светлая тема" : "Темная тема");
    }
}
