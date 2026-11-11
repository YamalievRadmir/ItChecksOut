package ru.sklad.app.views;

import ru.sklad.app.util.GridColumns;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import ru.sklad.app.entity.Sale;
import ru.sklad.app.service.SalesService;
import ru.sklad.app.service.ReceiptService;
import ru.sklad.app.util.ColumnVisibilityMenu;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import java.math.BigDecimal;
import java.text.NumberFormat;

@Route(value = "sales-history", layout = MainLayout.class)
@PageTitle("История продаж — Всё сходится!")
@jakarta.annotation.security.PermitAll
public class SalesHistoryView extends VerticalLayout {
    private final Grid<Sale> grid = new Grid<>(Sale.class, false);
    private final DatePicker from = new DatePicker("С");
    private final DatePicker to = new DatePicker("По");
    private final TextField search = new TextField();
    private final Span status = new Span();
    private List<Sale> history = List.of();
    private final Map<Long, Integer> receiptNumbers = new HashMap<>();
    private final SalesService service;
    private boolean updatingDateRange;
    private final Span revenueTotal = new Span();
    private final Span profitTotal = new Span();
    private final VerticalLayout profitCard = new VerticalLayout();

    public SalesHistoryView(SalesService service, ReceiptService receipts) {
        this.service = service;

        setSizeFull();
        addClassNames("sales-history-view", "compact-page");
        getStyle().set("overflow", "auto");
        add(new H2("История продаж"));
        from.setMax(LocalDate.of(9999, 12, 31));
        to.setMax(LocalDate.of(9999, 12, 31));
        from.setClearButtonVisible(true);
        to.setClearButtonVisible(true);
        LocalDate today = LocalDate.now();
        from.setValue(today);
        to.setValue(today);
        search.setPlaceholder("Номер чека или название товара");
        search.setAriaLabel("Поиск в истории продаж");
        search.setClearButtonVisible(true);
        search.setValueChangeMode(ValueChangeMode.LAZY);
        grid.addColumn(sale -> receiptNumbers.get(sale.getId())).setHeader("Чек №").setKey("receiptNumber");
        grid.addColumn(sale -> sale.getSaleDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")))
                .setHeader("Дата и время").setKey("date");
        grid.addColumn(sale -> sale.getItems().stream().map(item -> item.getProductName())
                .filter(Objects::nonNull).collect(Collectors.joining(", ")))
                .setHeader("Товар / позиции").setKey("products").setFlexGrow(2);
        grid.addColumn(sale -> sale.getItems().size()).setHeader("Позиций").setKey("items");
        grid.addColumn(Sale::getTotalSum).setHeader("Сумма без скидки").setKey("total");
        grid.addColumn(Sale::getDiscountSum).setHeader("Скидка").setKey("discount");
        grid.addColumn(Sale::getFinalSum).setHeader("Итог").setKey("final");
        grid.addColumn(sale -> "CASHLESS".equals(sale.getPaymentMethod()) ? "Безнал" : "Наличные")
                .setHeader("Способ оплаты").setKey("paymentMethod");
        Button todayButton = new Button("Сегодня", event -> selectDay(LocalDate.now()));
        Button yesterdayButton = new Button("Вчера", event -> selectDay(LocalDate.now().minusDays(1)));
        HorizontalLayout toolbar = new HorizontalLayout(from, to, todayButton, yesterdayButton, search,
                new Button("Показать", event -> refresh()), ColumnVisibilityMenu.create(grid));
        toolbar.setAlignItems(Alignment.BASELINE);
        toolbar.setWidthFull();
        toolbar.setFlexGrow(1, search);
        toolbar.addClassName("app-toolbar");
        from.addValueChangeListener(event -> { if (!updatingDateRange) refresh(); });
        to.addValueChangeListener(event -> { if (!updatingDateRange) refresh(); });
        search.addValueChangeListener(event -> filter());
        GridColumns.configure(grid, "Товар / позиции");
        grid.addItemDoubleClickListener(event -> {
            if (event.getItem() == null) return;
            try {
                new ReceiptDialog(receipts, event.getItem().getId(),
                        String.valueOf(receiptNumbers.getOrDefault(event.getItem().getId(), event.getItem().getId().intValue())),
                        this::refresh).open();
            } catch (IllegalArgumentException exception) {
                com.vaadin.flow.component.notification.Notification.show(exception.getMessage());
            }
        });
        grid.setSizeFull();
        grid.setMinHeight("160px");
        revenueTotal.addClassName("metric-value"); profitTotal.addClassName("metric-value");
        Span revenueLabel = new Span("Итого выручка:"); revenueLabel.addClassName("metric-label");
        Span profitLabel = new Span("Итого прибыль (без закупа):"); profitLabel.addClassName("metric-label");
        VerticalLayout revenueCard = new VerticalLayout(revenueLabel, revenueTotal);
        profitCard.add(profitLabel, profitTotal);
        for (VerticalLayout card : List.of(revenueCard, profitCard)) {
            card.addClassNames("custom-card", "history-total-card"); card.setSpacing(false);
        }
        profitCard.setVisible(false);
        HorizontalLayout cards = new HorizontalLayout(revenueCard, profitCard);
        cards.setWidthFull(); cards.addClassName("history-total-cards");
        VerticalLayout totals = new VerticalLayout(new H3("Итого за период"), cards);
        totals.setPadding(false); totals.setSpacing(false); totals.addClassName("history-totals");
        var tableCard = ru.sklad.app.util.AppCards.table(grid);
        add(toolbar, status, tableCard, totals);
        setFlexGrow(1, tableCard);
        refresh();
    }

    private void selectDay(LocalDate day) {
        // Apply both boundaries together, without loading an intermediate date range.
        updatingDateRange = true;
        try {
            from.setValue(day);
            to.setValue(day);
        } finally {
            updatingDateRange = false;
        }
        refresh();
    }

    private void refresh() {
        if (from.isInvalid() || to.isInvalid()
                || (from.getValue() != null && to.getValue() != null && from.getValue().isAfter(to.getValue()))) {
            history = List.of();
            receiptNumbers.clear();
            grid.setItems(history);
            revenueTotal.setText("—"); profitTotal.setText("—"); profitCard.setVisible(false);
            status.setText("Укажите корректный период: дата «С» должна быть не позже даты «По»");
            return;
        }
        var summary = service.historyWithTotals(from.getValue(), to.getValue());
        history = summary.sales();
        revenueTotal.setText(money(summary.revenue()));
        profitCard.setVisible(summary.profit() != null);
        profitTotal.setText(summary.profit() == null ? "—" : money(summary.profit()));
        profitTotal.getStyle().set("color", summary.profit() == null || summary.profit().signum() == 0
                ? "var(--lumo-body-text-color)" : summary.profit().signum() > 0
                ? "var(--lumo-success-text-color)" : "var(--lumo-error-text-color)");
        // The repository returns newest first, with ID as a tie-breaker for equal dates.
        // Number the loaded period once so searching does not change a check's displayed number.
        receiptNumbers.clear();
        for (int i = 0; i < history.size(); i++) {
            receiptNumbers.put(history.get(i).getId(), i + 1);
        }
        filter();
    }

    private void filter() {
        String term = search.getValue().trim().toLowerCase(Locale.ROOT);
        var items = history.stream().filter(sale -> String.valueOf(receiptNumbers.get(sale.getId())).contains(term)
                || sale.getItems().stream().anyMatch(item -> Objects.toString(item.getProductName(), "")
                .toLowerCase(Locale.ROOT).contains(term))).toList();
        grid.setItems(items);
        status.setText(items.isEmpty() ? "Продажи не найдены" : "Продаж: " + items.size());
    }

    private static String money(BigDecimal value) {
        return NumberFormat.getCurrencyInstance(Locale.forLanguageTag("ru-RU")).format(value);
    }
}
