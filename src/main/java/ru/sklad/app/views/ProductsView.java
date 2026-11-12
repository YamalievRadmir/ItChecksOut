package ru.sklad.app.views;

import ru.sklad.app.util.GridColumns;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import org.springframework.security.core.Authentication;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.context.SecurityContextHolder;
import ru.sklad.app.entity.Product;
import ru.sklad.app.repository.ProductRepository;
import ru.sklad.app.util.ExcelExporter;
import ru.sklad.app.util.ColumnVisibilityMenu;
import ru.sklad.app.util.RoleAccess;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Route(value = "products", layout = MainLayout.class)
@PageTitle("Товары и услуги — Всё сходится!")
@jakarta.annotation.security.RolesAllowed({"ADMIN", "SELLER"})
public class ProductsView extends VerticalLayout {

    private final ru.sklad.app.service.ProductFileImporter fileImporter;
    private final ProductRepository repository;
    private final ru.sklad.app.service.ProductService productService;
    private final ru.sklad.app.service.PermissionService permissions;
    private final Grid<Product> grid = ru.sklad.app.util.GridColumns.compact(new Grid<>(Product.class, false));
    private List<Product> displayedProducts = List.of();

    public ProductsView(ProductRepository repository, ru.sklad.app.service.PermissionService permissions,
                        ru.sklad.app.service.ProductService productService, ru.sklad.app.service.ProductFileImporter fileImporter) {
        this.fileImporter = fileImporter;
        this.repository = repository;
        this.productService = productService;
        this.permissions = permissions;
        setSizeFull();
        addClassNames("products-view", "compact-page");
        add(new H3("Товары и услуги"));

        TextField searchField = new TextField();
        searchField.setPlaceholder("Введите название, категорию или код...");
        searchField.setAriaLabel("Поиск товаров");
        searchField.setPrefixComponent(VaadinIcon.SEARCH.create());
        searchField.setClearButtonVisible(true);
        searchField.setWidthFull();
        searchField.setValueChangeMode(ValueChangeMode.LAZY);
        searchField.addValueChangeListener(event -> {
            String searchTerm = event.getValue().trim();
            if (searchTerm.isEmpty()) {
                updateGrid();
            } else {
                showProducts(repository.findByNameContainingIgnoreCaseOrCategoryContainingIgnoreCase(
                        searchTerm, searchTerm));
            }
        });

        Button addButton = new Button("Товар", VaadinIcon.PLUS.create());
        addButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        addButton.setVisible(permissions.hasPermission("canCreateProduct"));
        addButton.addClickListener(event -> openProductDialog(new Product()));
        addButton.getStyle().set("flex-shrink", "0");

        HorizontalLayout export = ExcelExporter.downloadButton("Экспорт в Excel", "products.xlsx",
                () -> {
                    List<ExcelExporter.Column<Product>> columns = new ArrayList<>(List.of(
                        new ExcelExporter.Column<Product>("Наименование", Product::getName),
                        new ExcelExporter.Column<Product>("Категория", Product::getCategory),
                        new ExcelExporter.Column<Product>("Ед. изм.", Product::getUnit),
                        new ExcelExporter.Column<Product>("Остаток", Product::getStockQuantity),
                        new ExcelExporter.Column<Product>("Цена розница", Product::getRetailPrice)));
                    if (permissions.hasPermission("canSeePurchasePrice")) columns.add(4, new ExcelExporter.Column<>("Цена закупа", Product::getPurchasePrice));
                    return ExcelExporter.export("Товары", columns, displayedProducts);
                });
        Button importButton = new Button("Загрузить из файла", VaadinIcon.UPLOAD.create(), event -> openImportDialog());
        importButton.setVisible(isAdmin());
        Button clearButton = new Button("Очистить склад", VaadinIcon.TRASH.create(), event -> {
            var confirm = new com.vaadin.flow.component.confirmdialog.ConfirmDialog();
            confirm.setHeader("Очистить склад");
            confirm.setText("Вы уверены, что хотите удалить ВСЕ товары из этого магазина? Это действие необратимо");
            confirm.setCancelable(true); confirm.setCancelText("Отмена"); confirm.setConfirmText("Удалить");
            confirm.setConfirmButtonTheme("error primary");
            confirm.addConfirmListener(click -> {
                try { productService.clearWarehouse(); updateGrid(); Notification.show("Склад очищен"); }
                catch (DataIntegrityViolationException ex) { Notification.show("Товары связаны с продажами, поставками или заказами. Очистка отменена, история сохранена."); }
            }); confirm.open();
        });
        clearButton.setVisible(isAdmin()); clearButton.addThemeVariants(ButtonVariant.LUMO_ERROR);        HorizontalLayout toolbar = new HorizontalLayout(searchField, addButton, importButton, clearButton, export);
        toolbar.setWidthFull();
        toolbar.addClassName("app-toolbar");
        toolbar.setAlignItems(Alignment.CENTER);
        toolbar.setFlexGrow(1, searchField);
        add(toolbar);

        grid.addColumn(product -> Objects.toString(product.getName(), ""))
                .setHeader("Наименование").setKey("name").setAutoWidth(true).setFlexGrow(1);
        grid.addColumn(product -> Objects.toString(product.getCategory(), "")).setHeader("Категория").setKey("category");
        grid.addColumn(product -> (Objects.toString(product.getStockQuantity(), "") + " "
                + Objects.toString(product.getUnit(), "")).trim())
                .setHeader("Остаток").setKey("stockQuantity");
        grid.addColumn(product -> Objects.toString(product.getRetailPrice(), "")).setHeader("Цена (розница)").setKey("retailPrice");
        if (permissions.hasPermission("canSeePurchasePrice")) {
            grid.addColumn(Product::getPurchasePrice).setHeader("Цена закупа").setKey("purchasePrice");
        }
        Grid.Column<Product> barcodeColumn = grid.addColumn(product -> Objects.toString(product.getBarcode(), ""))
                .setHeader("Штрихкод").setKey("barcode").setAutoWidth(true);
        barcodeColumn.setVisible(false);
        grid.addColumn(product -> Objects.toString(product.getAnimalType(), ""))
                .setHeader("Вид животного").setKey("animalType").setAutoWidth(true).setVisible(false);
        grid.addColumn(product -> Objects.toString(product.getAge(), ""))
                .setHeader("Возраст").setKey("age").setAutoWidth(true).setVisible(false);
        toolbar.addComponentAtIndex(1, ColumnVisibilityMenu.create(grid));
        grid.addItemDoubleClickListener(event -> openProductDialog(event.getItem()));

        updateGrid();
        GridColumns.configure(grid, "Наименование", "Категория");
        grid.setWidthFull(); grid.setHeight("65vh");
        var card = ru.sklad.app.util.AppCards.table(grid);
        add(card);
        setFlexGrow(1, card);
    }

