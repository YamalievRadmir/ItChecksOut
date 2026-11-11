package ru.sklad.app.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import ru.sklad.app.service.SiteOrderService;
import java.math.BigDecimal;
import java.util.*;

@RestController
@RequestMapping("/api/public/delivery-order")
@CrossOrigin(origins = {"https://agro-korma-shop.ru", "https://www.agro-korma-shop.ru"}, methods = RequestMethod.POST)
public class SiteOrderController {
    public record Item(String productName, BigDecimal quantity) {}
    public record Order(String customerName, String phone, String address, String comment, List<Item> items) {}
    private final SiteOrderService orders;
    public SiteOrderController(SiteOrderService orders) { this.orders = orders; }
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, String> create(@RequestBody Order order) {
        orders.create(order);
        return Map.of("status", "success", "message", "Заявка создана");
    }
    @ExceptionHandler({IllegalArgumentException.class, org.springframework.http.converter.HttpMessageNotReadableException.class})
    public ResponseEntity<Map<String, String>> invalid(Exception exception) {
        return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Проверьте имя, телефон, адрес и товары: количество должно быть положительным, с точностью до двух знаков"));
    }
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> conflict(Exception exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("status", "error", "message", "Не удалось сохранить заявку. Повторите запрос"));
    }
}
