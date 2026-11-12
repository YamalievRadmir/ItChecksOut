package ru.sklad.app.views;

import com.vaadin.flow.component.button.*;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.*;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.data.value.ValueChangeMode;
import org.springframework.security.access.AccessDeniedException;
import ru.sklad.app.service.ReceiptService;
import ru.sklad.app.util.GridColumns;
import java.math.*;
import java.time.format.DateTimeFormatter;
import java.text.NumberFormat;
import java.util.*;

/** Receipt snapshots are edited locally; only Save changes the database and stock. */
public class ReceiptDialog extends Dialog {
    private final ReceiptService service;
    private final String number;
    private final Runnable onSaved;
    private ReceiptService.Receipt receipt;
    private boolean editing;
    private final List<Row> rows = new ArrayList<>();
    private final Map<Row, Span> sums = new IdentityHashMap<>();
    private final VerticalLayout body = new VerticalLayout();
    private final Span subtotal = new Span(), discountText = new Span(), payable = new Span(), paymentText = new Span();
    private BigDecimalField discount;
    private ComboBox<String> payment;

    public ReceiptDialog(ReceiptService service, Long id, String number, Runnable onSaved) {
        this.service = service; this.number = number; this.onSaved = onSaved;
        receipt = service.get(id);
        setWidth("1000px"); setMaxWidth("calc(100vw - 48px)"); setCloseOnOutsideClick(false);
        body.setPadding(false); body.setWidthFull(); add(body); render();
    }

