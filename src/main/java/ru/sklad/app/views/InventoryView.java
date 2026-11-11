package ru.sklad.app.views;

import ru.sklad.app.util.GridColumns;

import com.vaadin.flow.component.button.*;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.*;
import com.vaadin.flow.component.textfield.*;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.*;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.security.core.context.SecurityContextHolder;
import ru.sklad.app.entity.Product;
import ru.sklad.app.entity.Inventory;
import ru.sklad.app.service.InventoryService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Route(value = "inventory", layout = MainLayout.class)
@PageTitle("Инвентаризация — Всё сходится!")
@RolesAllowed({"ADMIN", "SELLER"})
public class InventoryView extends VerticalLayout {
    private final InventoryService service;
    private final Grid<Product> grid = new Grid<>(Product.class, false);
    private final Grid<Inventory> history = new Grid<>(Inventory.class, false);
    private final Map<Long, BigDecimal> actual = new LinkedHashMap<>();
    private final Map<Long, BigDecimal> expected = new HashMap<>();
    private final Map<Long, Span> differences = new HashMap<>();
    private final Map<Long, NumberField> fields = new HashMap<>();
    private final Set<Long> invalid = new HashSet<>();
    private List<Product> products = List.of();
    private final TextField search = new TextField("Поиск товара");

    public InventoryView(InventoryService service) {
        this.service = service; setSizeFull(); addClassNames("inventory-view", "compact-page"); getStyle().set("overflow", "hidden");
        DatePicker date = new DatePicker("Дата инвентаризации", LocalDate.now());
        date.setMin(LocalDate.of(1, 1, 1)); date.setMax(LocalDate.of(9999, 12, 31)); date.setRequiredIndicatorVisible(true);
        TextField employee = new TextField("ФИО проводящего"); employee.setMaxLength(255); employee.setRequiredIndicatorVisible(true);
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        employee.setValue(authentication == null ? "" : authentication.getName()); employee.setWidth("320px");
        HorizontalLayout toolbar = new HorizontalLayout(date, employee); toolbar.addClassName("app-toolbar");
        search.setAriaLabel("Поиск товара"); search.setLabel(null); search.setPlaceholder("Введите название товара..."); search.setClearButtonVisible(true); search.setWidthFull();
        search.setValueChangeMode(ValueChangeMode.LAZY); search.addValueChangeListener(e -> filter());
        search.setWidth("220px"); toolbar.add(search); toolbar.setWidthFull();
        toolbar.setAlignItems(Alignment.END); toolbar.setFlexGrow(1, search);
        grid.addColumn(Product::getName).setHeader("Товар").setFlexGrow(2);
        grid.addColumn(p -> number(InventoryService.stock(p))).setHeader("Учетное кол-во");
        grid.addComponentColumn(product -> {
            NumberField field = new NumberField(); field.setAriaLabel("Фактическое количество: " + product.getName());
            field.setMin(0); field.setMax(99999999.99); field.setStep(0.01); field.setWidthFull();
            field.setErrorMessage("Введите неотрицательное число, не более двух знаков после запятой");
            field.setValueChangeMode(ValueChangeMode.EAGER);
            if (actual.containsKey(product.getId())) field.setValue(actual.get(product.getId()).doubleValue());
            fields.put(product.getId(), field);
            field.addValueChangeListener(e -> {
                Double value = e.getValue();
                boolean bad = value != null && (!Double.isFinite(value) || value < 0 || value > 99999999.99
                        || BigDecimal.valueOf(value).stripTrailingZeros().scale() > 2);
                field.setInvalid(bad);
                if (bad) { invalid.add(product.getId()); actual.remove(product.getId()); }
                else {
                    invalid.remove(product.getId());
                    if (value == null) actual.remove(product.getId()); else actual.put(product.getId(), BigDecimal.valueOf(value));
                }
                updateDifference(product.getId());
            });
            return field;
        }).setHeader("Фактическое кол-во").setWidth("230px").setFlexGrow(0);
        grid.addComponentColumn(product -> {
            Span difference = new Span(); differences.put(product.getId(), difference); updateDifference(product.getId());
            return difference;
        }).setHeader("Разница");
        GridColumns.configure(grid, "Товар");
        grid.setId("inventory-products"); grid.setSizeFull(); grid.setMinHeight("0");
        TextArea notes = new TextArea("Примечания"); notes.setWidthFull(); notes.setHeight("80px"); notes.getStyle().set("flex-shrink", "0");
        Button finish = new Button("Завершить инвентаризацию", e -> {
            if (date.isInvalid() || !invalid.isEmpty() || fields.values().stream().anyMatch(NumberField::isInvalid)) {
                Notification.show("Проверьте дату и фактические остатки, включая товары, скрытые поиском"); return;
            }
            try {
                int count = service.complete(date.getValue(), employee.getValue(), notes.getValue(), new LinkedHashMap<>(actual), expected);
                notes.clear(); reload(); Notification.show("Инвентаризация завершена. Обновлено " + count + " позиций");
            } catch (IllegalArgumentException exception) { Notification.show(exception.getMessage()); }
        });
        finish.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        history.addColumn(i -> i.getInventoryDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"))).setHeader("Дата");
        history.addColumn(Inventory::getEmployeeName).setHeader("Кто проводил"); history.addColumn(Inventory::getNotes).setHeader("Примечания");
        GridColumns.configure(history, "Кто проводил", "Примечания");
        history.setSizeFull(); history.setMinHeight("0");
        search.setTooltipText("Пустое фактическое количество сохраняет учетный остаток; 0 обнуляет его. Поиск сохраняет введенные значения.");
        Details notesSection = new Details("Примечания", notes); notesSection.setWidthFull();
        HorizontalLayout footer = new HorizontalLayout(notesSection, finish); footer.setWidthFull();
        footer.addClassNames("app-toolbar", "inventory-actions"); footer.setAlignItems(Alignment.START);
        Div countTab = new Div(toolbar, grid, footer); countTab.setSizeFull(); countTab.addClassNames("inventory-count-tab", "app-card");
        TabSheet tabs = new TabSheet(); tabs.addClassNames("compact-tabs", "inventory-tabs"); tabs.setSizeFull();
        tabs.add("Сверка остатков", countTab); tabs.add("История", ru.sklad.app.util.AppCards.table(history));
        add(new H3("Инвентаризация"), tabs); setFlexGrow(1, tabs);
        reload();
    }
    private void reload() {
        products = service.products(); actual.clear(); expected.clear(); differences.clear(); fields.clear(); invalid.clear();
        products.forEach(p -> expected.put(p.getId(), InventoryService.stock(p))); filter(); history.setItems(service.history());
    }
    private void filter() {
        String term = search.getValue().trim().toLowerCase(Locale.ROOT);
        grid.setItems(products.stream().filter(p -> Objects.toString(p.getName(), "").toLowerCase(Locale.ROOT).contains(term)).toList());
    }
    private void updateDifference(Long id) {
        Span span = differences.get(id); if (span == null) return;
        if (!actual.containsKey(id)) { span.setText("—"); span.getStyle().remove("color"); return; }
        BigDecimal difference = actual.get(id).subtract(expected.get(id));
        span.setText((difference.signum() > 0 ? "+" : "") + number(difference));
        span.getStyle().set("color", difference.signum() < 0 ? "var(--lumo-error-text-color)"
                : difference.signum() > 0 ? "var(--lumo-success-text-color)" : "var(--lumo-body-text-color)");
    }
    private static String number(BigDecimal value) { return value.stripTrailingZeros().toPlainString().replace('.', ','); }
}
