package ru.sklad.app.views;

import ru.sklad.app.util.GridColumns;
import ru.sklad.app.views.SessionCart.CartItem;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;

import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import ru.sklad.app.entity.Product;
import ru.sklad.app.entity.Sale;
import ru.sklad.app.util.WordExporter;
import ru.sklad.app.util.ColumnVisibilityMenu;
import ru.sklad.app.util.DiscountCalculator;
import ru.sklad.app.repository.ProductRepository;
import ru.sklad.app.service.SalesService;
import ru.sklad.app.service.HeldCartService;
import ru.sklad.app.service.PurchaseOrderService;
import ru.sklad.app.service.ProductAlternativeService;
import ru.sklad.app.service.InsufficientStockException;
import ru.sklad.app.entity.HeldCart;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.icon.VaadinIcon;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Route(value = "sales", layout = MainLayout.class)
@PageTitle("Продажи — Всё сходится!")
@jakarta.annotation.security.RolesAllowed({"ADMIN", "SELLER"})
public class SalesView extends VerticalLayout {
    private final ProductRepository repository;
    private final SessionCart sessionCart;
    private final Map<Long, CartItem> cart;
    private final ComboBox<Product> matches = new ComboBox<>();
    private final Grid<CartItem> grid = ru.sklad.app.util.GridColumns.compact(new Grid<>());
    private final Span total = new Span();
    private final Span mobileTotal = new Span();
    private final Span subtotalText = new Span();
    private final Span discountText = new Span();
    private final NumberField discountValue = new NumberField("Размер скидки");
    private final ComboBox<String> discountType = new ComboBox<>("Тип скидки");
    private final Checkbox applyDiscount = new Checkbox("Применить скидку");
    private final RadioButtonGroup<String> paymentMethod = new RadioButtonGroup<>();
    private boolean validDiscount = true;
    private Sale lastSale;
    private final HeldCartService heldCartService;
    private final PurchaseOrderService purchaseOrders;
    private final ProductAlternativeService alternatives;
    private final Button heldCartsButton = new Button();
    private Dialog alternativeDialog;

