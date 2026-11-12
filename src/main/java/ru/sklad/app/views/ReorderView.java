package ru.sklad.app.views;

import ru.sklad.app.util.GridColumns;

import com.vaadin.flow.component.button.*;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.*;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.textfield.*;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.*;
import jakarta.annotation.security.PermitAll;
import ru.sklad.app.entity.Product;
import ru.sklad.app.service.InventoryService;
import ru.sklad.app.service.PermissionService;
import ru.sklad.app.service.PurchaseOrderService;
import ru.sklad.app.service.PendingSupply;
import ru.sklad.app.util.PendingSupplySession;
import ru.sklad.app.service.PurchaseOrderService.Row;
import ru.sklad.app.util.ExcelExporter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.List;

@Route(value = "reorder", layout = MainLayout.class)
@PageTitle("К закупке — Всё сходится!")
@jakarta.annotation.security.RolesAllowed({"ADMIN", "SELLER"})
public class ReorderView extends VerticalLayout {
    private final PurchaseOrderService service;
    private final Grid<Row> orderGrid = ru.sklad.app.util.GridColumns.compact(new Grid<>());
    private final Grid<Product> stockGrid = ru.sklad.app.util.GridColumns.compact(new Grid<>(Product.class, false));
    private final TextField search = new TextField("Поиск по названию");