    private void openImportDialog() {
        if (!isAdmin()) return;
        Dialog dialog = new Dialog(); dialog.setHeaderTitle("Загрузка товаров из Excel");
        dialog.setWidth("850px"); dialog.setMaxWidth("95vw"); dialog.setModal(true);
        var buffer = new com.vaadin.flow.component.upload.receivers.MemoryBuffer();
        var upload = new com.vaadin.flow.component.upload.Upload(buffer);
        upload.setAcceptedFileTypes(".xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        upload.setMaxFiles(1); upload.setMaxFileSize(20 * 1024 * 1024); upload.setDropAllowed(true);
        upload.setWidthFull();
        var hint = new com.vaadin.flow.component.html.Paragraph("Требования к файлу: формат .xlsx. Первая строка должна содержать заголовки колонок: «Наименование», «Цена продажи», «Остаток». Порядок колонок не важен.");
        upload.setDropLabel(new com.vaadin.flow.component.html.Span("Перетащите Excel файл (.xlsx) сюда"));
        List<Product> pending = new ArrayList<>();
        Grid<Product> preview = new Grid<>(Product.class, false);
        preview.addColumn(Product::getName).setHeader("Наименование");
        preview.addColumn(Product::getRetailPrice).setHeader("Цена продажи");
        preview.addColumn(Product::getStockQuantity).setHeader("Остаток");
        preview.setWidthFull(); preview.setHeight("320px"); preview.setVisible(false);
        Button confirmImport = new Button("Подтвердить импорт", click -> {
            try {
                var result = fileImporter.savePreview(pending);
                dialog.close(); updateGrid(); Notification.show("Успешно обработано строк: " + result.processedRows()
                    + (result.zeroPriceRows() > 0 ? ". С нулевой ценой: " + result.zeroPriceRows() : ""));
            } catch (Exception ex) { importError("Не удалось сохранить импорт. Проверьте данные и повторите попытку."); }
        });
        confirmImport.setEnabled(false); confirmImport.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        upload.addStartedListener(event -> { pending.clear(); preview.setItems(List.of()); confirmImport.setEnabled(false); });        upload.addSucceededListener(event -> {
            if (!event.getFileName().toLowerCase(java.util.Locale.ROOT).endsWith(".xlsx")) {
                importError("Разрешены только файлы .xlsx"); return;
            }
            try (var input = buffer.getInputStream()) {
                pending.clear(); pending.addAll(fileImporter.parse(input));
                preview.setItems(pending.stream().limit(10).toList()); preview.setVisible(true);
                confirmImport.setEnabled(!pending.isEmpty());
            } catch (Exception ex) { importError("" + (ex.getMessage() == null ? "Не удалось прочитать файл" : ex.getMessage())); }
        });
        upload.addFileRejectedListener(event -> importError(event.getErrorMessage()));
        upload.addFailedListener(event -> importError("Ошибка загрузки файла: " + event.getReason().getMessage()));
        VerticalLayout content = new VerticalLayout(hint, upload, new com.vaadin.flow.component.html.Span("Предпросмотр: первые 10 строк"), preview); content.setPadding(false); dialog.add(content);
        dialog.getFooter().add(new Button("Отмена", event -> dialog.close()), confirmImport);
        dialog.addOpenedChangeListener(event -> { if (!event.isOpened()) pending.clear(); }); dialog.open();
    }
    private void importError(String message) {
        Notification notification = Notification.show(message, 7000, Notification.Position.MIDDLE);
        notification.addThemeVariants(com.vaadin.flow.component.notification.NotificationVariant.LUMO_ERROR);
    }

    private void updateGrid() {
        showProducts(repository.findAllByShop(ru.sklad.app.util.SecurityUtils.currentShop()));
    }

    private void showProducts(List<Product> products) {
        displayedProducts = List.copyOf(products);
        grid.setItems(displayedProducts);
    }

    private void openProductDialog(Product product) {
        if (!permissions.hasPermission(product.getId() == null ? "canCreateProduct" : "canEditProduct")) {
            Notification.show("У вас нет прав на редактирование");
            return;
        }
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(product.getId() == null ? "Добавить товар" : "Редактировать товар");
        dialog.setModal(true);
        dialog.setCloseOnOutsideClick(false);
        dialog.setWidth("600px");
        dialog.setMaxWidth("95vw");
        dialog.add(createProductForm(product, dialog));
        dialog.open();
    }

    private FormLayout createProductForm(Product product, Dialog dialog) {
        var alternatives = new com.vaadin.flow.component.combobox.MultiSelectComboBox<Product>("Альтернативы при отсутствии");
        var additives = new com.vaadin.flow.component.combobox.MultiSelectComboBox<Product>("Рекомендуемые добавки");
        if (isAdmin()) {
            var choices = repository.findAllByShop(ru.sklad.app.util.SecurityUtils.currentShop()).stream().filter(p -> !Objects.equals(p.getId(), product.getId())).toList();
            var relations = productService.relations(product.getId());
            alternatives.setItemLabelGenerator(Product::getName); alternatives.setItems(choices);
            var additiveChoices = choices.stream().filter(p -> {
                String text = (Objects.toString(p.getCategory(), "") + " " + Objects.toString(p.getName(), "")).toLowerCase(java.util.Locale.ROOT);
                return relations.additives().contains(p.getId()) || text.contains("добав") || text.contains("витамин") || text.contains("премикс");
            }).toList();
            additives.setItemLabelGenerator(Product::getName); additives.setItems(additiveChoices);
            alternatives.setValue(choices.stream().filter(p -> relations.alternatives().contains(p.getId())).collect(java.util.stream.Collectors.toSet()));
            additives.setValue(additiveChoices.stream().filter(p -> relations.additives().contains(p.getId())).collect(java.util.stream.Collectors.toSet()));
            additives.setHelperText("Выберите добавки, витамины или премиксы из каталога");
        }
        TextField name = new TextField("Наименование");
        name.setValue(Objects.toString(product.getName(), ""));
        name.setRequiredIndicatorVisible(true);
        name.setErrorMessage("Введите наименование товара");

        TextField barcode = new TextField("Штрихкод");
        barcode.setValue(Objects.toString(product.getBarcode(), ""));
        barcode.setMaxLength(255);
        barcode.setClearButtonVisible(true);
        barcode.setErrorMessage("Этот штрихкод уже используется другим товаром");

        ComboBox<String> category = new ComboBox<>("Категория");
        List<String> categories = new ArrayList<>(List.of("Зерновые", "Бакалея", "Корма"));
        repository.findAllByShop(ru.sklad.app.util.SecurityUtils.currentShop()).stream().map(Product::getCategory)
                .filter(Objects::nonNull).filter(value -> !value.isBlank()).distinct().sorted()
                .filter(value -> !categories.contains(value)).forEach(categories::add);
        if (product.getCategory() != null && !categories.contains(product.getCategory())) {
            categories.add(product.getCategory());
        }
        category.setItems(categories);
        category.setValue(product.getCategory());
        category.setAllowCustomValue(true);
        category.addCustomValueSetListener(event -> category.setValue(event.getDetail()));

        TextField unit = new TextField("Единица измерения");
        unit.setPlaceholder("кг, шт, мешок");
        unit.setValue(Objects.toString(product.getUnit(), ""));
        ComboBox<String> animalType = classificationField("Вид животного", List.of(
                "Куры", "Бройлеры", "Индейки", "Свиньи", "Коровы", "Кролики", "Перепела", "Универсальный"), product.getAnimalType());
        ComboBox<String> age = classificationField("Возраст", List.of(
                "Старт (0-1 мес)", "Рост (1-4 мес)", "Взрослые", "Универсальный"), product.getAge());
        animalType.setReadOnly(!isAdmin()); age.setReadOnly(!isAdmin());

        NumberField stockQuantity = new NumberField("Остаток");
        NumberField purchasePrice = new NumberField("Цена закупа");
        NumberField retailPrice = new NumberField("Цена розница");
        NumberField markup = new NumberField("Наценка, %");
        markup.setHelperText("При нулевой закупке наценка не определена");
        for (NumberField field : new NumberField[]{stockQuantity, purchasePrice, markup, retailPrice}) {
            field.setErrorMessage("Введите корректное число");
            field.setValueChangeMode(ValueChangeMode.EAGER);
        }
        setNumberValue(stockQuantity, product.getStockQuantity());
        if (permissions.hasPermission("canSeePurchasePrice")) setNumberValue(purchasePrice, product.getPurchasePrice());
        setNumberValue(retailPrice, product.getRetailPrice());
        updateMarkup(purchasePrice, retailPrice, markup);

        boolean admin = isAdmin();
        barcode.setReadOnly(!admin && product.getId() != null);
        barcode.setVisible(admin || product.getId() == null);
        purchasePrice.setReadOnly(!admin);
        purchasePrice.setVisible(permissions.hasPermission("canSeePurchasePrice"));
        markup.setReadOnly(!admin);
        markup.setVisible(permissions.hasPermission("canSeePurchasePrice"));
        category.setReadOnly(!admin && product.getId() != null);
        unit.setReadOnly(!admin && product.getId() != null);
        retailPrice.setMin(0);
        retailPrice.setMax(99999999.99);

        purchasePrice.addValueChangeListener(event -> {
            if (event.isFromClient() && isAdmin() && !purchasePrice.isInvalid() && !markup.isInvalid()) {
                updateRetailPrice(purchasePrice, markup, retailPrice);
            }
        });
        markup.addValueChangeListener(event -> {
            if (event.isFromClient() && isAdmin() && !purchasePrice.isInvalid() && !markup.isInvalid()) {
                updateRetailPrice(purchasePrice, markup, retailPrice);
            }
        });
        retailPrice.addValueChangeListener(event -> {
            if (event.isFromClient() && isAdmin() && !retailPrice.isInvalid() && !purchasePrice.isInvalid()) {
                updateMarkup(purchasePrice, retailPrice, markup);
            }
        });

        FormLayout form = admin || product.getId() == null
                ? new FormLayout(name, barcode, category, unit, animalType, age, stockQuantity, purchasePrice, markup, retailPrice)
                : new FormLayout(name, animalType, age, stockQuantity, purchasePrice, markup, retailPrice);
        if (admin) { form.add(alternatives, additives); alternatives.setWidthFull(); additives.setWidthFull(); }
        form.setWidthFull();
        form.addClassName("responsive-form");
        form.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("480px", 2));
        form.setColspan(name, 2);

        Button saveButton = new Button("Сохранить", event -> {
            if (!permissions.hasPermission(product.getId() == null ? "canCreateProduct" : "canEditProduct")) {
                Notification.show("У вас нет прав на редактирование");
                return;
            }
            if (name.getValue().isBlank()) {
                name.setInvalid(true);
                name.focus();
                return;
            }
            name.setInvalid(false);
            if (isAdmin() && ((animalType.getValue() != null && animalType.getValue().trim().length() > 255)
                    || (age.getValue() != null && age.getValue().trim().length() > 255))) {
                Notification.show("Вид животного и возраст — не более 255 символов"); return;
            }
            String code = barcode.getValue().trim();
            boolean duplicateBarcode = (isAdmin() || product.getId() == null) && !code.isEmpty() && repository.findByBarcode(code)
                    .filter(existing -> !Objects.equals(existing.getId(), product.getId())).isPresent();
            barcode.setInvalid(duplicateBarcode);
            if (duplicateBarcode) {
                barcode.focus();
                return;
            }
            if (stockQuantity.isInvalid() || retailPrice.isInvalid() || (isAdmin()
                    && (purchasePrice.isInvalid() || markup.isInvalid()))) {
                return;
            }

            Product savedProduct = product.getId() == null ? new Product()
                    : repository.findById(product.getId()).orElseThrow();
            savedProduct.setVersion(product.getVersion());
            savedProduct.setName(name.getValue().trim());
            if (isAdmin() || product.getId() == null) savedProduct.setBarcode(code.isEmpty() ? null : code);
            if (isAdmin() || product.getId() == null) {
                savedProduct.setCategory(category.getValue());
                savedProduct.setUnit(unit.getValue().trim());
            }
            savedProduct.setStockQuantity(toBigDecimal(stockQuantity));
            savedProduct.setRetailPrice(toBigDecimal(retailPrice));
            if (isAdmin()) {
                savedProduct.setAnimalType(animalType.getValue() == null ? "" : animalType.getValue().trim());
                savedProduct.setAge(age.getValue() == null ? "" : age.getValue().trim());
                savedProduct.setPurchasePrice(toBigDecimal(purchasePrice));
                savedProduct.setAlternativeProducts(new java.util.LinkedHashSet<>(alternatives.getValue()));
                savedProduct.setRecommendedAdditives(new java.util.LinkedHashSet<>(additives.getValue()));
            }
            try {
                productService.save(savedProduct);
            } catch (DataIntegrityViolationException exception) {
                Notification.show("Не удалось сохранить товар: проверьте уникальность штрихкода и значения полей");
                return;
            }
            updateGrid();
            dialog.close();
        });
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button cancelButton = new Button("Отмена", event -> dialog.close());
        HorizontalLayout buttons = new HorizontalLayout(saveButton, cancelButton);
        if (isAdmin() && product.getId() != null) {
            Button delete = new Button("Удалить товар", event -> {
                var confirm = new com.vaadin.flow.component.confirmdialog.ConfirmDialog();
                confirm.setHeader("Удалить товар?"); confirm.setText(product.getName()); confirm.setCancelable(true);
                confirm.setCancelText("Отмена"); confirm.setConfirmText("Удалить"); confirm.setConfirmButtonTheme("error primary");
                confirm.addConfirmListener(e -> {
                    try { productService.delete(product.getId()); updateGrid(); dialog.close(); }
                    catch (DataIntegrityViolationException exception) { Notification.show("Товар используется в документах и не может быть удален"); }
                }); confirm.open();
            });
            delete.addThemeVariants(ButtonVariant.LUMO_ERROR); buttons.add(delete);
        }
        buttons.setAlignItems(Alignment.CENTER);
        dialog.getFooter().add(buttons);

        return form;
    }

