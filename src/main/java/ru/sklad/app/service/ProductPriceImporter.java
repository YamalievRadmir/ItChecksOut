package ru.sklad.app.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import ru.sklad.app.entity.Product;
import ru.sklad.app.util.ProductAnimalClassifier;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

@Service
public class ProductPriceImporter {
    public record Catalog(List<Product> products, String sha256) {}

    public Catalog read(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        List<Product> products = new ArrayList<>(); Set<String> names = new HashSet<>();
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = book.getSheetAt(0);
            DataFormatter formatter = new DataFormatter(Locale.forLanguageTag("ru-RU"));
            FormulaEvaluator evaluator = book.getCreationHelper().createFormulaEvaluator();
            int header = -1, nameColumn = -1, priceColumn = -1, stockColumn = -1;
            for (int r = 0; r <= Math.min(sheet.getLastRowNum(), 20); r++) {
                Row row = sheet.getRow(r); if (row == null) continue;
                int name = -1, price = -1, stock = -1;
                for (int c = 0; c < row.getLastCellNum(); c++) {
                    String label = text(row, c, formatter, evaluator).toLowerCase(Locale.ROOT);
                    if (label.equals("наименование")) name = c;
                    if (label.equals("цена продажи")) price = c;
                    if (label.equals("остаток")) stock = c;
                }
                if (name >= 0 && price >= 0 && stock >= 0) { header = r; nameColumn = name; priceColumn = price; stockColumn = stock; break; }
            }
            if (header < 0) throw new IllegalArgumentException("В прайсе нужны колонки: Наименование, Цена продажи, Остаток");
            for (int r = header + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r); if (row == null) continue;
                String name = text(row, nameColumn, formatter, evaluator); if (name.isEmpty()) continue;
                if (name.length() > 255 || !names.add(name.toLowerCase(Locale.ROOT)))
                    throw new IllegalArgumentException("Повторяющееся или слишком длинное название в строке " + (r + 1));
                Product product = new Product(); product.setName(name); product.setUnit("шт.");
                product.setRetailPrice(number(row, priceColumn, formatter, evaluator));
                product.setStockQuantity(number(row, stockColumn, formatter, evaluator));
                product.setPurchasePrice(product.getRetailPrice().multiply(new BigDecimal("0.70")).setScale(2, RoundingMode.HALF_UP));
                ProductAnimalClassifier.fillMissing(product);
                product.setCategory("Универсальный".equals(product.getAnimalType()) ? "Зерно"
                        : product.getAnimalType() == null ? "" : "Комбикорм");
                products.add(product);
            }
        }
        if (products.isEmpty()) throw new IllegalArgumentException("В прайсе нет товаров");
        try { return new Catalog(List.copyOf(products), HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }

    private static String text(Row row, int column, DataFormatter formatter, FormulaEvaluator evaluator) {
        return formatter.formatCellValue(row.getCell(column), evaluator).replace('\u00a0', ' ').replace('\u202f', ' ')
                .trim().replaceAll("\\s+", " ");
    }
    private static BigDecimal number(Row row, int column, DataFormatter formatter, FormulaEvaluator evaluator) {
        Cell cell = row.getCell(column); BigDecimal value;
        try {
            if (cell != null && cell.getCellType() == CellType.NUMERIC) value = BigDecimal.valueOf(cell.getNumericCellValue());
            else if (cell != null && cell.getCellType() == CellType.FORMULA) {
                CellValue result = evaluator.evaluate(cell);
                if (result == null || result.getCellType() != CellType.NUMERIC) throw new NumberFormatException();
                value = BigDecimal.valueOf(result.getNumberValue());
            } else value = new BigDecimal(text(row, column, formatter, evaluator).replace(" ", "").replace(',', '.'));
            if (value.signum() < 0 || value.compareTo(new BigDecimal("99999999.99")) > 0) throw new NumberFormatException();
            return value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new IllegalArgumentException("Некорректная цена или остаток в строке " + (row.getRowNum() + 1) + ", колонка " + (column + 1), exception);
        }
    }
}