    public SalesView(ProductRepository repository, SalesService salesService, ru.sklad.app.service.PermissionService permissions,
                     HeldCartService heldCartService, PurchaseOrderService purchaseOrders, ProductAlternativeService alternatives, SessionCart sessionCart) {
        this.sessionCart = sessionCart; this.cart = sessionCart.getItems();
        this.heldCartService = heldCartService;
        this.repository = repository;
        this.purchaseOrders = purchaseOrders; this.alternatives = alternatives;
        setSizeFull();
        addClassNames("sales-view", "compact-page");
        add(new H3("Продажи — Внутренняя касса"));
        discountValue.setMin(0);
        discountValue.setStep(0.01);
        discountValue.setValue(sessionCart.getDiscountValue());
        discountValue.setValueChangeMode(ValueChangeMode.EAGER);
        discountType.setItems("%", "руб.");
        discountType.setValue(sessionCart.getDiscountType());
        discountType.setRequiredIndicatorVisible(true);
        applyDiscount.setVisible(permissions.hasPermission("canGiveDiscount"));
        discountValue.setVisible(false);
        discountType.setVisible(false);
        applyDiscount.addValueChangeListener(event -> {
            boolean enabled = event.getValue() && permissions.hasPermission("canGiveDiscount");
            discountValue.setVisible(enabled); discountType.setVisible(enabled);
            if (!enabled) { discountValue.setValue(0.0); discountType.setValue("%"); }
            updateTotal();
        });
        applyDiscount.setValue(sessionCart.isDiscountApplied() && permissions.hasPermission("canGiveDiscount"));
        paymentMethod.setLabel("Способ оплаты");
        paymentMethod.setItems("Наличные", "Безнал");
        paymentMethod.setValue(sessionCart.getPaymentMethod());
        paymentMethod.setWidthFull();
        paymentMethod.addValueChangeListener(event -> updateTotal());
        paymentMethod.setRequiredIndicatorVisible(true);
        discountValue.addValueChangeListener(event -> updateTotal());
        discountType.addValueChangeListener(event -> updateTotal());


        matches.setAriaLabel("Поиск товара по названию");
        matches.setPlaceholder("Выберите товар или введите название...");
        matches.setClearButtonVisible(true);
        matches.setItemLabelGenerator(ProductSuggestionsDialog::stockLabel);
        matches.getStyle().set("--vaadin-combo-box-overlay-width", "min(600px, calc(100vw - 32px))");
        matches.setRenderer(new com.vaadin.flow.data.renderer.ComponentRenderer<>(product -> {
            Span label = new Span(ProductSuggestionsDialog.stockLabel(product));
            label.getStyle().set("white-space", "normal").set("overflow-wrap", "anywhere");
            return label;
        }));
        matches.addFocusListener(event -> matches.getDataProvider().refreshAll());
        matches.setPageSize(30);
        matches.setAutoOpen(true);
        matches.addFocusListener(event -> matches.setOpened(true));
        matches.setItems(query -> {
            String term = query.getFilter().orElse("").trim();
            return repository.findByNameContainingIgnoreCase(term,
                    org.springframework.data.domain.PageRequest.of(query.getPage(), query.getPageSize(),
                            org.springframework.data.domain.Sort.by("name", "id"))).stream();
        });
        matches.setWidthFull();
        matches.addValueChangeListener(event -> {
            if (event.isFromClient() && event.getValue() != null) {
                addProduct(event.getValue());
                closeProductSearch();
            }
        });
        discountValue.setWidthFull();
        discountType.setWidthFull();
        Button allProducts = new Button("Все товары", VaadinIcon.LIST.create(), event -> createProductCatalog(repository).open());
        allProducts.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        allProducts.setWidthFull(); applyDiscount.setWidthFull();
        Div productSearch = new Div(matches, allProducts);
        productSearch.addClassName("sales-product-search");
        productSearch.setWidthFull();
        VerticalLayout lookup = new VerticalLayout(productSearch, applyDiscount, discountValue, discountType, paymentMethod);
        lookup.addClassNames("sales-lookup", "app-card");
        lookup.setWidth("320px");
        lookup.setHeightFull();
        lookup.setPadding(false);
        lookup.setSpacing(true);
        lookup.setAlignItems(Alignment.STRETCH);
        grid.addColumn(line -> Objects.toString(line.product.getName(), "")).setHeader("Товар").setKey("product").setFlexGrow(2);
        grid.addComponentColumn(line -> {
            NumberField quantity = new NumberField(); quantity.addThemeVariants(com.vaadin.flow.component.textfield.TextFieldVariant.LUMO_SMALL);
            quantity.setAriaLabel("Количество: " + line.product.getName());
            quantity.setMin(0.01);
            quantity.setMax(99999999.99);
            quantity.setValue(line.quantity.doubleValue());
            quantity.setWidthFull();
            quantity.setValueChangeMode(ValueChangeMode.EAGER);
            quantity.addValueChangeListener(event -> {
                Double value = event.getValue();
                if (quantity.isInvalid() || value == null || !Double.isFinite(value) || value <= 0) {
                    line.valid = false;
                    quantity.setInvalid(true);
                    return;
                }
                BigDecimal amount = BigDecimal.valueOf(value);
                if (amount.stripTrailingZeros().scale() > 2) {
                    line.valid = false;
                    quantity.setInvalid(true);
                    return;
                }
                line.valid = true;
                line.quantity = amount;
                updateTotal();
                grid.getDataProvider().refreshItem(line);
            });
            return quantity;
        }).setHeader("Кол-во").setKey("quantity").setWidth("160px").setFlexGrow(0);
        grid.addColumn(line -> line.price).setHeader("Цена").setKey("price");
        grid.addColumn(CartItem::sum).setHeader("Сумма").setKey("sum");
        grid.addComponentColumn(line -> {
            Button button = ProductSuggestionsDialog.additivesButton(line.product, alternatives, additive -> addProduct(additive));
            button.setText(""); button.setAriaLabel("Добавить добавки: " + line.product.getName());
            return button;
        }).setHeader("Добавки").setKey("additives");
        grid.addComponentColumn(line -> new Button("Удалить", event -> {
            cart.remove(line.product.getId());
            refreshCart();
        })).setHeader("Действия").setKey("actions");
        GridColumns.configure(grid, "Товар");
        grid.addThemeVariants(com.vaadin.flow.component.grid.GridVariant.LUMO_WRAP_CELL_CONTENT);
        grid.getColumnByKey("product").setAutoWidth(false).setWidth("160px").setFlexGrow(1);
        grid.getColumnByKey("quantity").setAutoWidth(false).setWidth("110px").setFlexGrow(0);
        grid.getColumnByKey("price").setAutoWidth(false).setWidth("80px").setFlexGrow(0);
        grid.getColumnByKey("sum").setAutoWidth(false).setWidth("90px").setFlexGrow(0);
        grid.getColumnByKey("additives").setAutoWidth(false).setWidth("90px").setFlexGrow(0);
        grid.getColumnByKey("actions").setAutoWidth(false).setWidth("95px").setFlexGrow(0);
        grid.setWidthFull(); grid.setHeight("50px");
        grid.addClassName("sales-grid");

        HorizontalLayout receipt = WordExporter.downloadButton("Распечатать чек (Word)", "receipt.docx",
                () -> WordExporter.receipt(lastSale));
        receipt.setVisible(false);
        Button printReceipt = new Button("Распечатать чек", event -> {
            if (lastSale != null) getUI().ifPresent(ui -> ui.getPage().open("/print-receipt/" + lastSale.getId(), "_blank"));
        });
        printReceipt.setVisible(false);
        Button conductSale = new Button("Оплатить", event -> {
            if (alternativeDialog != null && alternativeDialog.isOpened()) return;
            if (paymentMethod.getValue() == null) {
                Notification.show("Выберите способ оплаты");
                return;
            }
            updateTotal();
            if (!validDiscount) return;
            if (cart.values().stream().anyMatch(line -> !line.valid)) {
                Notification.show("Проверьте количество товаров в корзине");
                return;
            }
            Map<Long, BigDecimal> quantities = new LinkedHashMap<>();
            Map<Long, BigDecimal> prices = new LinkedHashMap<>();
            cart.forEach((id, line) -> quantities.put(id, line.quantity));
            cart.forEach((id, line) -> prices.put(id, line.price));
            try {
                lastSale = salesService.conductSale(quantities, prices,
                        discountValue.getValue() == null ? BigDecimal.ZERO : BigDecimal.valueOf(discountValue.getValue()),
                        discountType.getValue(), "Наличные".equals(paymentMethod.getValue()) ? "CASH" : "CASHLESS");
                receipt.setVisible(true);
                printReceipt.setVisible(true);

                clearCurrentCart();

                matches.getDataProvider().refreshAll();
                Notification.show("Продажа проведена успешно");

            } catch (InsufficientStockException exception) {
                handleShortages(exception, repository);
            } catch (IllegalArgumentException | ArithmeticException exception) {
                cart.forEach((id, line) -> repository.findById(id).ifPresent(current -> {
                    if (current.getRetailPrice() != null) {
                        line.price = current.getRetailPrice();
                    }
                }));
                refreshCart();
                Notification.show(exception.getMessage());
            }
        });
        conductSale.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        conductSale.addClassName("sales-pay-button");
        Button hold = new Button("Отложить чек", VaadinIcon.PAUSE.create(), event -> {
            if (cart.isEmpty()) { Notification.show("Корзина пуста"); return; }
            if (cart.values().stream().anyMatch(line -> !line.valid)) {
                Notification.show("Проверьте количество товаров в корзине"); return;
            }
            updateTotal(); if (!validDiscount) return;
            Map<Long, BigDecimal> quantities = new LinkedHashMap<>();
            cart.forEach((id, line) -> quantities.put(id, line.quantity));
            try {
                heldCartService.hold(quantities, discountValue.getValue() == null ? BigDecimal.ZERO : BigDecimal.valueOf(discountValue.getValue()),
                        discountType.getValue(), paymentMethod.getValue());
                clearCurrentCart();
                updateHeldCount(); Notification.show("Копия чека отложена");
            } catch (IllegalArgumentException exception) { Notification.show(exception.getMessage()); }
        });
        heldCartsButton.setIcon(VaadinIcon.ARCHIVE.create());
        heldCartsButton.addClickListener(event -> openHeldCarts());
        updateHeldCount();
        mobileTotal.addClassName("sales-mobile-total");
        Div payDock = new Div(mobileTotal, conductSale);
        payDock.addClassName("sales-pay-dock");
        Button clear = new Button("Очистить", event -> clearCurrentCart());

        Div checkout = new Div(payDock, hold, heldCartsButton, clear, printReceipt, receipt);
        checkout.addClassName("sales-checkout");
        Div amounts = new Div(subtotalText, discountText, total);
        amounts.addClassNames("custom-card", "sales-amounts");
        subtotalText.addClassName("metric-label");
        discountText.addClassName("metric-label");
        total.addClassName("metric-value");
        HorizontalLayout cartToolbar = new HorizontalLayout(new H3("Корзина"), ColumnVisibilityMenu.create(grid));
        cartToolbar.addClassName("sales-cart-toolbar");
        cartToolbar.setWidthFull();
        cartToolbar.setPadding(false);
        cartToolbar.setAlignItems(Alignment.CENTER);
        VerticalLayout basket = new VerticalLayout(cartToolbar, grid, amounts, checkout);
        basket.addClassNames("sales-basket", "app-card");
        basket.setSizeFull();
        basket.setPadding(false);
        basket.setAlignItems(Alignment.STRETCH);
        basket.setFlexGrow(0, grid);
        HorizontalLayout content = new HorizontalLayout(lookup, basket);
        content.addClassName("sales-content");
        content.setSizeFull();
        content.setPadding(false);
        content.setSpacing(true);
        content.setAlignItems(Alignment.START);
        content.setFlexGrow(1, basket);
        content.setFlexGrow(0, lookup);
        add(content);
        setFlexGrow(1, content);
        getStyle().set("overflow", "auto");

        refreshCart();

    }

