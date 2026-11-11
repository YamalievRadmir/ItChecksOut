package ru.sklad.app.views;

import ru.sklad.app.util.GridColumns;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.*;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import ru.sklad.app.entity.AuditLog;
import ru.sklad.app.service.AuditService;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class AuditSettingsPanel extends VerticalLayout {
    private int page;
    public AuditSettingsPanel(AuditService audit) {
        setWidthFull(); setPadding(false);
        DatePicker from = new DatePicker("С", LocalDate.now().minusDays(29));
        DatePicker to = new DatePicker("По", LocalDate.now());
        from.setMax(LocalDate.of(9998,12,31)); to.setMax(LocalDate.of(9998,12,31));
        TextField username = new TextField("Пользователь"); username.setClearButtonVisible(true); username.setValueChangeMode(ValueChangeMode.LAZY);
        Grid<AuditLog> grid = new Grid<>(AuditLog.class, false);
        grid.addColumn(a -> a.getTimestamp().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"))).setHeader("Время");
        grid.addColumn(AuditLog::getUsername).setHeader("Кто"); grid.addColumn(AuditLog::getAction).setHeader("Действие");
        grid.addColumn(a -> a.getEntityType() + ": " + a.getEntityName()).setHeader("Объект");
        grid.addColumn(AuditLog::getDetails).setHeader("Детали").setFlexGrow(3);
        grid.addThemeVariants(com.vaadin.flow.component.grid.GridVariant.LUMO_WRAP_CELL_CONTENT);
        GridColumns.configure(grid, "Объект", "Детали");
        grid.setWidthFull(); grid.setHeight("500px");
        Span status = new Span(); Button previous = new Button("Назад"); Button next = new Button("Далее");
        Runnable refresh = () -> {
            try {
                if (from.isInvalid() || to.isInvalid()) throw new IllegalArgumentException("Проверьте период");
                var rows = audit.search(from.getValue(), to.getValue(), username.getValue(), page); grid.setItems(rows);
                previous.setEnabled(page > 0); next.setEnabled(rows.size() == 100); status.setText("Страница " + (page + 1) + ", записей: " + rows.size());
            } catch (IllegalArgumentException exception) { grid.setItems(java.util.List.of()); status.setText(exception.getMessage()); previous.setEnabled(false); next.setEnabled(false); }
        };
        from.addValueChangeListener(e -> { page = 0; refresh.run(); }); to.addValueChangeListener(e -> { page = 0; refresh.run(); });
        username.addValueChangeListener(e -> { page = 0; refresh.run(); });
        previous.addClickListener(e -> { if (page > 0) page--; refresh.run(); }); next.addClickListener(e -> { page++; refresh.run(); });
        HorizontalLayout filters = new HorizontalLayout(from, to, username, new Button("Обновить", e -> refresh.run()));
        filters.setAlignItems(Alignment.BASELINE); filters.addClassName("app-toolbar");
        add(filters, grid, new HorizontalLayout(previous, status, next)); refresh.run();
    }
}
