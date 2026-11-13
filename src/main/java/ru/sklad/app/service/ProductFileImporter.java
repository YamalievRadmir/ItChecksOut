package ru.sklad.app.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import ru.sklad.app.entity.Product;
import ru.sklad.app.repository.ProductRepository;
import ru.sklad.app.util.SecurityUtils;
import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
public class ProductFileImporter {
    private final ProductRepository products;
    private final AuditService audit;
    public ProductFileImporter(ProductRepository products, AuditService audit) { this.products = products; this.audit = audit; }

    public record ImportResult(int processedRows, int zeroPriceRows) {}
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("hasRole('ADMIN')")
    public ImportResult importFile(InputStream input) throws IOException {
        return savePreview(parse(input));
    }
    @PreAuthorize("hasRole('ADMIN')")
    public List<Product> parse(InputStream input) throws IOException {
        List<Product> parsed = new ArrayList<>();
        try (XSSFWorkbook workbook = new XSSFWorkbook(input)) {
            if (workbook.getNumberOfSheets() == 0) throw new IllegalArgumentException("Ошибка: не найдена колонка с наименованием");
            Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(0);
            int name = -1, price = -1, stock = -1;
            DataFormatter formatter = new DataFormatter(Locale.ROOT);
            if (header != null) for (Cell cell : header) {
                String label = formatter.formatCellValue(cell).replace('\u00a0', ' ').trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
                if (Set.of("наименование", "название", "товар", "name").contains(label)) name = cell.getColumnIndex();
                if (matches(label, "цена", "цена продажи", "розничная", "розница", "цена розница", "стоимость", "price", "retail")) price = cell.getColumnIndex();
                if (matches(label, "остаток", "количество", "кол-во", "stock", "qty")) stock = cell.getColumnIndex();
            }
            if (name < 0) throw new IllegalArgumentException("Ошибка: не найдена колонка с наименованием");
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                String title = formatter.formatCellValue(row.getCell(name)).trim();
                if (title.isBlank()) continue;
                if (title.length() > 255) throw new IllegalArgumentException("Строка " + (i + 1) + ": наименование длиннее 255 символов");
                Product product = new Product(); product.setName(title);
                product.setRetailPrice(number(row, price, i));

                product.setStockQuantity(number(row, stock, i));
                parsed.add(product);
            }
            return List.copyOf(parsed);
        }
    }
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public ImportResult savePreview(List<Product> parsed) {
        var shop = SecurityUtils.businessShop();
        Map<String, Product> existing = new LinkedHashMap<>();
        for (Product p : products.findAllForUpdate()) existing.putIfAbsent(p.getName().trim().toLowerCase(Locale.ROOT), p);
        int zeroPrices = 0;
        for (Product row : parsed) {
            if (row.getName() == null || row.getName().isBlank() || row.getName().length() > 255)
                throw new IllegalArgumentException("Проверьте наименование товара");
            for (BigDecimal value : List.of(row.getRetailPrice(), row.getStockQuantity()))
                if (value.signum() < 0 || value.compareTo(new BigDecimal("99999999.99")) > 0 || value.stripTrailingZeros().scale() > 2)
                    throw new IllegalArgumentException("Цена или остаток вне допустимого диапазона");
            Product product = existing.computeIfAbsent(row.getName().trim().toLowerCase(Locale.ROOT), key -> {
                Product created = new Product(); created.setName(row.getName()); created.setShop(shop); created.setPurchasePrice(BigDecimal.ZERO); return created;
            });
            product.setRetailPrice(row.getRetailPrice()); product.setStockQuantity(row.getStockQuantity());
            if (row.getRetailPrice().signum() == 0) zeroPrices++;
            products.save(product);
        }
        products.flush();
        audit.safeLogAction("ИМПОРТ", "Товары", "Excel", "Обработано строк: " + parsed.size());
        return new ImportResult(parsed.size(), zeroPrices);
    }
    private boolean matches(String label, String... keywords) {
        return Arrays.stream(keywords).anyMatch(label::contains);
    }
    private BigDecimal number(Row row, int index, int rowIndex) {
        if (index < 0) return BigDecimal.ZERO;
        Cell cell = row.getCell(index);
        if (cell == null) return BigDecimal.ZERO;
        BigDecimal decimal;
        try {
            if (cell.getCellType() == CellType.NUMERIC) {
                decimal = BigDecimal.valueOf(cell.getNumericCellValue());
            } else if (cell.getCellType() == CellType.STRING) {
                String value = cell.getStringCellValue().toLowerCase(Locale.ROOT)
                        .replaceAll("[\\s\\p{Z}]+", "").replace("₽", "")
                        .replace("руб.", "").replace("руб", "").replace(',', '.');
                decimal = new BigDecimal(value);
            } else return BigDecimal.ZERO;
            decimal = decimal.setScale(2, RoundingMode.HALF_UP);
        } catch (IllegalArgumentException | ArithmeticException exception) {
            return BigDecimal.ZERO;
        }
        if (decimal.signum() < 0 || decimal.compareTo(new BigDecimal("99999999.99")) > 0)
            throw new IllegalArgumentException("Строка " + (rowIndex + 1) + ": цена или остаток вне допустимого диапазона");
        return decimal;
    }
}