    private void updateHeldCount() {
        long count = heldCartService.count();
        heldCartsButton.setText("Отложенные чеки" + (count > 0 ? " (" + count + ")" : ""));
    }

    private void handleShortages(InsufficientStockException exception, ProductRepository repository) {
        exception.getShortages().forEach(shortage -> Notification.show(shortage.message(), 6000,
                Notification.Position.MIDDLE).addThemeVariants(com.vaadin.flow.component.notification.NotificationVariant.LUMO_ERROR));
        Map<Long, BigDecimal> missing = new LinkedHashMap<>();
        exception.getShortages().forEach(shortage -> missing.merge(shortage.productId(), shortage.missing(), BigDecimal::add));
        // Checkout has already rolled back; this separate service call commits the replenishment draft.
        try { purchaseOrders.addShortages(missing); }
        catch (IllegalArgumentException failure) {
            Notification.show("Не удалось добавить товары в закупки: " + failure.getMessage(), 6000,
                    Notification.Position.MIDDLE).addThemeVariants(com.vaadin.flow.component.notification.NotificationVariant.LUMO_ERROR);
            return;
        }
        Map<Long, BigDecimal> quantities = new LinkedHashMap<>(); cart.forEach((id, line) -> quantities.put(id, line.quantity));
        for (var shortage : exception.getShortages()) {
            var alternative = alternatives.find(shortage.productId(), shortage.missing(), quantities);
            if (alternative.isPresent()) {
                alternativeDialog = createAlternativeDialog(shortage, alternative.get(), repository);
                alternativeDialog.open();
                return;
            }
        }
    }

