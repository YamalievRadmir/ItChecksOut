package ru.sklad.app.util;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.server.StreamResource;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

public final class ExcelExporter {
    private ExcelExporter() {}

    public record Column<T>(String header, Function<T, ?> value, String numberFormat) {
        public Column(String header, Function<T, ?> value) {
            this(header, value, "#,##0.00");
        }
    }

    public static <T> byte[] export(String sheetName, List<Column<T>> columns, List<T> data) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(sheetName);
            CellStyle header = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            font.setColor(IndexedColors.WHITE.getIndex());
            header.setFont(font);
            header.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            header.setWrapText(true);
            List<CellStyle> numberStyles = columns.stream().map(column -> {
                CellStyle style = workbook.createCellStyle();
                style.setDataFormat(workbook.createDataFormat().getFormat(column.numberFormat()));
                return style;
            }).toList();
            CellStyle date = workbook.createCellStyle();
            date.setDataFormat(workbook.createDataFormat().getFormat("dd.mm.yyyy"));
            Row headings = sheet.createRow(0);
            headings.setHeightInPoints(32);
            for (int c = 0; c < columns.size(); c++) {
                Cell cell = headings.createCell(c);
                cell.setCellValue(columns.get(c).header());
                cell.setCellStyle(header);
            }
            int rowIndex = 1;
            for (T item : data) {
                Row row = sheet.createRow(rowIndex++);
                for (int c = 0; c < columns.size(); c++) {
                    Cell cell = row.createCell(c);
                    Object value = columns.get(c).value().apply(item);
                    if (value instanceof Number numeric) {
                        cell.setCellValue(numeric.doubleValue());
                        cell.setCellStyle(numberStyles.get(c));
                    } else if (value instanceof LocalDate localDate) {
                        cell.setCellValue(localDate);
                        cell.setCellStyle(date);
                    } else if (value != null) {
                        // Treat user input as text, including strings starting with '='.
                        cell.setCellValue(value.toString());
                    }
                }
            }
            sheet.createFreezePane(1, 1);
            sheet.setAutoFilter(new CellRangeAddress(0, Math.max(0, data.size()), 0, columns.size() - 1));
            for (int c = 0; c < columns.size(); c++) {
                sheet.autoSizeColumn(c);
                sheet.setColumnWidth(c, Math.min(64 * 256, Math.max(7 * 256, sheet.getColumnWidth(c) + 512)));
            }
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException("Не удалось создать Excel-файл", exception);
        }
    }

    public static HorizontalLayout downloadButton(String label, String filename, Supplier<byte[]> content) {
        Anchor link = new Anchor();
        link.getElement().setAttribute("download", filename);
        link.getStyle().set("display", "none");
        Button button = new Button(label, event -> {
            byte[] snapshot = content.get();
            StreamResource resource = new StreamResource(filename, () -> new ByteArrayInputStream(snapshot));
            resource.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            resource.setCacheTime(0);
            link.setHref(resource);
            link.getElement().executeJs("this.click()");
        });
        HorizontalLayout control = new HorizontalLayout(button, link);
        control.setPadding(false);
        control.setSpacing(false);
        return control;
    }
}
