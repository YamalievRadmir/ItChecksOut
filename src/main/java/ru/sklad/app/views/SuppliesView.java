package ru.sklad.app.views;

import com.vaadin.flow.component.button.*;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.notification.*;
import com.vaadin.flow.component.orderedlayout.*;
import com.vaadin.flow.component.textfield.*;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.*;
import com.vaadin.flow.server.VaadinSession;
import jakarta.annotation.security.PermitAll;
import ru.sklad.app.entity.*;
import ru.sklad.app.repository.*;
import ru.sklad.app.service.*;
import ru.sklad.app.util.*;
import java.math.*;
import java.time.LocalDate;
import java.util.*;

@Route(value = "supplies", layout = MainLayout.class)
@PageTitle("Поставки — Всё сходится!")
@PermitAll
public class SuppliesView extends VerticalLayout implements BeforeEnterObserver {
    private static final BigDecimal MAX = new BigDecimal("99999999.99");
    private final ProductRepository products;
    private final SupplyRepository supplies;
    private final BatchSupplyService service;
    private final PermissionService permissions;
    private final SupplierService supplierService;
    private final Grid<Supplier> supplierGrid = new Grid<>(Supplier.class, false);
    private final TabSheet tabs = new TabSheet();
    private final Map<Long, CartLine> cart = new LinkedHashMap<>();
    private final Map<Long, Span> lineSums = new HashMap<>();
    private final Grid<CartLine> grid = new Grid<>();
    private final Grid<Supply> history = new Grid<>(Supply.class, false);
    private final ComboBox<Product> product = new ComboBox<>("Товар");
    private final NumberField quantity = new NumberField("Количество");
    private final NumberField purchasePrice = new NumberField("Цена закупа");
    private final TextField invoice = new TextField("Номер накладной");
    private final DatePicker date = new DatePicker("Дата поставки", LocalDate.now());
    private final ComboBox<String> supplier = new ComboBox<>("Поставщик");
    private final Span total = new Span();
    private final Span sourceInfo = new Span();
    private final Button receive = new Button("Оприходовать всю партию");
    private PendingSupply origin;
    private VaadinSession session;
    private UUID receiptId = UUID.randomUUID();
    private boolean receiving;

    public SuppliesView(ProductRepository products, SupplyRepository supplies,
                        PermissionService permissions, BatchSupplyService service, SupplierService supplierService) {
        this.products = products; this.supplies = supplies; this.permissions = permissions; this.service = service;
        this.supplierService = supplierService;
        setSizeFull(); addClassNames("supplies-view", "compact-page"); getStyle().set("overflow", "auto");
        boolean canReceive = permissions.hasPermission("canMakeSupply");
        boolean canSeePrice = permissions.hasPermission("canSeePurchasePrice");
        product.setItemLabelGenerator(p -> Objects.toString(p.getName(), ""));
        product.setPlaceholder("Начните вводить название..."); product.setClearButtonVisible(true); product.setPageSize(30);
        product.setItems(query -> products.findByNameContainingIgnoreCase(query.getFilter().orElse("").trim(),
                org.springframework.data.domain.PageRequest.of(query.getPage(), query.getPageSize())).stream());
        product.setWidthFull(); product.setRequiredIndicatorVisible(true);
        configureQuantity(quantity); quantity.setWidthFull(); quantity.setRequiredIndicatorVisible(true);
        // Empty fields after a successful add are a fresh form, not validation errors.
        product.setManualValidation(true); quantity.setManualValidation(true);
        purchasePrice.setReadOnly(true); purchasePrice.setVisible(canSeePrice); purchasePrice.setWidthFull();
        product.addValueChangeListener(event -> {
            purchasePrice.clear();
            if (canSeePrice && event.getValue() != null) products.findById(event.getValue().getId())
                    .ifPresent(p -> purchasePrice.setValue(price(p).doubleValue()));
        });
        Button add = new Button("Добавить в приемку", event -> addSelected());
        add.setVisible(canReceive); add.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        add.getStyle().set("align-self", "end");
        FormLayout addForm = new FormLayout(product, quantity, purchasePrice, add);
        addForm.addClassName("responsive-form"); addForm.setWidthFull();
        addForm.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("600px", 2),
                new FormLayout.ResponsiveStep("1000px", 4));

