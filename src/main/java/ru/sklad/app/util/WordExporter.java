package ru.sklad.app.util;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.server.StreamResource;
import org.apache.poi.xwpf.usermodel.*;
import ru.sklad.app.entity.Sale;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;

public final class WordExporter {
    private WordExporter() {}

    public record PayrollRow(String fullName, String role, int shifts, BigDecimal total) {}

    public static XWPFDocument timesheet(YearMonth month, List<PayrollRow> employees) {
        XWPFDocument document = document();
        String monthName = month.getMonth().getDisplayName(TextStyle.FULL_STANDALONE,
                Locale.forLanguageTag("ru-RU"));
        title(document, "Табель учета рабочего времени за " + monthName + " " + month.getYear());
        XWPFTable table = table(document, new String[]{"ФИО", "Должность", "Отработано смен", "К выплате, руб."});
        BigDecimal total = BigDecimal.ZERO;
        for (PayrollRow employee : employees) {
            row(table, employee.fullName(), employee.role(), String.valueOf(employee.shifts()), money(employee.total()));
            total = total.add(employee.total());
        }
        total(document, "ИТОГО К ВЫПЛАТЕ: " + money(total) + " руб.");
        return document;
    }

    public static XWPFDocument receipt(Sale sale) {
        if (sale == null || sale.getId() == null) {
            throw new IllegalArgumentException("Сначала проведите продажу");
        }
        XWPFDocument document = document();
        title(document, "Всё сходится!");
        title(document, "Товарный чек № " + sale.getId());
        paragraph(document, "Дата и время продажи: "
                + sale.getSaleDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")), false);
        paragraph(document, "Оплата: " + ("CASHLESS".equals(sale.getPaymentMethod()) ? "Безнал" : "Наличные"), false);
        XWPFTable table = table(document, new String[]{"Товар", "Кол-во", "Цена, руб.", "Сумма, руб."});
        for (var item : sale.getItems()) {
            row(table, item.getProductName(), item.getQuantity().stripTrailingZeros().toPlainString(),
                    money(item.getRetailPrice()), money(item.getTotalSum()));
        }
        if (sale.getDiscountSum().signum() > 0) {
            paragraph(document, "Сумма без скидки: " + money(sale.getTotalSum()) + " руб.", false);
            paragraph(document, "Скидка: " + money(sale.getDiscountSum()) + " руб.", false);
        }
        total(document, "ИТОГО: " + money(sale.getFinalSum()) + " руб.");
        XWPFParagraph thanks = paragraph(document, "Спасибо за покупку!", false);
        thanks.setAlignment(ParagraphAlignment.CENTER);
        return document;
    }

    public static StreamResource toStreamResource(XWPFDocument document, String filename) {
        byte[] data;
        try (document; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.write(output);
            data = output.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException("Не удалось создать Word-файл", exception);
        }
        StreamResource resource = new StreamResource(filename, () -> new ByteArrayInputStream(data));
        resource.setContentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        resource.setCacheTime(0);
        return resource;
    }

    public static HorizontalLayout downloadButton(String label, String filename, Supplier<XWPFDocument> document) {
        Anchor link = new Anchor();
        link.getElement().setAttribute("download", filename);
        link.getStyle().set("display", "none");
        Button button = new Button(label, event -> {
            link.setHref(toStreamResource(document.get(), filename));
            link.getElement().executeJs("this.click()");
        });
        HorizontalLayout control = new HorizontalLayout(button, link);
        control.setPadding(false);
        control.setSpacing(false);
        return control;
    }

    private static XWPFDocument document() {
        XWPFDocument document = new XWPFDocument();
        var section = document.getDocument().getBody().addNewSectPr();
        var size = section.addNewPgSz();
        size.setW(BigInteger.valueOf(11906));
        size.setH(BigInteger.valueOf(16838));
        var margins = section.addNewPgMar();
        margins.setTop(BigInteger.valueOf(1134));
        margins.setBottom(BigInteger.valueOf(1134));
        margins.setLeft(BigInteger.valueOf(1134));
        margins.setRight(BigInteger.valueOf(1134));
        return document;
    }

    private static void title(XWPFDocument document, String text) {
        XWPFParagraph title = paragraph(document, text, true);
        title.setStyle("Title");
        title.setAlignment(ParagraphAlignment.CENTER);
        title.setSpacingAfter(240);
        title.getRuns().get(0).setFontSize(16);
    }

    private static XWPFParagraph paragraph(XWPFDocument document, String text, boolean bold) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingAfter(120);
        XWPFRun run = paragraph.createRun();
        run.setFontFamily("Calibri");
        run.setFontSize(11);
        run.setBold(bold);
        run.setText(text);
        return paragraph;
    }

    private static XWPFTable table(XWPFDocument document, String[] headers) {
        XWPFTable table = document.createTable(1, headers.length);
        table.setWidth(9638);
        table.setCellMargins(100, 100, 100, 100);
        table.getRow(0).setRepeatHeader(true);
        for (int c = 0; c < headers.length; c++) {
            cell(table.getRow(0).getCell(c), headers[c], true);
            table.getRow(0).getCell(c).setColor("E7E6E6");
        }
        return table;
    }

    private static void row(XWPFTable table, String... values) {
        XWPFTableRow row = table.createRow();
        row.setCantSplitRow(true);
        for (int c = 0; c < values.length; c++) cell(row.getCell(c), values[c], false);
    }

    private static void cell(XWPFTableCell cell, String text, boolean bold) {
        cell.setWidth("2409");
        XWPFParagraph paragraph = cell.getParagraphs().get(0);
        paragraph.setSpacingAfter(60);
        XWPFRun run = paragraph.createRun();
        run.setFontFamily("Calibri");
        run.setFontSize(11);
        run.setBold(bold);
        run.setText(Objects.toString(text, ""));
    }

    private static void total(XWPFDocument document, String text) {
        XWPFParagraph total = paragraph(document, text, true);
        total.setAlignment(ParagraphAlignment.RIGHT);
        total.setSpacingBefore(240);
    }

    private static String money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
