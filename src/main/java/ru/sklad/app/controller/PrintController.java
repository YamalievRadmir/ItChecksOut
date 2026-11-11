package ru.sklad.app.controller;

import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.HtmlUtils;
import ru.sklad.app.entity.Sale;
import ru.sklad.app.entity.SaleItem;
import ru.sklad.app.repository.SaleRepository;
import ru.sklad.app.repository.SaleItemRepository;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

@RestController
public class PrintController {
    private final SaleRepository sales;
    private final SaleItemRepository items;
    public PrintController(SaleRepository sales, SaleItemRepository items) {
        this.sales = sales; this.items = items;
    }
    @GetMapping(value = "/print-receipt/{saleId}", produces = "text/html;charset=UTF-8")
    @PreAuthorize("@permissionService.canAccess('sales') or @permissionService.canAccess('sales-history')")
    @Transactional(readOnly = true)
    public ResponseEntity<String> receipt(@PathVariable Long saleId) {
        Sale sale = sales.findById(saleId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Продажа не найдена"));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(new MediaType("text", "html", java.nio.charset.StandardCharsets.UTF_8))
                .body(render(sale, items.findBySaleId(saleId)));
    }
    private static String escape(String value) { return HtmlUtils.htmlEscape(Objects.toString(value, "")); }
    private static String shortName(String name) {
        String value = Objects.toString(name, "");
        return value.substring(0, value.offsetByCodePoints(0, Math.min(20, value.codePointCount(0, value.length()))));
    }
    static String render(Sale sale, List<SaleItem> items) {
        StringBuilder html = new StringBuilder("""
                <!doctype html>
                <html lang="ru"><head><meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>Лист сборки заказа — Всё сходится!</title>
                <style>
                @page { size: 57mm auto; margin: 0; }
                * { box-sizing: border-box; }
                html, body { width: 57mm; margin: 0; padding: 0; background: white; color: black;
                  font-family: 'Courier New', monospace; font-size: 10px; line-height: 1.4; }
                main { width: 57mm; margin: 0; padding: 10px; }
                h1 { margin: 0; font-size: 18px; text-align: center; }
                p { margin: 2px 0; overflow-wrap: anywhere; }
                .center { text-align: center; }
                table { width: 100%; border-collapse: collapse; table-layout: fixed; font-size: 14px; margin: 4px 0; }
                th, td { padding: 5px 0; vertical-align: top; overflow-wrap: anywhere; text-align: right; }
                th:first-child, td:first-child { text-align: left; padding-right: 2px; }
                th { border-top: 1px dashed black; border-bottom: 1px dashed black;
                  font-size: 11px; white-space: nowrap; overflow-wrap: normal; }
                tr { break-inside: avoid; }
                tbody td:last-child { font-weight: bold; font-size: 16px; overflow-wrap: normal; }
                .quantity-value, .quantity-unit { white-space: nowrap; }
                button { font: inherit; cursor: pointer; }
                @media print { .print-controls { display: none !important; } }
                </style></head><body><main>
                <div class="print-controls"><button type="button" onclick="window.print()">Распечатать чек</button></div>
                <h1>Всё сходится!</h1>
                <p class="center">Лист сборки заказа</p>
                """);
        html.append("<p>Чек № ").append(sale.getId()).append("</p><p>")
                .append(escape((sale.getSaleDate() == null ? "" : sale.getSaleDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")))))
                .append("</p><table><colgroup><col style='width:56%'><col style='width:44%'></colgroup>")
                .append("<thead><tr><th>Наименование</th><th>Количество</th></tr></thead><tbody>");
        for (SaleItem item : items) {
            html.append("<tr><td>").append(escape(shortName(item.getProductName())))
                    .append("</td><td><span class='quantity-value'>").append(escape(Objects.requireNonNullElse(item.getQuantity(), java.math.BigDecimal.ZERO).stripTrailingZeros().toPlainString().replace('.', ',')))
                    .append("</span>")
                    .append(item.getUnit() == null || item.getUnit().isBlank() ? "" : " <span class='quantity-unit'>" + escape(item.getUnit().trim()) + "</span>")
                    .append("</td></tr>");
        }
        html.append("</tbody></table>");
        html.append("</main>")
                .append("<script>window.addEventListener('load', function () { window.print(); }, {once: true});</script></body></html>");
        return html.toString();
    }
}