    private Dialog createAlternativeDialog(InsufficientStockException.Shortage shortage, Product alternative,
                                           ProductRepository repository) {
        Dialog dialog = new Dialog(); dialog.setHeaderTitle("Товара нет на складе");
        dialog.setWidth("540px"); dialog.setMaxWidth("95vw"); dialog.setCloseOnOutsideClick(false);
        String reason = shortage.available().signum() == 0 ? "Товар «" + shortage.name() + "» закончился."
                : "Товара «" + shortage.name() + "» недостаточно.";
        com.vaadin.flow.component.html.Paragraph explanation = new com.vaadin.flow.component.html.Paragraph(
                reason + " Но у нас есть похожий товар:");
        VerticalLayout card = new VerticalLayout(new H3(alternative.getName()),
                new Span("Цена: " + alternative.getRetailPrice().setScale(2, RoundingMode.HALF_UP) + " ₽"),
                new Span("Остаток: " + ru.sklad.app.service.InventoryService.stock(alternative).stripTrailingZeros().toPlainString().replace('.', ',')),
                new Span("Заменить недостающее количество: " + shortage.missing().stripTrailingZeros().toPlainString().replace('.', ',')
                        + " " + Objects.toString(alternative.getUnit(), "")));
        card.addClassName("custom-card"); card.setWidthFull(); card.setSpacing(false);
        dialog.add(explanation, card);
        repository.findById(shortage.productId()).ifPresent(source -> dialog.add(
                ProductSuggestionsDialog.additivesButton(source, alternatives, additive -> addProduct(additive))));
        Button add = new Button("Добавить в корзину", VaadinIcon.PLUS.create(), event -> {
            if (replaceShortage(shortage.productId(), alternative.getId(), repository)) { dialog.close(); closeProductSearch(); }
        });
        add.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(new Button("Отмена", event -> dialog.close()), add);
        return dialog;
    }

