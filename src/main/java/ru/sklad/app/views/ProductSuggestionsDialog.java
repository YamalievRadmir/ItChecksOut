package ru.sklad.app.views;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.combobox.ComboBox;
import ru.sklad.app.entity.Product;
import ru.sklad.app.service.ProductAlternativeService;
import ru.sklad.app.service.InventoryService;
import java.util.List;
import java.util.function.Consumer;

/** Shared selection UI; callers recheck current prices and stock on every addition. */
final class ProductSuggestionsDialog {
    private ProductSuggestionsDialog() {}
    static String stockLabel(Product p) {
        if (p == null) return "";
        var stock = InventoryService.stock(p).max(java.math.BigDecimal.ZERO);
        String unit = java.util.Objects.toString(p.getUnit(), "").trim();
        return java.util.Objects.toString(p.getName(), "") + " (Остаток: "
                + stock.stripTrailingZeros().toPlainString().replace('.', ',')
                + (unit.isEmpty() ? "" : " " + unit) + ")" + (stock.signum() == 0 ? " (Нет в наличии)" : "");
    }
    static Button additivesButton(Product source, ProductAlternativeService service, Consumer<Product> add) {
        List<Product> additives = service.additives(source.getId());
        Button button = new Button("Добавить добавки", event -> {
            Dialog dialog = new Dialog(); dialog.setHeaderTitle("Рекомендуемые добавки: " + source.getName());
            dialog.setWidth("600px"); dialog.setMaxWidth("95vw");
            VerticalLayout rows = new VerticalLayout();
            for (Product p : service.additives(source.getId())) {
                rows.add(new Span(p.getName() + " · остаток: " + InventoryService.stock(p)),
                        new Button("Добавить в корзину", e -> add.accept(p)));
            }
            dialog.add(rows); dialog.getFooter().add(new Button("Готово", e -> dialog.close())); dialog.open();
        });
        button.setIcon(com.vaadin.flow.component.icon.VaadinIcon.PLUS_CIRCLE.create());
        button.addThemeVariants(com.vaadin.flow.component.button.ButtonVariant.LUMO_SMALL,
                com.vaadin.flow.component.button.ButtonVariant.LUMO_TERTIARY);
        button.setTooltipText("Добавить рекомендуемые добавки к товару «" + source.getName() + "»");
        button.setVisible(!additives.isEmpty());
        return button;
    }
    static void missing(Product source, List<Product> choices, ProductAlternativeService service,
                        Consumer<Product> add, Consumer<Product> addAdditive) {
        if (choices.isEmpty()) {
            com.vaadin.flow.component.notification.Notification.show("Товар закончился")
                    .addThemeVariants(com.vaadin.flow.component.notification.NotificationVariant.LUMO_ERROR);
            return;
        }
        Dialog dialog = new Dialog(); dialog.setHeaderTitle("Товара нет на складе");
        dialog.setWidth("600px"); dialog.setMaxWidth("95vw");
        ComboBox<Product> choice = new ComboBox<>("Поиск альтернативы");
        choice.setItemLabelGenerator(ProductSuggestionsDialog::stockLabel);
        choice.setItems(choices); choice.setWidthFull(); choice.setValue(choices.get(0));
        dialog.add(new Span("Товар «" + source.getName() + "» закончился. Выберите замену."), choice,
                additivesButton(source, service, addAdditive));
        dialog.getFooter().add(new Button("Отмена", e -> dialog.close()), new Button("Добавить в корзину", e -> {
            if (choice.getValue() != null) { add.accept(choice.getValue()); dialog.close(); }
        }));
        dialog.open();
    }
}