    public ReorderView(PurchaseOrderService service, PermissionService permissions) {
        this.service = service; setSizeFull(); addClassNames("reorder-view", "compact-page", "workspace-table-page"); getStyle().set("overflow", "auto");
        orderGrid.addColumn(Row::name).setHeader("Наименование").setFlexGrow(2);
        orderGrid.addComponentColumn(row -> {
            NumberField quantity = new NumberField(); quantity.addThemeVariants(com.vaadin.flow.component.textfield.TextFieldVariant.LUMO_SMALL); quantity.setAriaLabel("Количество к заказу: " + row.name());
            quantity.setMin(0); quantity.setMax(99999999.99); quantity.setStep(0.01); quantity.setWidth("120px");
            quantity.setValue(row.quantity().doubleValue()); quantity.setValueChangeMode(ValueChangeMode.ON_BLUR);
            quantity.addValueChangeListener(event -> {
                if (!event.isFromClient()) return;
                Double value = event.getValue();
                if (value == null || !Double.isFinite(value)) {
                    quantity.setInvalid(true); Notification.show("Введите корректное количество"); return;
                }
                try { apply(service.changeQuantity(row.productId(), BigDecimal.valueOf(value))); }
                catch (IllegalArgumentException exception) { quantity.setInvalid(true); Notification.show(exception.getMessage()); }
            });
            return quantity;
        }).setHeader("Количество к заказу").setAutoWidth(true).setFlexGrow(0);
        orderGrid.addColumn(Row::stock).setHeader("Остаток на складе");
        orderGrid.addColumn(Row::unit).setHeader("Ед. изм.");
        orderGrid.addComponentColumn(row -> {
            Button remove = new Button("Удалить", VaadinIcon.TRASH.create(), event -> apply(service.remove(row.productId())));
            remove.setAriaLabel("Удалить из заказа: " + row.name()); remove.addThemeVariants(ButtonVariant.LUMO_ERROR);
            return remove;
        }).setHeader("Действие");
        GridColumns.configure(orderGrid, "Наименование");
        orderGrid.setWidthFull(); orderGrid.setHeight("50px"); orderGrid.setMinHeight("0");
        VerticalLayout orderTab = new VerticalLayout(orderGrid); orderTab.addClassNames("compact-tab-body", "app-card"); orderTab.setPadding(false); orderTab.setSizeFull(); orderTab.setFlexGrow(0, orderGrid);

        search.setPlaceholder("Введите название товара..."); search.setPrefixComponent(VaadinIcon.SEARCH.create());
        search.setClearButtonVisible(true); search.setWidthFull(); search.setValueChangeMode(ValueChangeMode.LAZY);
        search.addValueChangeListener(event -> refreshStock());
        stockGrid.addColumn(Product::getName).setHeader("Наименование").setFlexGrow(2);
        stockGrid.addColumn(p -> Objects.toString(p.getCategory(), "—")).setHeader("Категория");
        stockGrid.addColumn(InventoryService::stock).setHeader("Остаток на складе");
        if (permissions.hasPermission("canSeePurchasePrice")) {
            stockGrid.addColumn(p -> p.getPurchasePrice() == null ? "—" : p.getPurchasePrice().setScale(2, RoundingMode.HALF_UP) + " ₽").setHeader("Цена закупа");
        }
        stockGrid.addComponentColumn(product -> {
            Button add = new Button(VaadinIcon.PLUS.create(), event -> openAddDialog(product));
            add.setAriaLabel("Добавить в заказ: " + product.getName()); add.setTooltipText("Добавить в заказ");
            add.addThemeVariants(ButtonVariant.LUMO_PRIMARY); return add;
        }).setHeader("Действие").setWidth("100px").setFlexGrow(0);
        GridColumns.configure(stockGrid, "Наименование");
        stockGrid.setWidthFull(); stockGrid.setHeight("65vh"); stockGrid.setMinHeight("0");
        VerticalLayout stockTab = new VerticalLayout(search, stockGrid); stockTab.addClassNames("compact-tab-body", "app-card"); stockTab.setPadding(false); stockTab.setSizeFull(); stockTab.setFlexGrow(0, stockGrid);
        TabSheet tabs = new TabSheet(); tabs.add("Заказ", orderTab); tabs.add("На складе", stockTab);
        tabs.setSizeFull(); tabs.addClassName("compact-tabs");
        tabs.addSelectedChangeListener(event -> { if (tabs.getSelectedIndex() == 1) refreshStock(); else apply(service.load()); });
        Button addManual = new Button("Добавить товар вручную", VaadinIcon.PLUS.create(), event -> openAddDialog(null));
        addManual.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        HorizontalLayout export = ExcelExporter.downloadButton("Выгрузить заявку в Excel", "reorder.xlsx", this::exportRequest);
        Button repeat = new Button("Как вчера", VaadinIcon.COPY.create(), event -> {
            try {
                apply(service.repeatYesterday()); tabs.setSelectedIndex(0);
                Notification.show("Вчерашний заказ скопирован");
            } catch (IllegalArgumentException exception) { Notification.show(exception.getMessage()); }
        });
        repeat.addThemeVariants(ButtonVariant.LUMO_SMALL);
        Button clear = new Button("Очистить", VaadinIcon.TRASH.create(), event -> {
            ConfirmDialog confirmation = new ConfirmDialog(); confirmation.setHeader("Очистить заказ?");
            confirmation.setText("Вы уверены, что хотите очистить текущий заказ?");
            confirmation.setCancelable(true); confirmation.setCancelText("Отмена");
            confirmation.setConfirmText("Очистить"); confirmation.setConfirmButtonTheme("error primary");
            confirmation.addConfirmListener(confirmed -> {
                apply(service.clear()); tabs.setSelectedIndex(0); Notification.show("Заказ очищен");
            });
            confirmation.open();
        });
        clear.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);
        Button transfer = new Button("Оприходовать привезенное", VaadinIcon.TRUCK.create(), event -> {
            try {
                PendingSupply pending = service.prepareSupply();
                getUI().ifPresent(ui -> {
                    PendingSupplySession.put(ui.getSession(), pending);
                    ui.navigate(SuppliesView.class);
                });
            } catch (IllegalArgumentException exception) { Notification.show(exception.getMessage()); }
        });
        transfer.setVisible(permissions.canAccess("supplies") && permissions.hasPermission("canMakeSupply"));
        HorizontalLayout footer = new HorizontalLayout(addManual, export, repeat, clear, transfer,
                new Button("Обновить", event -> { apply(service.load()); refreshStock(); }));
        footer.addClassNames("app-toolbar", "compact-actions"); footer.getStyle().set("flex-shrink", "0");
        add(new H3("К закупке"), tabs, footer); setFlexGrow(0, tabs); apply(service.load()); refreshStock();
    }
    private void apply(PurchaseOrderService.Draft draft) {
        orderGrid.setItems(draft.rows());
        GridColumns.updateGridHeight(orderGrid);
    }
    private void refreshStock() { stockGrid.setItems(service.catalog(search.getValue())); }
    private void openAddDialog(Product selected) {
        Dialog dialog = new Dialog(); dialog.setHeaderTitle(selected == null ? "Добавить товар в заказ" : "Добавить: " + selected.getName());
        dialog.setWidth("440px"); dialog.setMaxWidth("95vw"); dialog.setCloseOnOutsideClick(false);
        ComboBox<Product> product = new ComboBox<>("Поиск товара"); product.setWidthFull(); product.setClearButtonVisible(true);
        product.setItemLabelGenerator(Product::getName); product.setPageSize(30);
        product.setItems(query -> service.searchProducts(query.getFilter().orElse(""), query.getPage(), query.getPageSize()).stream()).setIdentifierProvider(Product::getId);
        product.setVisible(selected == null); if (selected != null) product.setValue(selected);
        NumberField quantity = new NumberField("Количество к заказу"); quantity.setMin(0.01); quantity.setMax(99999999.99);
        quantity.setStep(0.01); quantity.setValue(1.0); quantity.setWidthFull(); quantity.setRequiredIndicatorVisible(true);
        Span info = new Span();
        product.addValueChangeListener(event -> {
            Product value = event.getValue(); info.setText(value == null ? "" : "Остаток: " + InventoryService.stock(value) + " " + Objects.toString(value.getUnit(), ""));
        });
        if (selected != null) info.setText("Остаток: " + InventoryService.stock(selected) + " " + Objects.toString(selected.getUnit(), ""));
        VerticalLayout form = new VerticalLayout(product, info, quantity); form.setPadding(false); dialog.add(form);
        Button confirm = new Button("Добавить в заказ", event -> {
            Double value = quantity.getValue();
            if (product.getValue() == null || value == null || quantity.isInvalid() || !Double.isFinite(value)) {
                Notification.show("Выберите товар и укажите корректное количество"); return;
            }
            try { apply(service.add(product.getValue().getId(), BigDecimal.valueOf(value))); dialog.close(); Notification.show("Товар добавлен в заказ"); }
            catch (IllegalArgumentException exception) { quantity.setInvalid(true); quantity.setErrorMessage(exception.getMessage()); Notification.show(exception.getMessage()); }
        });
        // Service validation remains authoritative; allow correcting an invalid quantity without reopening the dialog.
        quantity.addValueChangeListener(event -> quantity.setInvalid(false));
        confirm.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(new Button("Отмена", event -> dialog.close()), confirm); dialog.open();
    }
    private byte[] exportRequest() {
        var draft = service.load();
        return ExcelExporter.export("Заказ на закупку", List.of(
                new ExcelExporter.Column<Row>("Наименование", Row::name),
                new ExcelExporter.Column<Row>("Количество к заказу", Row::quantity),
                new ExcelExporter.Column<Row>("Ед. изм.", Row::unit),
                new ExcelExporter.Column<Row>("Остаток на складе", Row::stock)), draft.rows());
    }
}