    private boolean replaceShortage(Long sourceId, Long alternativeId, ProductRepository repository) {
        CartItem source = cart.get(sourceId);
        Product currentSource = repository.findById(sourceId).orElse(null);
        Product currentAlternative = repository.findById(alternativeId).orElse(null);
        if (source == null || currentSource == null || currentAlternative == null || currentAlternative.getRetailPrice() == null
                || currentAlternative.getRetailPrice().signum() < 0) {
            Notification.show("Состав товаров изменился. Обновите корзину"); return false;
        }
        BigDecimal available = ru.sklad.app.service.InventoryService.stock(currentSource).max(BigDecimal.ZERO);
        BigDecimal missing = source.quantity.subtract(available);
        if (missing.signum() <= 0) {
            Notification.show("Остаток обновился, товара достаточно. Повторите оплату"); return true;
        }
        CartItem existing = cart.get(alternativeId);
        BigDecimal replacementQuantity = missing.add(existing == null ? BigDecimal.ZERO : existing.quantity);
        if (ru.sklad.app.service.InventoryService.stock(currentAlternative).compareTo(replacementQuantity) < 0) {
            Notification.show("Остаток альтернативного товара изменился. Выберите другой товар"); return false;
        }
        if (existing == null) existing = new CartItem(currentAlternative);
        existing.quantity = replacementQuantity; existing.valid = true;
        if (available.signum() == 0) cart.remove(sourceId);
        else { source.quantity = available; source.valid = true; }
        cart.put(alternativeId, existing); closeProductSearch(); refreshCart();
        return true;
    }

