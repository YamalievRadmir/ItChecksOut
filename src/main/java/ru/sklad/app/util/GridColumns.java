package ru.sklad.app.util;

import com.vaadin.flow.component.grid.Grid;
import java.util.Set;

/** Shared column sizing, applied after all columns have been created. */
public final class GridColumns {
    private GridColumns() {}

    public static void configure(Grid<?> grid, String... flexibleHeaders) {
        Set<String> flexible = Set.of(flexibleHeaders);
        // Keep row scrolling inside the bounded Grid, with its native sticky header.
        grid.setAllRowsVisible(false);
        grid.setColumnReorderingAllowed(true);
        grid.getColumns().forEach(column -> {
            column.setResizable(true);
            column.setAutoWidth(true);
            String header = column.getHeaderText();
            column.setFlexGrow(header != null && flexible.contains(header) ? 1 : 0);
        });
    }
}
