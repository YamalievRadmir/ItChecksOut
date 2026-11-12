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

    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("hasRole('ADMIN')")
    public int importFile(InputStream input) throws IOException {
        var shop = SecurityUtils.currentShop();
        try (XSSFWorkbook workbook = new XSSFWorkbook(input)) {
            if (workbook.getNumberOfSheets() == 0) throw new IllegalArgumentException("Ошибка: не найдена колонка с наименованием");
            Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(0);
            int name = -1, price = -1, stock = -1;
            DataFormatter formatter = new DataFormatter(Locale.ROOT);
            if (header != null) for (Cell cell : header) {
                String label = formatter.formatCellValue(cell).replace('\u00a0', ' ').trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
                if (Set.of("наименование", "название", "товар", "name").contains(label)) name = cell.getColumnIndex();
                if (Set.of("цена", "розница", "стоимость", "price").contains(label)) price = cell.getColumnIndex();
                if (Set.of("остаток", "количество", "кол-во", "stock").contains(label)) stock = cell.getColumnIndex();
            }
            if (name < 0) throw new IllegalArgumentException("Ошибка: не найдена колонка с наименованием");
            Map<String, Product> existing = new LinkedHashMap<>();
            for (Product product : products.findAllForUpdate()) existing.putIfAbsent(product.getName().trim().toLowerCase(Locale.ROOT), product);
            int count = 0;
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                String title = formatter.formatCellValue(row.getCell(name)).trim();
                if (title.isBlank()) continue;
                if (title.length() > 255) throw new IllegalArgumentException("Строка " + (i + 1) + ": наименование длиннее 255 символов");
                String key = title.toLowerCase(Locale.ROOT);
                Product product = existing.get(key);
                if (product == null) {
                    product = new Product(); product.setName(title); product.setShop(shop); product.setPurchasePrice(BigDecimal.ZERO);
                    existing.put(key, product);
                }
                product.setRetailPrice(number(row, price, i));
                product.setStockQuantity(number(row, stock, i));
                products.save(product); count++;
            }
            products.flush();
            audit.safeLogAction("ИМПОРТ", "Товары", "Excel", "Обработано строк: " + count);
            return count;
        }
    }
    private BigDecimal number(Row row, int index, int rowIndex) {
        if (index < 0) return BigDecimal.ZERO;
        Cell cell = row.getCell(index);
        if (cell == null || cell.getCellType() != CellType.NUMERIC) return BigDecimal.ZERO;
        double value = cell.getNumericCellValue();
        if (!Double.isFinite(value)) return BigDecimal.ZERO;
        BigDecimal decimal = BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
        if (decimal.signum() < 0 || decimal.compareTo(new BigDecimal("99999999.99")) > 0)
            throw new IllegalArgumentException("Строка " + (rowIndex + 1) + ": цена или остаток вне допустимого диапазона");
        return decimal;
    }
}