    private void openHeldCarts() {
        updateHeldCount();
        Dialog dialog = new Dialog(); dialog.setHeaderTitle("Отложенные чеки");
        dialog.setWidth("800px"); dialog.setMaxWidth("95vw");
        Grid<HeldCart> held = ru.sklad.app.util.GridColumns.compact(new Grid<>(HeldCart.class, false));
        held.addColumn(c -> c.getCreatedAt().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")))
                .setHeader("Время создания").setKey("createdAt");
        held.addColumn(c -> c.getTotalSum().setScale(2, RoundingMode.HALF_UP) + " ₽").setHeader("Сумма").setKey("sum");
        held.addColumn(heldCartService::positionCount).setHeader("Количество позиций").setKey("positions");
        held.addComponentColumn(c -> new Button("Вернуть в корзину", event -> {
            try {
                var restored = heldCartService.restore(c.getId());
                Map<Long, CartItem> replacement = new LinkedHashMap<>();
                restored.items().forEach(item -> {
                    CartItem line = new CartItem(item.product()); line.quantity = item.quantity();
                    replacement.put(item.product().getId(), line);
                });
                cart.clear(); cart.putAll(replacement);
                applyDiscount.setValue(restored.discountValue().signum() > 0);
                discountType.setValue(restored.discountType()); discountValue.setValue(restored.discountValue().doubleValue());
                paymentMethod.setValue(restored.paymentMethod()); refreshCart(); updateHeldCount(); dialog.close();
                Notification.show("Чек возвращен в корзину. Проверьте итог: применены текущие цены и права на скидку");
            } catch (IllegalArgumentException exception) {
                held.setItems(heldCartService.list()); updateHeldCount(); Notification.show(exception.getMessage());
            }
        })).setHeader("Действие").setKey("restore").setAutoWidth(true);
        GridColumns.configure(held);
        held.setItems(heldCartService.list()); held.setWidthFull(); held.setHeight("360px");
        dialog.add(new com.vaadin.flow.component.html.Paragraph(
                "Возврат заменит текущую корзину. Если она нужна, сначала отложите ее. Цены при возврате обновляются."), held);
        dialog.getFooter().add(new Button("Закрыть", event -> dialog.close())); dialog.open();
    }

    private Dialog createProductCatalog(ProductRepository repository) {
        Dialog dialog = new Dialog(); dialog.setHeaderTitle("Все товары"); dialog.setModal(true);
        dialog.setWidth("calc(100vw - 64px)"); dialog.setMaxWidth("calc(100vw - 64px)");
        dialog.setHeight("min(720px, 88dvh)");
        dialog.setCloseOnOutsideClick(false);
        TextField search = new TextField(); search.setAriaLabel("Поиск по названию товара");
        search.setPlaceholder("Поиск по названию товара..."); search.setPrefixComponent(VaadinIcon.SEARCH.create());
        search.setClearButtonVisible(true); search.setWidthFull(); search.setValueChangeMode(ValueChangeMode.LAZY);
        Grid<Product> catalog = ru.sklad.app.util.GridColumns.compact(new Grid<>(Product.class, false)); catalog.setId("sales-product-catalog");
        catalog.addColumn(Product::getName).setHeader("Наименование").setKey("name");
        catalog.addColumn(Product::getCategory).setHeader("Категория").setKey("category");
        catalog.addColumn(p -> (p.getStockQuantity() == null ? BigDecimal.ZERO : p.getStockQuantity())
                .stripTrailingZeros().toPlainString().replace('.', ',') + " " + Objects.toString(p.getUnit(), ""))
                .setHeader("Остаток").setKey("stock");
        catalog.addColumn(p -> p.getRetailPrice() == null ? "—" : p.getRetailPrice().setScale(2, RoundingMode.HALF_UP) + " ₽")
                .setHeader("Цена").setKey("price");
        Span added = new Span(); added.getElement().setAttribute("aria-live", "polite");
        Span selectionCount = new Span(); selectionCount.addClassName("sales-catalog-count");
        selectionCount.getElement().setAttribute("aria-live", "polite");
        Runnable updateSelection = () -> selectionCount.setText("В корзине: " + cart.size() + " поз. · "
                + cart.values().stream().map(line -> line.quantity).reduce(BigDecimal.ZERO, BigDecimal::add)
                        .stripTrailingZeros().toPlainString().replace('.', ',') + " ед.");
        java.util.function.Consumer<Product> add = selected -> {
            Product current = selected == null ? null : repository.findById(selected.getId()).orElse(null);
            if (addProduct(current)) {
                added.setText("Добавлено: " + current.getName());
                catalog.getDataProvider().refreshAll();
                updateSelection.run();
            }
        };
        catalog.addComponentColumn(p -> {
            CartItem line = cart.get(p.getId());
            if (line == null) {
                Div action = new Div(new Button("Добавить", VaadinIcon.PLUS.create(), event -> add.accept(p)));
                action.addClassName("sales-catalog-actions");
                return action;
            }
            Button plus = new Button(VaadinIcon.PLUS.create(), event -> add.accept(p));
            plus.setAriaLabel("Увеличить количество: " + p.getName());
            Span quantity = new Span(line.quantity.stripTrailingZeros().toPlainString().replace('.', ','));
            quantity.addClassName("sales-catalog-quantity");
            Button minus = new Button(VaadinIcon.MINUS.create(), event -> {
                CartItem current = cart.get(p.getId());
                if (current == null) return;
                BigDecimal remaining = current.quantity.subtract(BigDecimal.ONE);
                if (remaining.signum() <= 0) cart.remove(p.getId());
                else { current.quantity = remaining; current.valid = true; }
                refreshCart(); updateSelection.run(); catalog.getDataProvider().refreshAll();
                added.setText("Количество изменено: " + p.getName());
            });
            minus.setAriaLabel("Уменьшить количество: " + p.getName());
            Button remove = new Button(VaadinIcon.TRASH.create(), event -> {
                cart.remove(p.getId()); refreshCart(); updateSelection.run(); catalog.getDataProvider().refreshAll();
                added.setText("Удалено: " + p.getName());
            });
            remove.addThemeVariants(ButtonVariant.LUMO_ERROR);
            remove.setAriaLabel("Удалить из корзины: " + p.getName());
            Div actions = new Div(plus, quantity, minus, remove);
            actions.addClassName("sales-catalog-actions");
            return actions;
        })
                .setHeader("Действия").setKey("add");
        catalog.addItemDoubleClickListener(event -> {
            if (event.getItem() != null && (event.getColumn() == null || !"add".equals(event.getColumn().getKey())))
                add.accept(event.getItem());
        });
        GridColumns.configure(catalog, "Наименование");
        // A bounded name column leaves room for prices and the complete quantity controls.
        catalog.getColumnByKey("name").setAutoWidth(false).setWidth("240px").setFlexGrow(1);
        catalog.getColumnByKey("category").setAutoWidth(false).setWidth("140px").setFlexGrow(0);
        catalog.getColumnByKey("stock").setAutoWidth(true).setFlexGrow(0);
        catalog.getColumnByKey("price").setAutoWidth(true).setFlexGrow(0);
        catalog.getColumnByKey("add").setAutoWidth(true).setFlexGrow(0);
        catalog.setWidthFull(); catalog.setHeight("65vh");
        Runnable refresh = () -> catalog.setItems(repository.findByNameContainingIgnoreCase(search.getValue().trim()).stream()
                .sorted(java.util.Comparator.comparing(Product::getName, String.CASE_INSENSITIVE_ORDER).thenComparing(Product::getId)).toList());
        search.addValueChangeListener(event -> refresh.run());
        VerticalLayout content = new VerticalLayout(search, catalog, added);
        content.setPadding(false); content.setSizeFull(); content.addClassName("sales-catalog-content");
        content.setFlexGrow(0, catalog); dialog.add(content);
        Button done = new Button("Готово", VaadinIcon.CHECK.create(), event -> dialog.close());
        done.setId("sales-catalog-done"); done.addClassName("sales-catalog-done");
        done.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_LARGE); done.setWidthFull();
        done.setAriaLabel("Готово, закрыть выбор товаров");
        VerticalLayout footer = new VerticalLayout(selectionCount, done);
        footer.setPadding(false); footer.setSpacing(false); footer.setWidthFull(); footer.addClassName("sales-catalog-footer");
        dialog.getFooter().add(footer); refresh.run(); updateSelection.run();
        return dialog;
    }

