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
    private final Grid<Product> grid = ru.sklad.app.util.GridColumns.compact(new Grid<>(Product.class, false));
    private final Grid<Inventory> history = ru.sklad.app.util.GridColumns.compact(new Grid<>(Inventory.class, false));
    private final Map<Long, BigDecimal> actual = new LinkedHashMap<>();
    private final Map<Long, BigDecimal> expected = new HashMap<>();
    private final Map<Long, Span> differences = new HashMap<>();
    private final Map<Long, NumberField> fields = new HashMap<>();
    private final Set<Long> invalid = new HashSet<>();
    private Long activeDraft;
    private final Grid<ru.sklad.app.entity.InventoryDraft> drafts = GridColumns.compact(new Grid<>(ru.sklad.app.entity.InventoryDraft.class,false));
    private List<Product> products = List.of();
    private final TextField search = new TextField("Поиск товара");

    public InventoryView(InventoryService service) {
        this.service = service; setSizeFull(); addClassNames("inventory-view", "compact-page", "workspace-table-page"); getStyle().set("overflow", "auto");
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
            NumberField field = new NumberField(); field.addThemeVariants(com.vaadin.flow.component.textfield.TextFieldVariant.LUMO_SMALL); field.setAriaLabel("Фактическое количество: " + product.getName());
            field.setMin(0); field.setMax(99999999.99); field.setStep(0.01); field.setWidth("120px");
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
        }).setHeader("Фактическое кол-во").setAutoWidth(true).setFlexGrow(0);
        grid.addComponentColumn(product -> {
            Span difference = new Span(); differences.put(product.getId(), difference); updateDifference(product.getId());
            return difference;
        }).setHeader("Разница");
        GridColumns.configure(grid, "Товар");
        grid.setId("inventory-products"); grid.setWidthFull(); grid.setHeight("65vh"); grid.setMinHeight("0");
        TextArea notes = new TextArea("Примечания"); notes.setWidthFull(); notes.setHeight("80px"); notes.getStyle().set("flex-shrink", "0");
        Button finish = new Button("Завершить инвентаризацию", e -> {
            if (date.isInvalid() || !invalid.isEmpty() || fields.values().stream().anyMatch(NumberField::isInvalid)) {
                Notification.show("Проверьте дату и фактические остатки, включая товары, скрытые поиском"); return;
            }
            try {
                int count = service.complete(date.getValue(), employee.getValue(), notes.getValue(), new LinkedHashMap<>(actual), expected);
                if(activeDraft != null) { service.deleteDraft(activeDraft); activeDraft=null; refreshDrafts(); }
                notes.clear(); reload(); Notification.show("Инвентаризация завершена. Обновлено " + count + " позиций");
            } catch (IllegalArgumentException exception) { Notification.show(exception.getMessage()); }
        });
        finish.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        history.addColumn(i -> i.getInventoryDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"))).setHeader("Дата");
        history.addColumn(Inventory::getEmployeeName).setHeader("Кто проводил"); history.addColumn(Inventory::getNotes).setHeader("Примечания");
        GridColumns.configure(history, "Кто проводил", "Примечания");
        history.setWidthFull(); history.setHeight("65vh"); history.setMinHeight("0");
        search.setTooltipText("Пустое фактическое количество сохраняет учетный остаток; 0 обнуляет его. Поиск сохраняет введенные значения.");
        Details notesSection = new Details("Примечания", notes); notesSection.setWidthFull();
        Button saveDraft = new Button("Сохранить черновик", event -> {
            if(date.isInvalid() || !invalid.isEmpty()) { Notification.show("Проверьте дату и количества"); return; }
            try { activeDraft = service.saveDraft(activeDraft,date.getValue(),employee.getValue(),snapshot(true)); refreshDrafts(); Notification.show("Черновик сохранен"); }
            catch(IllegalArgumentException ex) { Notification.show(ex.getMessage()); }
        });
        Anchor download = new Anchor(); download.getElement().setAttribute("download",true); download.setVisible(false);
        Button print = new Button("Печать акта",event -> {
            if(date.isInvalid() || date.getValue()==null || !invalid.isEmpty()) { Notification.show("Проверьте дату и количества"); return; }
            byte[] data=ru.sklad.app.util.InventoryData.document(date.getValue(),employee.getValue(),snapshot(false));
            var resource=new com.vaadin.flow.server.StreamResource("inventory-act.docx",()->new java.io.ByteArrayInputStream(data));
            resource.setContentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"); resource.setCacheTime(0);
            download.setHref(resource); download.getElement().executeJs("this.click()");
        });
        // Keep the download anchor in the DOM while hiding it visually.
        download.setVisible(true); download.getStyle().set("display","none");
        HorizontalLayout footer = new HorizontalLayout(notesSection, finish, saveDraft, print, download); footer.setWidthFull();
        footer.addClassNames("app-toolbar", "inventory-actions"); footer.setAlignItems(Alignment.START);
        VerticalLayout countTab = new VerticalLayout(toolbar, grid, footer); countTab.setPadding(false); countTab.setFlexGrow(0, grid); countTab.setSizeFull(); countTab.addClassNames("inventory-count-tab", "app-card");
        TabSheet tabs = new TabSheet(); tabs.addClassNames("compact-tabs", "inventory-tabs"); tabs.setSizeFull();
        tabs.add("Сверка остатков", countTab); tabs.add("История", ru.sklad.app.util.AppCards.table(history));
        drafts.addColumn(ru.sklad.app.entity.InventoryDraft::getDate).setHeader("Дата");
        drafts.addColumn(ru.sklad.app.entity.InventoryDraft::getEmployeeName).setHeader("Проводящий");
        drafts.addComponentColumn(d -> new Button("Продолжить",event -> {
            try {
                var saved=service.draft(d.getId()); var items=ru.sklad.app.util.InventoryData.decode(saved.getItemsData());
                reload(); activeDraft=saved.getId(); date.setValue(saved.getDate()); employee.setValue(saved.getEmployeeName());
                for(var item:items) {
                    expected.put(item.productId(),item.expected());
                    if(item.actual()!=null) actual.put(item.productId(),item.actual());
                }
                search.clear(); filter(); tabs.setSelectedIndex(0);
            } catch(IllegalArgumentException ex) { Notification.show(ex.getMessage()); }
        })).setHeader("Действия");
        GridColumns.configure(drafts,"Проводящий"); tabs.add("Черновики",ru.sklad.app.util.AppCards.table(drafts)); refreshDrafts();
        history.addItemClickListener(event -> showHistory(event.getItem()));
        add(new H3("Инвентаризация"), tabs); setFlexGrow(1, tabs);
        reload();
    }
    private void refreshDrafts() { drafts.setItems(service.drafts()); GridColumns.updateGridHeight(drafts); }
    private List<ru.sklad.app.util.InventoryData.Item> snapshot(boolean draft) {
        return products.stream().map(p -> new ru.sklad.app.util.InventoryData.Item(p.getId(),p.getName(),expected.getOrDefault(p.getId(),InventoryService.stock(p)),
            actual.getOrDefault(p.getId(),draft ? null : expected.getOrDefault(p.getId(),InventoryService.stock(p))),Objects.requireNonNullElse(p.getPurchasePrice(),BigDecimal.ZERO),Objects.requireNonNullElse(p.getRetailPrice(),BigDecimal.ZERO))).toList();
    }
    private void showHistory(Inventory inventory) {
        var dialog=new com.vaadin.flow.component.dialog.Dialog(); dialog.setHeaderTitle("Акт инвентаризации " + inventory.getInventoryDate().toLocalDate()); dialog.setWidth("1000px"); dialog.setMaxWidth("95vw");
        var items=ru.sklad.app.util.InventoryData.decode(inventory.getItemsData());
        if(items.isEmpty()) dialog.add(new Span("Для этой записи состав товаров не сохранен"));
        else {
            Grid<ru.sklad.app.util.InventoryData.Item> details=GridColumns.compact(new Grid<>());
            details.addColumn(ru.sklad.app.util.InventoryData.Item::name).setHeader("Товар");
            details.addColumn(ru.sklad.app.util.InventoryData.Item::expected).setHeader("Учетное кол-во");
            details.addColumn(ru.sklad.app.util.InventoryData.Item::actual).setHeader("Фактическое кол-во");
            details.addColumn(i -> number(i.difference()) + (i.difference().signum()<0 ? " (недостача)" : i.difference().signum()>0 ? " (излишек)" : "")).setHeader("Разница");
            details.addColumn(ru.sklad.app.util.InventoryData.Item::cost).setHeader("Себестоимость разницы");
            details.addColumn(ru.sklad.app.util.InventoryData.Item::lostProfit).setHeader("Упущенная выгода");
            details.setItems(items); GridColumns.configure(details,"Товар"); GridColumns.updateGridHeight(details);
            dialog.add(details,new Span("Себестоимость расхождений: " + number(items.stream().map(ru.sklad.app.util.InventoryData.Item::cost).reduce(BigDecimal.ZERO,BigDecimal::add)) + "; Упущенная выгода: " + number(items.stream().map(ru.sklad.app.util.InventoryData.Item::lostProfit).reduce(BigDecimal.ZERO,BigDecimal::add))));
        }
        dialog.getFooter().add(new Button("Закрыть",event -> dialog.close())); dialog.open();
    }
    private void reload() {
        products = service.products(); actual.clear(); expected.clear(); differences.clear(); fields.clear(); invalid.clear();
        products.forEach(p -> expected.put(p.getId(), InventoryService.stock(p))); filter(); history.setItems(service.history());
    }
    private void filter() {
        String term = search.getValue().trim().toLowerCase(Locale.ROOT);
        grid.setItems(products.stream().filter(p -> Objects.toString(p.getName(), "").toLowerCase(Locale.ROOT).contains(term)).toList());
        updateTotals();
    }
    private void updateDifference(Long id) {
        updateTotals();
        Span span = differences.get(id); if (span == null) return;
        if (!actual.containsKey(id)) { span.setText("—"); span.getStyle().remove("color"); return; }
        BigDecimal difference = actual.get(id).subtract(expected.get(id));
        span.setText((difference.signum() > 0 ? "+" : "") + number(difference));
        span.getStyle().set("color", difference.signum() < 0 ? "var(--lumo-error-text-color)"
                : difference.signum() > 0 ? "var(--lumo-success-text-color)" : "var(--lumo-body-text-color)");
    }
    private void updateTotals() {
        if (grid.getColumns().size() < 5) return;
        var rows = grid.getListDataView().getItems().toList();
        BigDecimal stock = rows.stream().map(InventoryService::stock).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal counted = rows.stream().map(p -> actual.getOrDefault(p.getId(), InventoryService.stock(p))).reduce(BigDecimal.ZERO, BigDecimal::add);
        grid.getColumns().get(1).setFooter("Итого");
        grid.getColumns().get(2).setFooter(number(stock));
        grid.getColumns().get(3).setFooter(number(counted));
        grid.getColumns().get(4).setFooter(number(counted.subtract(stock)));
    }
    private static String number(BigDecimal value) { return value.stripTrailingZeros().toPlainString().replace('.', ','); }
}
