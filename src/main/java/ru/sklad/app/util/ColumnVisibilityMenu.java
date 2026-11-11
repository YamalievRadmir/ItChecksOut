package ru.sklad.app.util;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.contextmenu.ContextMenu;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.icon.VaadinIcon;

public final class ColumnVisibilityMenu {
    private ColumnVisibilityMenu() {}

    public static Button create(Grid<?> grid) {
        Button button = new Button(VaadinIcon.COG.create());
        button.setAriaLabel("Настройка видимости колонок");
        button.setTooltipText("Настроить колонки");
        ContextMenu menu = new ContextMenu(button);
        menu.setOpenOnClick(true);
        for (Grid.Column<?> column : grid.getColumns()) {
            String key = column.getKey();
            if (key == null) throw new IllegalArgumentException("У колонки должен быть key");
            var item = menu.addItem(column.getHeaderText(), event ->
                    grid.getColumnByKey(key).setVisible(event.getSource().isChecked()));
            item.setCheckable(true);
            item.setChecked(column.isVisible());
            menu.addOpenedChangeListener(event -> {
                if (event.isOpened()) item.setChecked(column.isVisible());
            });
        }
        return button;
    }
}