    private void updateCatalogButton(Button button, Product product) {
        CartItem line = cart.get(product.getId());
        if (line == null) {
            button.setText("Добавить в корзину"); button.setIcon(VaadinIcon.PLUS.create());
            button.removeThemeVariants(ButtonVariant.LUMO_PRIMARY);
            button.setAriaLabel("Добавить в корзину: " + product.getName());
            button.setTooltipText("Добавить одну единицу");
        } else {
            String quantity = line.quantity.stripTrailingZeros().toPlainString().replace('.', ',');
            button.setText("В корзине: " + quantity); button.setIcon(VaadinIcon.CHECK.create());
            button.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            button.setAriaLabel(product.getName() + ", в корзине " + quantity + ". Добавить еще одну единицу");
            button.setTooltipText("Добавить еще одну единицу");
        }
    }

    private boolean addProduct(Product product) {
        closeProductSearch();
        if (product == null || product.getId() == null) {
            Notification.show("Товар не найден");
            return false;
        }
        product = repository.findById(product.getId()).orElse(null);
        if (product == null) { Notification.show("Товар больше не существует"); return false; }
        if (product.getRetailPrice() == null || product.getRetailPrice().signum() < 0) {
            Notification.show("У товара не указана корректная розничная цена"); return false;
        }
        BigDecimal stock = ru.sklad.app.service.InventoryService.stock(product);
        java.util.Map<Long, BigDecimal> quantities = new java.util.LinkedHashMap<>();
        cart.forEach((id, line) -> quantities.put(id, line.quantity));
        if (stock.signum() <= 0) {
            CartItem line = cart.get(product.getId());
            if (line == null) {
                line = new CartItem(product);
                cart.put(product.getId(), line);
            } else {
                line.quantity = line.quantity.add(BigDecimal.ONE);
            }
            line.valid = true;
            refreshCart();
            quantities.put(product.getId(), line.quantity);
            var replacement = alternatives.find(product.getId(), line.quantity, quantities);
            if (replacement.isPresent()) {
                var shortage = new InsufficientStockException.Shortage(product.getId(), product.getName(),
                        BigDecimal.ZERO, line.quantity);
                if (alternativeDialog != null) alternativeDialog.close();
                alternativeDialog = createAlternativeDialog(shortage, replacement.get(), repository);
                alternativeDialog.open();
            } else {
                Notification.show("Товар закончился")
                        .addThemeVariants(com.vaadin.flow.component.notification.NotificationVariant.LUMO_ERROR);
            }
            return true;
        }
        BigDecimal already = quantities.getOrDefault(product.getId(), BigDecimal.ZERO);
        if (already.compareTo(stock) >= 0) { Notification.show("В корзине уже весь доступный остаток"); return false; }
        if (product.getRetailPrice() == null) {
            Notification.show("У товара не указана розничная цена");
            return false;
        }
        CartItem existing = cart.get(product.getId());
        if (existing == null) {
            CartItem line = new CartItem(product); line.quantity = BigDecimal.ONE.min(stock);
            cart.put(product.getId(), line);
        } else {
            existing.quantity = existing.quantity.add(BigDecimal.ONE).min(stock);
            existing.valid = true;
        }
        refreshCart();
        return true;
    }