    private ComboBox<String> classificationField(String label, List<String> suggestions, String current) {
        ComboBox<String> field = new ComboBox<>(label);
        List<String> values = new ArrayList<>(suggestions);
        if (current != null && !current.isBlank() && !values.contains(current)) values.add(current);
        field.setItems(values); field.setAllowCustomValue(true); field.setClearButtonVisible(true);
        if (current != null && !current.isBlank()) field.setValue(current);
        field.addCustomValueSetListener(event -> field.setValue(event.getDetail().trim()));
        return field;
    }

    private boolean isAdmin() {
        return RoleAccess.isAdmin();
    }

    private void setNumberValue(NumberField field, BigDecimal value) {
        if (value == null) {
            field.clear();
        } else {
            field.setValue(value.doubleValue());
        }
    }

    private void updateRetailPrice(NumberField purchasePrice, NumberField markup, NumberField retailPrice) {
        BigDecimal purchase = toBigDecimal(purchasePrice);
        BigDecimal retail = purchase.add(purchase.multiply(toBigDecimal(markup)).movePointLeft(2));
        setNumberValue(retailPrice, retail.setScale(2, RoundingMode.HALF_UP));
    }

    private void updateMarkup(NumberField purchasePrice, NumberField retailPrice, NumberField markup) {
        BigDecimal purchase = toBigDecimal(purchasePrice);
        if (purchase.signum() == 0) {
            markup.clear();
            return;
        }
        BigDecimal percent = toBigDecimal(retailPrice).subtract(purchase)
                .multiply(BigDecimal.valueOf(100)).divide(purchase, 2, RoundingMode.HALF_UP);
        setNumberValue(markup, percent);
    }

    private BigDecimal toBigDecimal(NumberField field) {
        return field.getValue() == null ? BigDecimal.ZERO : BigDecimal.valueOf(field.getValue());
    }
}
