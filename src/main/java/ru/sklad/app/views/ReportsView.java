package ru.sklad.app.views;

import ru.sklad.app.util.GridColumns;

import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.tabs.TabSheet;
import ru.sklad.app.util.ColumnVisibilityMenu;
import ru.sklad.app.util.RoleAccess;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import ru.sklad.app.repository.SaleItemRepository.ProductSales;
import ru.sklad.app.service.ReportsService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.List;
import ru.sklad.app.util.ExcelExporter;
import com.storedobject.chart.*;

import java.time.format.DateTimeFormatter;

@Route(value = "reports", layout = MainLayout.class)
@PageTitle("Отчеты — Всё сходится!")
@jakarta.annotation.security.PermitAll
public class ReportsView extends VerticalLayout {
    private List<ProductSales> displayedSales = List.of();
    private final Grid<ReportsService.DaySales> dailyGrid = ru.sklad.app.util.GridColumns.compact(new Grid<>());
    private final Grid<ReportsService.DayProduct> productGrid = ru.sklad.app.util.GridColumns.compact(new Grid<>());
    private final Details dailyDetails = new Details();
    private final Details dayDetails = new Details();
    private final Span dayStatus = new Span();
    private LocalDate selectedDay;

    public ReportsView(ReportsService reports) {

        setSizeFull();
        getStyle().set("overflow", "auto"); addClassNames("reports-view", "compact-page");
        add(new H3("Отчеты"));
        configureDailyTables(reports);
        VerticalLayout indicators = new VerticalLayout(new H3("Текущая стоимость склада"));
        VerticalLayout graphs = new VerticalLayout();
        boolean[] graphsSelected = {false};
        VerticalLayout leaders = new VerticalLayout(new H3("Топ-10 товаров по сумме до скидок"));
        for (VerticalLayout section : List.of(indicators, graphs, leaders)) {
            section.setPadding(false); section.setSpacing(false); section.setWidthFull(); section.addClassNames("report-section", "app-card");
        }
        var inventory = reports.inventory();
        Span cashValue = new Span(money(BigDecimal.ZERO));
        Span cashlessValue = new Span(money(BigDecimal.ZERO));
        HorizontalLayout cards = new HorizontalLayout();
        if (RoleAccess.isAdmin()) cards.add(card("Стоимость склада в закупке", inventory.purchase()),
                card("Потенциальная прибыль", inventory.profit()));
        cards.add(card("Стоимость склада в рознице", inventory.retail()),
                card("Выручка (Наличные)", cashValue), card("Выручка (Безнал)", cashlessValue));
        cards.setWidthFull();
        cards.getStyle().set("flex-wrap", "wrap");
        cards.getStyle().set("flex-shrink", "0");
        indicators.add(cards, new Span("Карточки выручки — за выбранный период, после скидок"));
        var currentMonth=java.time.YearMonth.now();
        var monthlyDays=reports.dailySales(currentMonth.atDay(1),currentMonth.atEndOfMonth());
        VerticalLayout monthly=new VerticalLayout(new H3("Итоги месяца"),
            new Span("Выручка: " + money(monthlyDays.stream().map(ReportsService.DaySales::revenue).reduce(BigDecimal.ZERO,BigDecimal::add))));
        if(RoleAccess.isAdmin()) monthly.add(new Span("Чистая прибыль: " + money(monthlyDays.stream().map(ReportsService.DaySales::profit).reduce(BigDecimal.ZERO,BigDecimal::add))));
        monthly.addClassNames("custom-card","app-card"); indicators.add(monthly);
        DatePicker from = new DatePicker("С", LocalDate.now().withDayOfMonth(1));
        DatePicker to = new DatePicker("По", LocalDate.now());
        from.setMax(LocalDate.of(9999, 12, 31));
        to.setMax(LocalDate.of(9999, 12, 31));
        from.setRequiredIndicatorVisible(true);
        to.setRequiredIndicatorVisible(true);
        Span status = new Span();
        Grid<ProductSales> grid = ru.sklad.app.util.GridColumns.compact(new Grid<>());
        grid.addColumn(ProductSales::getProductName).setHeader("Товар").setFlexGrow(2);
        grid.addColumn(ProductSales::getQuantity).setHeader("Продано");
        grid.addColumn(ProductSales::getUnit).setHeader("Ед. изм.");
        grid.addColumn(item -> money(item.getRevenue())).setHeader("Сумма до скидок");
        BigDecimal[] revenue = {BigDecimal.ZERO};
        grid.addComponentColumn(item -> {
            double share = revenue[0].signum() == 0 ? 0
                    : item.getRevenue().divide(revenue[0], 8, RoundingMode.HALF_UP).doubleValue();
            ProgressBar bar = new ProgressBar(0, 1, Math.max(0, Math.min(1, share)));
            bar.getElement().setAttribute("aria-label", "Доля выручки: "
                    + Math.round(share * 100) + "%");
            bar.setWidthFull();
            return bar;
        }).setHeader("Доля суммы до скидок");
        Runnable refresh = () -> {
            graphs.removeAll();
            if (from.isInvalid() || to.isInvalid() || from.getValue() == null || to.getValue() == null
                    || from.getValue().isAfter(to.getValue())) {
                displayedSales = List.of();
                grid.setItems(displayedSales);
                dailyGrid.setItems(List.of()); productGrid.setItems(List.of()); selectedDay = null;
                dayDetails.setOpened(false); dayDetails.setSummaryText("Детализация по дню"); dayStatus.setVisible(false);
                cashValue.setText("—");
                cashlessValue.setText("—");
                status.setText("Укажите корректный период: дата «С» должна быть не позже даты «По»");
                return;
            }
            if (graphsSelected[0]) createGraphs(graphs, reports, from.getValue(), to.getValue());
            var result = reports.sales(from.getValue(), to.getValue());
            dailyGrid.setItems(reports.dailySales(from.getValue(), to.getValue()));
            if (selectedDay != null && !selectedDay.isBefore(from.getValue()) && !selectedDay.isAfter(to.getValue())) {
                showDay(reports, selectedDay, false);
            } else {
                selectedDay = null; productGrid.setItems(List.of()); dayDetails.setOpened(false);
                dayDetails.setSummaryText("Детализация по дню"); dayStatus.setVisible(false);
            }
            revenue[0] = result.revenue();
            cashValue.setText(money(result.cashRevenue()));
            cashlessValue.setText(money(result.cashlessRevenue()));
            displayedSales = List.copyOf(result.topProducts());
            grid.setItems(displayedSales);
            status.setText(result.topProducts().isEmpty() ? "За выбранный период продаж нет"
                    : "Выручка за период после скидок: " + money(result.cashRevenue().add(result.cashlessRevenue())));
        };
        Button apply = new Button("Показать", event -> refresh.run());
        HorizontalLayout export = ExcelExporter.downloadButton("Выгрузить Топ-10", "top-10.xlsx",
                () -> ExcelExporter.export("Лидеры продаж", List.of(
                        new ExcelExporter.Column<ProductSales>("Товар", ProductSales::getProductName),
                        new ExcelExporter.Column<ProductSales>("Продано", ProductSales::getQuantity),
                        new ExcelExporter.Column<ProductSales>("Ед. изм.", ProductSales::getUnit),
                        new ExcelExporter.Column<ProductSales>("Сумма до скидок", ProductSales::getRevenue)), displayedSales));
        HorizontalLayout filters = new HorizontalLayout(from, to, apply);
        filters.setAlignItems(Alignment.BASELINE);
        filters.getStyle().set("flex-wrap", "wrap");
        from.addValueChangeListener(event -> refresh.run());
        to.addValueChangeListener(event -> refresh.run());
        GridColumns.configure(grid, "Товар");
        grid.setWidthFull();
        grid.setHeight("360px");
        grid.getStyle().set("flex-shrink", "0");
        grid.setMinHeight("0");
        filters.addClassName("app-toolbar");
        addComponentAtIndex(1, filters); addComponentAtIndex(2, status);
        leaders.add(export, grid);
        TabSheet extras = new TabSheet(); extras.setWidthFull(); extras.addClassName("report-extras");
        extras.getStyle().set("flex-shrink", "0");
        extras.add("Показатели", indicators);
        var chartTab = extras.add("Графики", graphs); extras.add("Топ-10 товаров", leaders);
        extras.addSelectedChangeListener(event -> {
            graphsSelected[0] = extras.getSelectedTab() == chartTab;
            if (graphsSelected[0] && graphs.getComponentCount() == 0) {
                if (!from.isInvalid() && !to.isInvalid() && from.getValue() != null && to.getValue() != null && !from.getValue().isAfter(to.getValue()))
                    createGraphs(graphs, reports, from.getValue(), to.getValue());
                else graphs.add(new Span("Укажите корректный период"));
            }
        });
        add(extras);
        refresh.run();
    }