    private void clearCurrentCart() {
        cart.clear(); // This is the same map held by SessionCart.
        applyDiscount.setValue(false);
        discountValue.setValue(0.0);
        discountValue.setInvalid(false);
        discountType.setValue("%");
        paymentMethod.setValue("Наличные");
        if (alternativeDialog != null) alternativeDialog.close();
        closeProductSearch();
        refreshCart();
    }

    private void refreshCart() {
        grid.setItems(new ArrayList<>(cart.values()));
        updateTotal();
    }

    private void updateTotal() {
        GridColumns.updateGridHeight(grid);
        sessionCart.settings(discountValue.getValue() == null ? 0 : discountValue.getValue(),
                discountType.getValue(), applyDiscount.getValue(), paymentMethod.getValue());
        BigDecimal sum = cart.values().stream().map(CartItem::sum).reduce(BigDecimal.ZERO, BigDecimal::add);
        subtotalText.setText("Сумма без скидки: " + sum.setScale(2, RoundingMode.HALF_UP) + " ₽");
        try {
            Double value = discountValue.getValue();
            if (value != null && !Double.isFinite(value)) throw new IllegalArgumentException("Введите корректную скидку");
            if (value == null && discountValue.isInvalid()) throw new IllegalArgumentException("Введите корректную скидку");
            BigDecimal discount = DiscountCalculator.discount(sum, value == null ? BigDecimal.ZERO : BigDecimal.valueOf(value),
                    discountType.getValue());
            discountValue.setInvalid(false);
            validDiscount = true;
            discountText.setText("Скидка: " + discount + " ₽");
            total.setText("К оплате: " + sum.subtract(discount).setScale(2, RoundingMode.HALF_UP) + " ₽");
        } catch (IllegalArgumentException exception) {
            validDiscount = false;
            discountValue.setErrorMessage(exception.getMessage());
            discountValue.setInvalid(true);
            discountText.setText("Скидка: —");
            total.setText("К оплате: —");
        }
        mobileTotal.setText(total.getText());
    }

    private void closeProductSearch() {
        matches.setOpened(false); matches.clear(); matches.blur();
    }
}
