package ru.sklad.app.views;

import ru.sklad.app.util.GridColumns;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.textfield.*;
import com.vaadin.flow.component.timepicker.TimePicker;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import ru.sklad.app.entity.*;
import ru.sklad.app.repository.*;
import ru.sklad.app.service.CallbackRequestService;
import ru.sklad.app.service.DeliveryService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Route(value = "deliveries", layout = MainLayout.class)
@PageTitle("Доставка — Всё сходится!")
@jakarta.annotation.security.RolesAllowed({"ADMIN", "SELLER"})
public class DeliveriesView extends VerticalLayout {
    private final ru.sklad.app.service.ProductAlternativeService suggestions;
    private final DeliveryService service;
    private final CustomerRepository customers;
    private final ProductRepository products;
    private final Grid<Delivery> grid = ru.sklad.app.util.GridColumns.compact(new Grid<>(Delivery.class, false));
    private final Grid<Delivery> pastGrid = ru.sklad.app.util.GridColumns.compact(new Grid<>(Delivery.class, false));
    private final Span currentBadge = new Span();
    private final Span callbackBadge = new Span();
    private final Grid<CallbackRequest> callbackGrid = GridColumns.compact(new Grid<>(CallbackRequest.class, false));
    private final Grid<CallbackRequest> allCallbackGrid = GridColumns.compact(new Grid<>(CallbackRequest.class, false));
    private final Grid<Delivery> allDeliveryGrid = GridColumns.compact(new Grid<>(Delivery.class, false));
    private final Span noCallbacks = new Span("Нет заявок на звонок.");
    private final Span noAllCallbacks = new Span("Нет заявок на звонок.");
    private final Span noOpenDeliveries = new Span("Нет актуальных доставок.");
    private List<UUID> pendingCallbackIds;
    private List<DeliveryRow> openDeliveryRows;
    private final Grid<Customer> customerGrid = ru.sklad.app.util.GridColumns.compact(new Grid<>(Customer.class, false));
    private final DatePicker selectedDate = new DatePicker("Дата доставки", LocalDate.now());