        invoice.setMaxLength(255); date.setMin(LocalDate.of(1, 1, 1)); date.setMax(LocalDate.of(9999, 12, 31));
        date.setRequiredIndicatorVisible(true);
        supplier.setAllowCustomValue(true); supplier.setClearButtonVisible(true);
        supplier.setPlaceholder("Выберите или введите поставщика");
        supplier.addCustomValueSetListener(event -> {
            if (event.getDetail().trim().length() > 255) { Notification.show("Название поставщика: не более 255 символов"); return; }
            supplier.setValue(event.getDetail().trim());
        });
        FormLayout partyFields = new FormLayout(invoice, date, supplier); partyFields.setWidthFull();
        partyFields.addClassName("responsive-form");
        partyFields.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("600px", 3));
        grid.setId("supply-cart");
        grid.addColumn(line -> line.name).setHeader("Товар");
        grid.addComponentColumn(line -> {
            NumberField field = new NumberField(); configureQuantity(field); field.setWidthFull();
            field.setAriaLabel("Количество: " + line.name); field.setValue(line.quantity.doubleValue()); field.setInvalid(!line.valid);
            field.setReadOnly(!canReceive); field.setValueChangeMode(ValueChangeMode.ON_BLUR);
            field.addValueChangeListener(event -> {
                Double value = event.getValue(); line.valid = validQuantity(value); field.setInvalid(!line.valid);
                if (line.valid) line.quantity = BigDecimal.valueOf(value);
                Span sum = lineSums.get(line.productId); if (sum != null) sum.setText(money(line.sum()));
                updateTotal();
            });
            return field;
        }).setHeader("Количество").setWidth("190px");
        if (canSeePrice) {
            grid.addColumn(line -> money(line.purchasePrice)).setHeader("Цена закупа");
            grid.addComponentColumn(line -> {
                Span sum = new Span(money(line.sum())); lineSums.put(line.productId, sum); return sum;
            }).setHeader("Сумма");
        }
        if (canReceive) grid.addComponentColumn(line -> new Button("Удалить", event -> {
            cart.remove(line.productId); refreshCart();
        })).setHeader("Действие");
        GridColumns.configure(grid, "Товар"); grid.setSizeFull(); grid.setMinHeight("240px");
        receive.setVisible(canReceive); receive.addThemeVariants(ButtonVariant.LUMO_PRIMARY); receive.setDisableOnClick(true);
        receive.addClickListener(event -> receiveParty());
        total.setVisible(canSeePrice); total.getStyle().set("font-weight", "700");
        HorizontalLayout actions = new HorizontalLayout(total, receive); actions.addClassName("app-toolbar"); actions.setAlignItems(Alignment.CENTER);
        sourceInfo.setVisible(false); sourceInfo.addClassName("metric-label");
        configureHistory(canSeePrice);
        Details historyPanel = new Details("История приемок", history); historyPanel.setWidthFull();
        VerticalLayout receiptTab = new VerticalLayout(new H4("Форма добавления в приемку"), addForm,
                new H3("Текущая приемка (Корзина)"), sourceInfo, partyFields, grid, actions, historyPanel);
        receiptTab.setPadding(false); receiptTab.setSizeFull(); receiptTab.addClassNames("compact-tab-body", "app-card");
        receiptTab.getStyle().set("overflow", "auto"); receiptTab.setFlexGrow(1, grid);
        supplierGrid.setId("supplier-directory");
        supplierGrid.addColumn(Supplier::getName).setHeader("Название").setKey("name");
        supplierGrid.addColumn(Supplier::getPhone).setHeader("Телефон").setKey("phone");
        supplierGrid.addColumn(Supplier::getAddress).setHeader("Адрес").setKey("address");
        GridColumns.configure(supplierGrid, "Название", "Адрес"); supplierGrid.setSizeFull();
        supplierGrid.addItemDoubleClickListener(event -> openSupplierDialog(event.getItem()));
        Button addSupplier = new Button("Добавить поставщика", event -> openSupplierDialog(null));
        addSupplier.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        VerticalLayout supplierTab = new VerticalLayout(addSupplier, supplierGrid);
        supplierTab.setPadding(false); supplierTab.setSizeFull(); supplierTab.addClassNames("compact-tab-body", "app-card");
        supplierTab.setFlexGrow(1, supplierGrid);
        var receipt = tabs.add("Приемка", receiptTab); tabs.add("Поставщики", supplierTab);
        tabs.setSizeFull(); tabs.addClassName("compact-tabs");
        tabs.addSelectedChangeListener(event -> {
            if (tabs.getSelectedTab() == receipt) refreshHistory();
            else refreshSupplierDirectory();
        });
        add(new H3("Поставки"), tabs); setFlexGrow(1, tabs);
        refreshHistory(); refreshCart();
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        session = event.getUI().getSession();
        PendingSupply pending = PendingSupplySession.get(session);
        if (pending != null && !pending.equals(origin) && permissions.hasPermission("canMakeSupply")) {
            // Import once; repeated navigation must not discard edits to the current receipt.
            Map<Long, CartLine> imported = new LinkedHashMap<>();
            for (var line : pending.lines()) {
                Product current = products.findById(line.productId()).orElseThrow(() -> new IllegalArgumentException("Товар больше не существует: " + line.name()));
                imported.put(current.getId(), new CartLine(current, line.quantity()));
            }
            cart.clear(); cart.putAll(imported); origin = pending; receiptId = UUID.randomUUID();
            tabs.setSelectedIndex(0);
            sourceInfo.setText("Из заявки на закупку"); sourceInfo.setVisible(true); refreshCart();
        }
        refreshHistory();
    }

    private void addSelected() {
        if (!permissions.hasPermission("canMakeSupply")) { Notification.show("У вас нет прав на приемку товара"); return; }
        product.setInvalid(product.getValue() == null);
        quantity.setInvalid(!validQuantity(quantity.getValue()));
        if (product.isInvalid() || quantity.isInvalid()) {
            Notification.show("Выберите товар и укажите количество больше нуля, не более двух знаков после запятой"); return;
        }
        Product selected = products.findById(product.getValue().getId()).orElse(null);
        if (selected == null) { Notification.show("Товар больше не существует"); return; }
        BigDecimal amount = BigDecimal.valueOf(quantity.getValue());
        CartLine existing = cart.get(selected.getId());
        if (existing != null) amount = amount.add(existing.quantity);
        if (amount.compareTo(MAX) > 0) { Notification.show("Количество превышает 99999999,99"); return; }
        cart.put(selected.getId(), new CartLine(selected, amount));
        clearAddForm(); refreshCart();
    }

    private void clearAddForm() {
        product.clear(); quantity.clear(); purchasePrice.clear();
        product.setInvalid(false); quantity.setInvalid(false); purchasePrice.setInvalid(false);
    }

    private void receiveParty() {
        if (receiving) return;
        receiving = true;
        try {
            if (cart.isEmpty() || cart.values().stream().anyMatch(line -> !line.valid)) {
                Notification.show("Добавьте товары и проверьте количество во всех строках"); return;
            }
            if (date.isInvalid() || date.getValue() == null || invoice.isInvalid() || supplier.isInvalid()) {
                Notification.show("Проверьте дату поставки, накладную и поставщика"); return;
            }
            service.receiveParty(receiptId, origin, cart.values().stream()
                    .map(line -> new BatchSupplyService.PartyLine(line.productId, line.quantity, line.purchasePrice)).toList(),
                    date.getValue(), invoice.getValue(), supplier.getValue());
            if (session != null && origin != null && origin.equals(PendingSupplySession.get(session))) PendingSupplySession.clear(session);
            cart.clear(); origin = null; receiptId = UUID.randomUUID(); sourceInfo.setVisible(false);
            clearAddForm(); invoice.clear(); supplier.clear(); date.setValue(LocalDate.now());
            refreshCart(); refreshHistory(); product.getDataProvider().refreshAll();
            Notification.show("Партия успешно оприходована").addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        } catch (IllegalArgumentException | ArithmeticException exception) {
            Notification.show(exception.getMessage()).addThemeVariants(NotificationVariant.LUMO_ERROR);
        } finally { receiving = false; receive.setEnabled(true); }
    }

    private void configureHistory(boolean canSeePrice) {
        history.addColumn(Supply::getSupplyDate).setHeader("Дата");
        history.addColumn(s -> s.getProduct().getName()).setHeader("Товар");
        history.addColumn(Supply::getQuantity).setHeader("Количество");
        if (canSeePrice) {
            history.addColumn(Supply::getPurchasePrice).setHeader("Цена закупа");
            history.addColumn(Supply::getTotalSum).setHeader("Сумма");
        }
        history.addColumn(Supply::getInvoiceNumber).setHeader("Накладная");
        history.addColumn(Supply::getSupplier).setHeader("Поставщик");
        GridColumns.configure(history, "Товар", "Поставщик"); history.setWidthFull(); history.setHeight("320px");
    }
    private void refreshHistory() {
        history.setItems(supplies.findAllByOrderBySupplyDateDescIdDesc());
        refreshSupplierDirectory();
    }
    private void refreshSupplierDirectory() {
        List<Supplier> directory = supplierService.list(); supplierGrid.setItems(directory);
        String selected = supplier.getValue();
        supplier.setItems(java.util.stream.Stream.concat(directory.stream().map(Supplier::getName), supplies.findSuppliers().stream())
                .distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList());
        if (selected != null) supplier.setValue(selected);
    }

    private void openSupplierDialog(Supplier selected) {
        Dialog dialog = new Dialog(); dialog.setHeaderTitle(selected == null ? "Добавить поставщика" : "Редактировать поставщика");
        dialog.setWidth("600px"); dialog.setMaxWidth("95vw"); dialog.setCloseOnOutsideClick(false);
        TextField name = new TextField("Имя / Название"); name.setMaxLength(255); name.setRequiredIndicatorVisible(true);
        name.setErrorMessage("Укажите название поставщика");
        TextField phone = new TextField("Телефон"); phone.setMaxLength(255);
        TextField address = new TextField("Адрес"); address.setMaxLength(255);
        TextArea comment = new TextArea("Комментарий"); comment.setHeight("140px");
        if (selected != null) {
            name.setValue(Objects.toString(selected.getName(), "")); phone.setValue(Objects.toString(selected.getPhone(), ""));
            address.setValue(Objects.toString(selected.getAddress(), "")); comment.setValue(Objects.toString(selected.getComment(), ""));
        }
        FormLayout form = new FormLayout(name, phone, address, comment); form.addClassName("responsive-form"); form.setWidthFull();
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("480px", 2));
        form.setColspan(name, 2); form.setColspan(address, 2); form.setColspan(comment, 2); dialog.add(form);
        Button save = new Button("Сохранить", event -> {
            name.setInvalid(name.getValue().isBlank()); if (name.isInvalid() || phone.isInvalid() || address.isInvalid()) return;
            try {
                Supplier saved = supplierService.save(selected == null ? null : selected.getId(), name.getValue(), phone.getValue(), address.getValue(), comment.getValue());
                boolean renameSelection = selected != null && Objects.equals(supplier.getValue(), selected.getName());
                refreshSupplierDirectory(); if (renameSelection) supplier.setValue(saved.getName());
                dialog.close(); Notification.show("Поставщик сохранен");
            } catch (IllegalArgumentException exception) { Notification.show(exception.getMessage()); }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        if (selected != null) {
            Button delete = new Button("Удалить", event -> {
                ConfirmDialog confirm = new ConfirmDialog(); confirm.setHeader("Удалить поставщика?");
                confirm.setText("Поставщик «" + selected.getName() + "» будет удален из справочника. История приемок сохранится.");
                confirm.setCancelable(true); confirm.setCancelText("Отмена"); confirm.setConfirmText("Удалить");
                confirm.setConfirmButtonTheme("error primary");
                confirm.addConfirmListener(confirmation -> {
                    try {
                        supplierService.delete(selected.getId()); refreshSupplierDirectory(); dialog.close();
                        Notification.show("Поставщик удален");
                    } catch (IllegalArgumentException exception) { Notification.show(exception.getMessage()); }
                });
                confirm.open();
            });
            delete.addThemeVariants(ButtonVariant.LUMO_ERROR); dialog.getFooter().add(delete);
        }
        dialog.getFooter().add(new Button("Отмена", event -> dialog.close()), save); dialog.open(); name.focus();
    }
    private void refreshCart() { lineSums.clear(); grid.setItems(new ArrayList<>(cart.values())); updateTotal(); }
    private void updateTotal() {
        boolean valid = cart.values().stream().allMatch(line -> line.valid);
        total.setText(valid ? "Итого: " + money(cart.values().stream().map(CartLine::sum).reduce(BigDecimal.ZERO, BigDecimal::add))
                : "Проверьте количество товаров");
    }
    private static void configureQuantity(NumberField field) {
        field.setMin(0.01); field.setMax(MAX.doubleValue()); field.setStep(0.01);
        field.setErrorMessage("Количество больше нуля, не более двух знаков после запятой");
    }
    private static boolean validQuantity(Double value) {
        return value != null && Double.isFinite(value) && value > 0 && value <= MAX.doubleValue()
                && BigDecimal.valueOf(value).stripTrailingZeros().scale() <= 2;
    }
    private static BigDecimal price(Product product) { return product.getPurchasePrice() == null ? BigDecimal.ZERO : product.getPurchasePrice(); }
    private static String money(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',') + " ₽"; }
    private static class CartLine {
        final Long productId;
        final String name;
        final BigDecimal purchasePrice;
        BigDecimal quantity;
        boolean valid = true;
        CartLine(Product product, BigDecimal quantity) {
            this.productId = product.getId(); this.name = product.getName(); this.purchasePrice = price(product); this.quantity = quantity;
        }
        BigDecimal sum() { return quantity.multiply(purchasePrice); }
    }
}
