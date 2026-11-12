package ru.sklad.app.util;

import com.vaadin.flow.component.grid.Grid;
import java.util.Set;

/** Shared column sizing, applied after all columns have been created. */
public final class GridColumns {
    private GridColumns() {}

    public static <T> Grid<T> compact(Grid<T> grid) {
        grid.addThemeVariants(com.vaadin.flow.component.grid.GridVariant.LUMO_COMPACT);
        grid.setAllRowsVisible(false);
        grid.setWidthFull();
        grid.setHeight("65vh");
        grid.setMinHeight("0");
        grid.setMaxHeight("65vh");
        return grid;
    }

    public static void updateGridHeight(Grid<?> grid) {
        int rows = grid.getListDataView().getItemCount();
        long height = Math.min(500L, 50L + 40L * rows);
        grid.addClassName("dynamic-height-grid");
        grid.getStyle().set("--dynamic-grid-height", height + "px");
        grid.setHeight(height + "px");
        grid.setMinHeight("0");
        grid.setMaxHeight("min(500px, 60vh)");
    }

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