    private final ru.sklad.app.service.PermissionService permissions;
    private final CallbackRequestService callbacks;
    public DeliveriesView(DeliveryService service, CustomerRepository customers, ProductRepository products,
                          ru.sklad.app.service.ProductAlternativeService suggestions, ru.sklad.app.service.PermissionService permissions,
                          CallbackRequestService callbacks) {
        this.permissions = permissions;
        this.callbacks = callbacks;
        this.suggestions = suggestions;
        this.service = service; this.customers = customers; this.products = products;
        setSizeFull(); addClassNames("deliveries-view", "compact-page"); getStyle().set("overflow", "auto");
        Button create = new Button("Создать заявку", e -> edit(null));
        create.setVisible(permissions.canEdit("deliveries"));
        create.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        add(new H3("Доставки"));
        selectedDate.setRequiredIndicatorVisible(true);
        selectedDate.setMin(LocalDate.of(1, 1, 1)); selectedDate.setMax(LocalDate.of(9999, 12, 31));
        selectedDate.addValueChangeListener(e -> refresh());
        HorizontalLayout filters = new HorizontalLayout(selectedDate,
                new Button("Вчера", e -> selectedDate.setValue(LocalDate.now().minusDays(1))),
                new Button("Сегодня", e -> selectedDate.setValue(LocalDate.now())),
                new Button("Завтра", e -> selectedDate.setValue(LocalDate.now().plusDays(1))), create);
        filters.setAlignItems(Alignment.BASELINE); filters.addClassName("app-toolbar");
        configureDeliveryGrid(grid);
        configureDeliveryGrid(pastGrid);
        VerticalLayout requests = new VerticalLayout(filters, grid);
        requests.addClassNames("compact-tab-body", "app-card"); requests.setPadding(false); requests.setWidthFull(); requests.setFlexGrow(0, grid);
        customerGrid.addColumn(Customer::getName).setHeader("Имя");
        customerGrid.addColumn(c -> ru.sklad.app.util.PhoneFields.display(c.getPhone())).setHeader("Телефон");
        customerGrid.addColumn(Customer::getAddress).setHeader("Адрес").setFlexGrow(2);
        GridColumns.configure(customerGrid, "Имя", "Адрес");
        customerGrid.setWidthFull(); customerGrid.setHeight("50px");
        Button addCustomer = new Button("Добавить заказчика", e -> createCustomer());
        addCustomer.setVisible(permissions.canEdit("deliveries"));
        addCustomer.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        VerticalLayout customerTab = new VerticalLayout(addCustomer, customerGrid);
        customerTab.addClassNames("compact-tab-body", "app-card"); customerTab.setPadding(false); customerTab.setWidthFull(); customerTab.setFlexGrow(0, customerGrid);
        com.vaadin.flow.component.tabs.TabSheet tabs = new com.vaadin.flow.component.tabs.TabSheet();
        currentBadge.getElement().getThemeList().add("badge"); currentBadge.getElement().getThemeList().add("error");
        currentBadge.getStyle().set("background", "#b91c1c").set("color", "white").set("margin-left", "8px");
        var currentTab = new com.vaadin.flow.component.tabs.Tab(new Span("Актуальные"), currentBadge);
        configureDeliveryGrid(allDeliveryGrid);
        configureCallbacks(allCallbackGrid);
        var allPanel = new VerticalLayout(new H3("Актуальные доставки"), noOpenDeliveries, allDeliveryGrid,
                new H3("Заявки на звонок"), noAllCallbacks, allCallbackGrid);
        allPanel.addClassNames("compact-tab-body", "app-card");
        allPanel.setPadding(false);
        allPanel.setWidthFull();
        var allTab = tabs.add("Все заявки", allPanel);
        tabs.add(currentTab, requests);
        tabs.add("Прошлые", ru.sklad.app.util.AppCards.table(pastGrid));
        tabs.add("Заказчики", customerTab);
        configureCallbacks(callbackGrid);
        callbackBadge.getStyle().set("display", "inline-flex").set("align-items", "center").set("justify-content", "center")
                .set("min-width", "1.6em").set("height", "1.6em").set("padding", "0 0.25em")
                .set("border-radius", "999px").set("margin-left", "8px").set("background", "#b91c1c").set("color", "white");
        var callbackTab = new com.vaadin.flow.component.tabs.Tab(new Span("Заявка на звонок"), callbackBadge);
        var callbackPanel = new VerticalLayout(noCallbacks, callbackGrid);
        callbackPanel.addClassNames("compact-tab-body", "app-card");
        callbackPanel.setPadding(false);
        tabs.add(callbackTab, callbackPanel);
        tabs.addSelectedChangeListener(event -> {
            if (event.getSelectedTab() == callbackTab) refreshCallbacks();
            if (event.getSelectedTab() == allTab) {
                refreshOpenDeliveries();
                refreshCallbacks();
            }
        });
        tabs.setSelectedTab(allTab); tabs.setWidthFull(); tabs.addClassName("compact-tabs");
        add(tabs); setFlexGrow(0, tabs); refresh(); refreshCustomers(); refreshCallbacks();
        addAttachListener(event -> {
            var ui = event.getUI();
            var poll = ui.addPollListener(pollEvent -> {
                refreshCallbacks();
                if (tabs.getSelectedTab() == allTab) refreshOpenDeliveries();
            });
            addDetachListener(detach -> {
                poll.remove();
                detach.unregisterListener();
            });
        });
    }