    private void render() {
        setHeaderTitle("Чек № " + number + " от " + receipt.date().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")));
        body.removeAll(); getFooter().removeAll(); rows.clear(); sums.clear();
        receipt.lines().forEach(line -> rows.add(new Row(line)));
        Grid<Row> grid = ru.sklad.app.util.GridColumns.compact(new Grid<>()); grid.setId("receipt-items");
        grid.addColumn(row -> row.name).setHeader("Наименование");
        if (editing) {
            grid.addComponentColumn(row -> field(row, false)).setHeader("Количество").setWidth("150px");
            grid.addComponentColumn(row -> field(row, true)).setHeader("Цена").setWidth("160px");
        } else {
            grid.addColumn(row -> row.quantity.stripTrailingZeros().toPlainString() + " " + Objects.toString(row.unit, ""))
                    .setHeader("Количество");
            grid.addColumn(row -> money(row.price)).setHeader("Цена");
        }
        grid.addComponentColumn(row -> {
            Span sum = new Span(money(row.sum())); sums.put(row, sum); return sum;
        }).setHeader("Сумма");
        if (editing) grid.addComponentColumn(row -> {
            Button remove = new Button(VaadinIcon.TRASH.create(), event -> {
                rows.remove(row); sums.clear(); grid.getDataProvider().refreshAll(); GridColumns.updateGridHeight(grid); updateTotals();
            });
            remove.addThemeVariants(ButtonVariant.LUMO_ERROR); remove.setAriaLabel("Удалить: " + row.name); return remove;
        }).setHeader("Удалить");
        GridColumns.configure(grid, "Наименование"); grid.setItems(rows);
        grid.setWidthFull(); GridColumns.updateGridHeight(grid); body.add(grid);
        discount = new BigDecimalField("Скидка, ₽"); discount.setValue(receipt.discount());
        discount.setValueChangeMode(ValueChangeMode.EAGER);
        payment = new ComboBox<>("Способ оплаты"); payment.setItems("CASH", "CASHLESS");
        payment.setItemLabelGenerator(ReceiptDialog::paymentLabel); payment.setValue(receipt.payment());
        if (editing) {
            ComboBox<ReceiptService.Choice> product = new ComboBox<>("Добавить товар");
            product.setItems(service.choices()); product.setItemLabelGenerator(ReceiptService.Choice::name);
            product.setWidth("min(400px, 100%)"); product.setClearButtonVisible(true);
            Button add = new Button("Добавить", VaadinIcon.PLUS.create(), event -> {
                var selected = product.getValue();
                if (selected == null) { Notification.show("Выберите товар"); return; }
                Row existing = rows.stream().filter(row -> row.productId.equals(selected.id())).findFirst().orElse(null);
                if (existing == null) rows.add(new Row(selected));
                else existing.quantity = (existing.quantity == null ? BigDecimal.ZERO : existing.quantity).add(BigDecimal.ONE);
                product.clear(); sums.clear(); grid.getDataProvider().refreshAll(); updateTotals();
            });
            HorizontalLayout addToolbar = new HorizontalLayout(product, add); addToolbar.setAlignItems(FlexComponent.Alignment.END);
            addToolbar.setWidthFull();
            addToolbar.addClassName("app-toolbar"); body.addComponentAsFirst(addToolbar);
            HorizontalLayout fields = new HorizontalLayout(discount, payment); fields.addClassName("app-toolbar"); body.add(fields);
        }
        discount.addValueChangeListener(event -> updateTotals());
        VerticalLayout totals = new VerticalLayout(subtotal, discountText, new H3(payable), paymentText);
        totals.setPadding(false); totals.setSpacing(false); body.add(totals); updateTotals();
        Button print = new Button("Печать чека", VaadinIcon.PRINT.create(), event ->
                getUI().ifPresent(ui -> ui.getPage().open("/print-receipt/" + receipt.id(), "_blank")));
        print.setEnabled(!editing); getFooter().add(print);
        if (editing) {
            Button cancel = new Button("Отменить изменения", event -> reload());
            Button save = new Button("Сохранить", event -> save()); save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            getFooter().add(cancel, save);
        } else if (receipt.editable()) {
            getFooter().add(new Button("Редактировать чек", VaadinIcon.EDIT.create(), event -> {
                try {
                    receipt = service.get(receipt.id());
                    if (!receipt.editable()) { Notification.show("У вас нет прав на редактирование чеков"); return; }
                    editing = true; render();
                } catch (IllegalArgumentException | AccessDeniedException exception) { Notification.show(exception.getMessage()); }
            }));
        }
        getFooter().add(new Button("Закрыть", event -> close()));
    }

    private BigDecimalField field(Row row, boolean price) {
        BigDecimalField field = new BigDecimalField(); field.setWidthFull();
        field.setAriaLabel((price ? "Цена: " : "Количество: ") + row.name);
        field.setValue(price ? row.price : row.quantity); field.setValueChangeMode(ValueChangeMode.EAGER);
        field.addValueChangeListener(event -> {
            if (price) row.price = field.isInvalid() ? null : event.getValue();
            else row.quantity = field.isInvalid() ? null : event.getValue();
            Span sum = sums.get(row); if (sum != null) sum.setText(money(row.sum())); updateTotals();
        });
        return field;
    }

    private void updateTotals() {
        BigDecimal total = rows.stream().map(Row::sum).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal deduction = discount.getValue();
        boolean valid = rows.stream().allMatch(row -> row.quantity != null && row.quantity.signum() > 0
                && row.price != null && row.price.signum() >= 0) && deduction != null && !discount.isInvalid()
                && deduction.signum() >= 0 && deduction.compareTo(total) <= 0;
        subtotal.setText("Общая сумма: " + money(total));
        discountText.setVisible(deduction != null && deduction.signum() != 0);
        discountText.setText("Скидка: " + money(deduction == null ? BigDecimal.ZERO : deduction));
        payable.setText(valid ? "Итог: " + money(total.subtract(deduction)) : "Проверьте количество, цены и скидку");
        paymentText.setVisible(!editing); paymentText.setText("Способ оплаты: " + paymentLabel(receipt.payment()));
    }

    private void save() {
        try {
            if (discount.isInvalid()) throw new IllegalArgumentException("Проверьте скидку");
            receipt = service.update(receipt.id(), receipt.revision(), rows.stream()
                    .map(row -> new ReceiptService.Input(row.itemId, row.productId, row.quantity, row.price)).toList(),
                    discount.getValue(), payment.getValue());
            editing = false; render(); onSaved.run(); Notification.show("Чек обновлен");
        } catch (IllegalArgumentException | ArithmeticException exception) {
            Notification.show(exception.getMessage());
        } catch (AccessDeniedException exception) { Notification.show("У вас нет прав на редактирование чеков"); }
    }

    private void reload() {
        try { receipt = service.get(receipt.id()); editing = false; render(); }
        catch (IllegalArgumentException exception) { Notification.show(exception.getMessage()); }
    }

    private static String paymentLabel(String value) { return "CASHLESS".equals(value) ? "Безнал" : "Наличные"; }
    private static String money(BigDecimal value) { return NumberFormat.getCurrencyInstance(Locale.forLanguageTag("ru-RU")).format(value); }
    private static final class Row {
        final Long itemId, productId;
        final String name, unit;
        BigDecimal quantity, price;
        Row(ReceiptService.Line line) {
            itemId = line.itemId(); productId = line.productId(); name = line.name(); unit = line.unit();
            quantity = line.quantity(); price = line.price();
        }
        Row(ReceiptService.Choice choice) {
            itemId = null; productId = choice.id(); name = choice.name(); unit = "";
            quantity = BigDecimal.ONE; price = choice.price() == null ? BigDecimal.ZERO : choice.price();
        }
        BigDecimal sum() { return quantity == null || price == null ? BigDecimal.ZERO : quantity.multiply(price).setScale(2, RoundingMode.HALF_UP); }
    }
}