    private void createGraphs(VerticalLayout graphs, ReportsService reports, LocalDate from, LocalDate to) {
        graphs.removeAll();
        var data = reports.chartData(from, to);
        String[] dates = data.daily().stream().map(point -> point.day().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))).toArray(String[]::new);
        boolean noSales = data.daily().stream().noneMatch(point -> point.receiptCount() > 0);
        SOChart revenue = cartesian(new LineChart(new CategoryData(dates),
                new Data(data.daily().stream().mapToDouble(point -> point.revenue().doubleValue()).toArray())));
        SOChart counts = cartesian(new BarChart(new CategoryData(dates),
                new Data(data.daily().stream().mapToDouble(point -> point.receiptCount()).toArray())), "Количество чеков", "Чеки");
        var top = data.topProducts().stream().filter(item -> item.getRevenue() != null && item.getRevenue().signum() > 0).toList();
        SOChart pie = chart();
        if (!top.isEmpty()) {
            CategoryData names = new CategoryData(top.stream().map(item -> java.util.Objects.toString(item.getProductName(), "Без названия")).toArray(String[]::new));
            Data values = new Data(top.stream().mapToDouble(item -> item.getRevenue().doubleValue()).toArray());
            PieChart series = new PieChart(); series.setItemNames(names); series.setData(values); series.setName("Выручка"); pie.add(series);
        }
        var categories = data.categories().stream().filter(item -> item.getRevenue() != null && item.getRevenue().signum() > 0).toList();
        SOChart category = cartesian(new BarChart(new CategoryData(categories.stream().map(item -> item.getCategory()).toArray(String[]::new)),
                new Data(categories.stream().mapToDouble(item -> item.getRevenue().doubleValue()).toArray())));
        Div grid = new Div(chartCard(revenue, "Выручка по дням", noSales), chartCard(counts, "Количество чеков по дням", noSales),
                chartCard(pie, "Топ-5 товаров", top.isEmpty()), chartCard(category, "Выручка по категориям", categories.isEmpty()));
        grid.setWidthFull(); grid.getStyle().set("display", "grid")
                .set("grid-template-columns", "repeat(auto-fit, minmax(min(100%, 400px), 1fr))")
                .set("gap", "16px");
        graphs.add(grid, new Span("Графики за выбранный период учитывают скидки."));
    }

    private void configureDailyTables(ReportsService reports) {
        boolean admin = RoleAccess.isAdmin();
        dailyGrid.setId("report-days");
        dailyGrid.addColumn(day -> day.day().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))).setHeader("Дата").setKey("date");
        dailyGrid.addColumn(day -> number(day.quantity())).setHeader("Количество товаров").setKey("quantity");
        dailyGrid.addColumn(day -> money(day.revenue())).setHeader("Общая сумма").setKey("revenue");
        if (admin) {
            dailyGrid.addComponentColumn(day -> profit(day.profit())).setHeader("Сумма прибыли").setKey("profit");
            dailyGrid.addColumn(day -> money(day.purchase())).setHeader("Сумма закупа").setKey("purchase");
        }
        GridColumns.configure(dailyGrid); dailyGrid.setWidthFull(); dailyGrid.setHeight("320px");
        dailyGrid.getColumns().forEach(column -> column.setTextAlign(ColumnTextAlign.END));
        dailyGrid.getColumnByKey("date").setTextAlign(ColumnTextAlign.START).setFlexGrow(1);
        dailyGrid.addItemDoubleClickListener(event -> {
            if (event.getItem() != null) showDay(reports, event.getItem().day(), true);
        });
        dailyGrid.addItemClickListener(event -> {
            if (selectedDay != null && event.getItem() != null && !selectedDay.equals(event.getItem().day()))
                showDay(reports, event.getItem().day(), true);
        });
        HorizontalLayout dayToolbar = new HorizontalLayout(new Span("Двойной клик по дню — детализация товаров"), ColumnVisibilityMenu.create(dailyGrid));
        dayToolbar.addClassName("app-toolbar"); dayToolbar.setAlignItems(Alignment.CENTER);
        VerticalLayout dayBody = new VerticalLayout(dayToolbar, dailyGrid);
        dayBody.setPadding(false); dayBody.setWidthFull(); dayBody.addClassNames("report-table-body", "app-card");
        dailyDetails.setSummaryText("Выручка по дням"); dailyDetails.add(dayBody); dailyDetails.setOpened(true);
        dailyDetails.setWidthFull();

        productGrid.setId("report-day-products");
        productGrid.addColumn(ReportsService.DayProduct::name).setHeader("Наименование товара").setKey("name");
        productGrid.addColumn(item -> number(item.quantity()) + " " + java.util.Objects.toString(item.unit(), ""))
                .setHeader("Количество").setKey("quantity");
        productGrid.addColumn(item -> money(item.salePrice())).setHeader("Цена продажи").setKey("salePrice");
        productGrid.addColumn(item -> money(item.revenue())).setHeader("Сумма продажи").setKey("revenue");
        if (admin) {
            productGrid.addColumn(item -> money(item.purchasePrice())).setHeader("Цена закупа").setKey("purchasePrice");
            productGrid.addComponentColumn(item -> profit(item.profit())).setHeader("Прибыль").setKey("profit");
        }
        GridColumns.configure(productGrid, "Наименование товара"); productGrid.setWidthFull(); productGrid.setHeight("320px");
        productGrid.getColumns().forEach(column -> column.setTextAlign(ColumnTextAlign.END));
        productGrid.getColumnByKey("name").setTextAlign(ColumnTextAlign.START);
        HorizontalLayout productToolbar = new HorizontalLayout(new Span("Цены — средние за единицу. Суммы продаж учитывают скидки."),
                ColumnVisibilityMenu.create(productGrid));
        productToolbar.addClassName("app-toolbar"); productToolbar.setAlignItems(Alignment.CENTER);
        dayStatus.getElement().setAttribute("role", "status");
        dayStatus.getElement().setAttribute("aria-live", "polite"); dayStatus.setVisible(false);
        VerticalLayout productBody = new VerticalLayout(productToolbar, dayStatus, productGrid);
        productBody.setPadding(false); productBody.setWidthFull(); productBody.addClassNames("report-table-body", "app-card");
        dayDetails.setId("report-day-detail");
        dayDetails.setSummaryText("Детализация по дню"); dayDetails.add(productBody); dayDetails.setOpened(false); dayDetails.setWidthFull();
        add(dailyDetails, dayDetails);
    }

    private void showDay(ReportsService reports, LocalDate date, boolean open) {
        selectedDay = date;
        var items = reports.dayProducts(date);
        productGrid.setItems(items);
        dayStatus.setText(items.isEmpty() ? "За этот день продаж не найдено" : "Товаров: " + items.size());
        dayStatus.setVisible(true);
        dayDetails.setSummaryText("Детализация за " + date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")));
        if (open) {
            dayDetails.setOpened(true);
            // Wait for the opened Details to be laid out before bringing its table into view.
            dayDetails.getElement().executeJs("requestAnimationFrame(() => this.scrollIntoView({"
                    + "behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth', block: 'start'}))");
        }
    }

    private static String number(BigDecimal value) { return value.stripTrailingZeros().toPlainString().replace('.', ','); }
    private Span profit(BigDecimal value) {
        Span span = new Span(money(value));
        span.getStyle().set("color", value.signum() < 0 ? "var(--lumo-error-text-color)"
                : value.signum() > 0 ? "var(--lumo-success-text-color)" : "var(--lumo-body-text-color)");
        return span;
    }

    private SOChart chart() {
        SOChart chart = new SOChart();
        chart.setWidthFull(); chart.setHeight("340px"); chart.setMinWidth("0");
        chart.disableDefaultLegend();
        return chart;
    }

    private SOChart cartesian(XYChart series) {
        return cartesian(series, "Выручка, руб.", "Выручка");
    }
    private SOChart cartesian(XYChart series, String axisName, String seriesName) {
        SOChart chart = chart();
        XAxis x = new XAxis(DataType.CATEGORY);
        YAxis y = new YAxis(DataType.NUMBER); y.setName(axisName); y.setMin(0);
        RectangularCoordinate coordinates = new RectangularCoordinate(x, y);
        coordinates.sizeIncludesLabels();
        Position position = new Position(); position.setLeft(Size.pixels(12)); position.setRight(Size.pixels(20));
        position.setTop(Size.pixels(45)); position.setBottom(Size.pixels(24)); coordinates.setPosition(position);
        series.setName(seriesName); series.plotOn(coordinates); chart.add(series);
        return chart;
    }

    private Div chartCard(SOChart chart, String title, boolean empty) {
        var heading = new com.vaadin.flow.component.html.H3(title);
        heading.getStyle().set("margin-top", "0");
        Div card = new Div(heading);
        if (empty) card.add(new Span("Нет данных"));
        else card.add(chart);
        card.setWidthFull(); card.addClassName("app-card");
        card.getStyle().set("min-width", "0").set("box-sizing", "border-box").set("flex-shrink", "0");
        return card;
    }
    private Div card(String title, BigDecimal value) {
        return card(title, new Span(money(value)));
    }

    private Div card(String title, Span amount) {
        Span label = new Span(title);
        label.addClassName("metric-label");
        amount.addClassName("metric-value");
        Div card = new Div(label, amount);
        card.addClassName("custom-card");
        card.getStyle().set("flex", "1 1 240px");
        return card;
    }

    private String money(BigDecimal value) {
        return NumberFormat.getCurrencyInstance(Locale.forLanguageTag("ru-RU")).format(value.setScale(2, RoundingMode.HALF_UP));
    }
}