    private void configureCallbacks(Grid<CallbackRequest> callbackGrid) {
        var dateTime = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());
        callbackGrid.addColumn(request -> dateTime.format(request.getCreatedAt())).setHeader("Поступила");
        callbackGrid.addColumn(CallbackRequest::getCustomerName).setHeader("Имя");
        callbackGrid.addComponentColumn(request -> {
            var details = new Span(Objects.toString(request.getOrderDetails(), ""));
            details.getStyle().set("white-space", "pre-wrap");
            return details;
        }).setHeader("Товары и комментарий").setFlexGrow(2);
        callbackGrid.addComponentColumn(request -> new com.vaadin.flow.component.html.Anchor(
                "tel:+" + request.getPhone(), ru.sklad.app.util.PhoneFields.display(request.getPhone()))).setHeader("Телефон");
        if (permissions.canEdit("deliveries")) {
            callbackGrid.addComponentColumn(request -> new Button("Звонок выполнен", event -> {
                callbacks.complete(request.getId());
                refreshCallbacks();
            })).setHeader("Действия");
        }
        GridColumns.configure(callbackGrid, "Имя");
        callbackGrid.setWidthFull();
        callbackGrid.setHeight("50px");
    }

    private void refreshCallbacks() {
        var pending = callbacks.pending();
        var ids = pending.stream().map(CallbackRequest::getId).toList();
        if (ids.equals(pendingCallbackIds)) return;
        pendingCallbackIds = ids;
        callbackGrid.setItems(pending);
        allCallbackGrid.setItems(pending);
        callbackGrid.setVisible(!pending.isEmpty());
        allCallbackGrid.setVisible(!pending.isEmpty());
        noCallbacks.setVisible(pending.isEmpty());
        noAllCallbacks.setVisible(pending.isEmpty());
        callbackBadge.setText(Integer.toString(pending.size()));
        callbackBadge.setVisible(!pending.isEmpty());
        callbackBadge.getElement().setAttribute("aria-label", "Необработанных заявок на звонок: " + pending.size());
        GridColumns.updateGridHeight(callbackGrid);
        GridColumns.updateGridHeight(allCallbackGrid);
    }

    private record DeliveryRow(Long id, LocalDate date, LocalTime time, String name, String phone, BigDecimal total) {}

    private void refreshOpenDeliveries() {
        var open = service.listOpen();
        var rows = open.stream().map(d -> new DeliveryRow(d.getId(), d.getDeliveryDate(), d.getDeliveryTime(),
                d.getCustomer().getName(), d.getCustomer().getPhone(), d.getTotalSum())).toList();
        if (rows.equals(openDeliveryRows)) return;
        openDeliveryRows = rows;
        allDeliveryGrid.setItems(open);
        allDeliveryGrid.setVisible(!open.isEmpty());
        noOpenDeliveries.setVisible(open.isEmpty());
        GridColumns.updateGridHeight(allDeliveryGrid);
    }
    private void configureDeliveryGrid(Grid<Delivery> grid) {
        grid.addColumn(d -> d.getDeliveryDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))).setHeader("Дата");
        grid.addColumn(d -> d.getDeliveryTime().format(DateTimeFormatter.ofPattern("HH:mm"))).setHeader("Время");
        grid.addColumn(d -> d.getCustomer().getName()).setHeader("Клиент").setFlexGrow(2);
        grid.addColumn(d -> ru.sklad.app.util.PhoneFields.display(d.getCustomer().getPhone())).setHeader("Телефон");
        grid.addColumn(d -> money(d.getTotalSum())).setHeader("Сумма");
        grid.addComponentColumn(d -> {
            Span badge = new Span(d.getStatus());
            badge.getElement().getThemeList().add("badge");
            if ("Выполнена".equals(d.getStatus())) badge.getElement().getThemeList().add("success");
            if ("Отменена".equals(d.getStatus())) badge.getElement().getThemeList().add("error");
            badge.getStyle().set("color", "white").set("background-color", switch (d.getStatus()) {
                case "Выполнена" -> "#2e7d32";
                case "Отменена" -> "#b91c1c";
                default -> "#1565c0";
            });
            return badge;
        }).setHeader("Статус");
        grid.addComponentColumn(d -> {
            Button complete = new Button("Доставка выполнена", e -> changeStatus(d.getId(), true));
            complete.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            Button cancel = new Button("Отменить", e -> changeStatus(d.getId(), false));
            cancel.addThemeVariants(ButtonVariant.LUMO_ERROR);
            HorizontalLayout actions = new HorizontalLayout(complete, cancel);
            actions.getStyle().set("flex-wrap", "wrap"); actions.setVisible(permissions.canEdit("deliveries") && "Новая".equals(d.getStatus()));
            return actions;
        }).setHeader("Действия").setWidth("330px").setFlexGrow(0);
        GridColumns.configure(grid, "Клиент");
        grid.setWidthFull(); grid.setHeight("50px");
        grid.asSingleSelect().addValueChangeListener(e -> {
            if (permissions.canEdit("deliveries") && e.isFromClient() && e.getValue() != null) {
                Long id = e.getValue().getId(); grid.deselectAll(); edit(id);
            }
        });
    }
    private void refresh() {
        refreshOpenDeliveries();
        // Count the same rows that are displayed, including when another date is selected.
        List<Delivery> current = selectedDate.isInvalid() ? List.of() : service.list(selectedDate.getValue()).stream()
                .filter(d -> "Новая".equals(d.getStatus())).toList();
        grid.setItems(current);
        pastGrid.setItems(service.list().stream().filter(d -> "Выполнена".equals(d.getStatus()) || "Отменена".equals(d.getStatus())).toList());
        GridColumns.updateGridHeight(pastGrid);
        currentBadge.setText(Integer.toString(current.size()));
        currentBadge.setVisible(!current.isEmpty());
        currentBadge.getElement().setAttribute("aria-label", "Новых доставок на выбранную дату: " + current.size());
        GridColumns.updateGridHeight(grid);
        // The router layout is an ancestor, not necessarily a direct UI child.
        com.vaadin.flow.component.Component parent = getParent().orElse(null);
        while (parent != null) {
            if (parent instanceof MainLayout main) { main.refreshDeliveryBadge(); break; }
            parent = parent.getParent().orElse(null);
        }
    }
    private void refreshCustomers() { customerGrid.setItems(service.customers()); GridColumns.updateGridHeight(customerGrid); }

    private void addDeliveryProduct(Product selected, BigDecimal requested,
                                    Map<Long, DeliveryService.Line> cart, Runnable refreshCart) {
        Product current = products.findById(selected.getId()).orElse(null);
        if (current == null) { Notification.show("Товар больше не существует"); return; }
        BigDecimal stock = ru.sklad.app.service.InventoryService.stock(current).max(BigDecimal.ZERO);
        if (stock.signum() == 0) {
            Map<Long, BigDecimal> quantities = new LinkedHashMap<>();
            cart.forEach((id, line) -> quantities.put(id, line.quantity()));
            ProductSuggestionsDialog.missing(current, suggestions.choices(current.getId(), requested, quantities), suggestions,
                    alternative -> addDeliveryProduct(alternative, requested, cart, refreshCart),
                    additive -> addDeliveryProduct(additive, BigDecimal.ONE, cart, refreshCart));
            return;
        }
        if (current.getRetailPrice() == null || current.getRetailPrice().signum() < 0) {
            Notification.show("У товара не указана корректная цена"); return;
        }
        BigDecimal previous = cart.containsKey(current.getId()) ? cart.get(current.getId()).quantity() : BigDecimal.ZERO;
        BigDecimal wanted = previous.add(requested);
        BigDecimal amount = wanted.min(stock);
        cart.put(current.getId(), new DeliveryService.Line(current, amount));
        if (wanted.compareTo(stock) > 0)
            Notification.show("Добавлено максимально возможное количество ("
                    + amount.stripTrailingZeros().toPlainString().replace('.', ',') + ")");
        refreshCart.run();
    }
    private void changeStatus(Long id, boolean complete) {
        try {
            if (complete) service.complete(id); else service.cancel(id);
            refresh(); Notification.show(complete ? "Доставка выполнена, товары списаны" : "Доставка отменена");
        } catch (IllegalArgumentException exception) {
            refresh(); Notification.show(exception.getMessage());
        }
    }
    private void createCustomer() {
        Dialog dialog = new Dialog(); dialog.setHeaderTitle("Добавить заказчика");
        dialog.setWidth("480px"); dialog.setMaxWidth("95vw"); dialog.setCloseOnOutsideClick(false);
        TextField name = new TextField("Имя"); TextField phone = new TextField("Телефон"); TextField address = new TextField("Адрес");
        for (TextField field : List.of(name, phone, address)) {
            field.setWidthFull(); field.setRequiredIndicatorVisible(true); field.setMaxLength(255);
        }
        VerticalLayout form = new VerticalLayout(name, phone, address); form.setPadding(false); dialog.add(form);
        Button save = new Button("Сохранить", e -> {
            try {
                service.createCustomer(name.getValue(), address.getValue(), phone.getValue());
                refreshCustomers(); dialog.close(); Notification.show("Заказчик добавлен");
            } catch (IllegalArgumentException exception) { Notification.show(exception.getMessage()); }
            catch (DataIntegrityViolationException exception) { Notification.show("Заказчик с таким телефоном уже существует"); }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(new Button("Отмена", e -> dialog.close()), save); dialog.open();
    }
    private static String money(BigDecimal amount) {
        return NumberFormat.getCurrencyInstance(Locale.forLanguageTag("ru-RU")).format(amount);
    }
    private void edit(Long id) {
        Delivery existing = id == null ? null : service.get(id);
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(id == null ? "Новая заявка на доставку" : "Заявка №" + id);
        dialog.setWidth("960px"); dialog.setMaxWidth("96vw"); dialog.setCloseOnOutsideClick(false);
        ComboBox<Customer> customer = new ComboBox<>("Клиент — поиск по имени или телефону");
        customer.setWidthFull(); customer.setClearButtonVisible(true); customer.setAllowCustomValue(true);
        customer.setHelperText("Выберите клиента или введите имя нового клиента");
        customer.setItemLabelGenerator(c -> c.getName() + " · " + c.getPhone());
        customer.setPageSize(30);
        customer.setItems(query -> {
            String term = query.getFilter().orElse("").trim();
            return customers.search(term, DeliveryService.normalizePhone(term),
                    PageRequest.of(query.getPage(), query.getPageSize())).stream();
        }).setIdentifierProvider(Customer::getId);
        TextField name = new TextField("Имя нового клиента"); name.setMaxLength(255);
        TextField address = new TextField("Адрес"); address.setMaxLength(255);
        TextField phone = new TextField("Телефон"); phone.setMaxLength(40);
        name.setRequiredIndicatorVisible(true); address.setRequiredIndicatorVisible(true); phone.setRequiredIndicatorVisible(true);
        customer.addCustomValueSetListener(e -> {
            customer.clear(); name.setValue(e.getDetail().trim()); name.setVisible(true);
            address.setReadOnly(false); phone.setReadOnly(false);
            address.clear(); phone.clear();
        });
        customer.addValueChangeListener(e -> {
            if (!e.isFromClient()) return;
            Customer selected = e.getValue();
            name.setVisible(selected == null);
            address.setReadOnly(selected != null); phone.setReadOnly(selected != null);
            address.setValue(selected == null ? "" : selected.getAddress());
            phone.setValue(selected == null ? "" : ru.sklad.app.util.PhoneFields.display(selected.getPhone()));
        });
        DatePicker date = new DatePicker("Дата доставки", existing == null
                ? Objects.requireNonNullElse(selectedDate.getValue(), LocalDate.now()) : existing.getDeliveryDate());
        date.setMin(LocalDate.of(1, 1, 1)); date.setMax(LocalDate.of(9999, 12, 31)); date.setRequiredIndicatorVisible(true);
        TimePicker time = new TimePicker("Время доставки"); time.setStep(Duration.ofMinutes(1)); time.getElement().addPropertyChangeListener("value", "input", event -> {}); time.setLocale(java.util.Locale.forLanguageTag("ru-RU"));
        time.setValue(existing == null ? LocalTime.of(9, 0) : existing.getDeliveryTime()); time.setRequiredIndicatorVisible(true);
        RadioButtonGroup<String> payment = new RadioButtonGroup<>();
        payment.setLabel("Оплата"); payment.setItems("Нал", "Безнал");
        payment.setValue(existing == null ? "Нал" : existing.getPaymentMethod());
        ComboBox<String> status = new ComboBox<>("Статус"); status.setItems("Новая", "Выполнена", "Отменена");
        status.setValue(existing == null ? "Новая" : existing.getStatus());
        status.setReadOnly(true);
        status.setHelperText("Выполнение и отмена доступны в таблице заявок");
        TextArea comment = new TextArea("Комментарий"); comment.setWidthFull();
        comment.setValue(existing == null ? "" : Objects.toString(existing.getComment(), ""));
        FormLayout details = new FormLayout(customer, name, address, phone, date, time, payment, status, comment);
        details.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("600px", 2));
        details.setWidthFull(); details.setColspan(customer, 2); details.setColspan(comment, 2);
        Map<Long, DeliveryService.Line> cart = new LinkedHashMap<>();
        if (existing != null) {
            Customer selected = existing.getCustomer(); customer.setValue(selected);
            name.setVisible(false); address.setValue(selected.getAddress()); phone.setValue(selected.getPhone());
            address.setReadOnly(true); phone.setReadOnly(true);
            existing.getItems().forEach(i -> cart.put(i.getProduct().getId(), new DeliveryService.Line(i.getProduct(), i.getQuantity())));
        }
        Span total = new Span();
        Grid<DeliveryService.Line> items = ru.sklad.app.util.GridColumns.compact(new Grid<>());
        items.addColumn(line -> line.product().getName()).setHeader("Товар").setFlexGrow(2);
        items.addColumn(DeliveryService.Line::quantity).setHeader("Количество");
        items.addColumn(line -> money(price(line.product()).multiply(line.quantity()))).setHeader("Сумма");
        Runnable refreshCart = () -> {
            items.setItems(cart.values());
            GridColumns.updateGridHeight(items);
            total.setText("Итого: " + money(cart.values().stream().map(line -> price(line.product()).multiply(line.quantity()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP)));
        };
        items.addComponentColumn(line -> ProductSuggestionsDialog.additivesButton(line.product(), suggestions,
                additive -> addDeliveryProduct(additive, BigDecimal.ONE, cart, refreshCart)));
        items.addComponentColumn(line -> new Button("Удалить", e -> { cart.remove(line.product().getId()); refreshCart.run(); }));
        GridColumns.configure(items, "Товар");
        items.setWidthFull(); items.setHeight("50px");
        ComboBox<Product> product = new ComboBox<>("Товар"); product.setItemLabelGenerator(ProductSuggestionsDialog::stockLabel);
        product.addFocusListener(event -> product.getDataProvider().refreshAll());
        product.setPageSize(30); product.setWidth("320px");
        product.setItems(query -> products.findByNameContainingIgnoreCase(query.getFilter().orElse("").trim(),
                PageRequest.of(query.getPage(), query.getPageSize())).stream()).setIdentifierProvider(Product::getId);
        NumberField quantity = new NumberField("Количество"); quantity.setValue(1.0);
        quantity.setMin(0.01); quantity.setMax(99999999.99); quantity.setStep(0.01); quantity.setWidth("160px");
        product.addValueChangeListener(event -> {
            if (!event.isFromClient() || event.getValue() == null) return;
            Product current = products.findById(event.getValue().getId()).orElse(null);
            if (current != null && ru.sklad.app.service.InventoryService.stock(current).signum() <= 0) {
                Double value = quantity.getValue();
                BigDecimal requested = value != null && Double.isFinite(value) && value > 0 && !quantity.isInvalid()
                        ? BigDecimal.valueOf(value) : BigDecimal.ONE;
                addDeliveryProduct(current, requested, cart, refreshCart);
                product.clear();
            }
        });
        Button add = new Button("Добавить в заявку", e -> {
            if (product.getValue() == null || quantity.getValue() == null || quantity.isInvalid()
                    || !Double.isFinite(quantity.getValue()) || quantity.getValue() <= 0) {
                Notification.show("Выберите товар и корректное количество"); return;
            }
            Product chosen = product.getValue(); BigDecimal amount = BigDecimal.valueOf(quantity.getValue());
            if (amount.stripTrailingZeros().scale() > 2 || amount.compareTo(new BigDecimal("99999999.99")) > 0) {
                Notification.show("Количество — до 99999999,99, не более двух знаков после запятой"); return;
            }
            addDeliveryProduct(chosen, amount, cart, refreshCart);
            product.clear(); quantity.setValue(1.0); quantity.setInvalid(false);
        });
        Button allProducts = new Button("Все товары", e -> {
            Dialog catalogDialog = new Dialog(); catalogDialog.setHeaderTitle("Все товары склада");
            catalogDialog.setWidth("1000px"); catalogDialog.setMaxWidth("95vw");
            TextField search = new TextField("Поиск по названию"); search.setWidthFull();
            search.setClearButtonVisible(true);
            search.setValueChangeMode(com.vaadin.flow.data.value.ValueChangeMode.LAZY);
            Grid<Product> catalog = ru.sklad.app.util.GridColumns.compact(new Grid<>(Product.class, false));
            catalog.addColumn(Product::getName).setHeader("Товар").setFlexGrow(2);
            catalog.addColumn(Product::getCategory).setHeader("Категория");
            catalog.addColumn(p -> ru.sklad.app.service.InventoryService.stock(p).stripTrailingZeros().toPlainString()
                    + " " + Objects.toString(p.getUnit(), "")).setHeader("Остаток");
            catalog.addColumn(p -> money(price(p))).setHeader("Цена");
            catalog.addComponentColumn(p -> new Button("Добавить", event -> {
                addDeliveryProduct(p, BigDecimal.ONE, cart, refreshCart);
            })).setHeader("Действия");
            catalog.setHeight("420px");
            Runnable refreshCatalog = () -> catalog.setItems(products.findByNameContainingIgnoreCase(search.getValue().trim()));
            search.addValueChangeListener(event -> refreshCatalog.run());
            VerticalLayout body = new VerticalLayout(search, catalog); body.setPadding(false);
            catalogDialog.add(body);
            catalogDialog.getFooter().add(new Button("Готово", event -> catalogDialog.close()));
            refreshCatalog.run(); catalogDialog.open();
        });
        Button repeat = new Button("Как в прошлый раз", e -> {
            if (customer.getValue() == null) { Notification.show("Сначала выберите клиента из базы"); return; }
            var previous = service.repeat(customer.getValue().getId(), id);
            if (previous.isEmpty()) { Notification.show("У клиента пока нет предыдущих доставок"); return; }
            cart.clear(); previous.forEach(line -> addDeliveryProduct(line.product(), line.quantity(), cart, refreshCart)); refreshCart.run();
        });
        repeat.setTooltipText("Заменить корзину товарами из последней доставки клиента");
        HorizontalLayout controls = new HorizontalLayout(product, quantity, add, allProducts, repeat);
        controls.setAlignItems(Alignment.BASELINE); controls.getStyle().set("flex-wrap", "wrap");
        VerticalLayout basket = new VerticalLayout(new H2("Товары в заявке"), controls, items, total);
        basket.addClassName("custom-card"); basket.setWidthFull(); total.addClassName("metric-value");
        VerticalLayout content = new VerticalLayout(details, basket); content.setPadding(false);
        dialog.add(content); refreshCart.run();
        Button save = new Button("Сохранить", e -> {
            if (date.isInvalid() || time.isInvalid() || time.getValue() == null || phone.isInvalid()) { Notification.show("Проверьте дату и время доставки"); return; }
            Map<Long, BigDecimal> quantities = new LinkedHashMap<>();
            cart.forEach((productId, line) -> quantities.put(productId, line.quantity()));
            try {
                service.save(id, customer.getValue() == null ? null : customer.getValue().getId(), name.getValue(),
                        address.getValue(), phone.getValue(), date.getValue(), time.getValue(), payment.getValue(),
                        comment.getValue(), status.getValue(), quantities);
                dialog.close(); refresh(); refreshCustomers(); Notification.show("Заявка сохранена");
            } catch (IllegalArgumentException exception) {
                Notification.show(exception.getMessage());
            } catch (DataIntegrityViolationException exception) {
                Notification.show("Не удалось сохранить: проверьте телефон клиента и значения полей. Повторите поиск клиента.");
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        if (existing != null && !"Новая".equals(existing.getStatus())) {
            content.setEnabled(false); save.setVisible(false);
        }
        dialog.getFooter().add(new Button("Отмена", e -> dialog.close()), save);
        dialog.open();
    }
    private static BigDecimal price(Product product) {
        return product.getRetailPrice() == null ? BigDecimal.ZERO : product.getRetailPrice();
    }
}
