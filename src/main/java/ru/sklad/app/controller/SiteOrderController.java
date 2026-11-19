package ru.sklad.app.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import ru.sklad.app.service.CallbackRequestService;
import ru.sklad.app.service.SiteOrderService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

@RestController
@RequestMapping("/api/public")
@CrossOrigin(origins = {"https://agro-korma-shop.ru", "https://www.agro-korma-shop.ru"}, methods = RequestMethod.POST)
public class SiteOrderController {
    public record Item(String productName, BigDecimal quantity) {}
    public record Order(String customerName, String phone, String address, String comment, List<Item> items,
                        UUID requestId, LocalDate deliveryDate, LocalTime deliveryTime) {
        public Order(String customerName, String phone, String address, String comment, List<Item> items) {
            this(customerName, phone, address, comment, items, null, null, null);
        }
    }
    public record Callback(UUID id, String name, String phone, String orderDetails) {}
    private final SiteOrderService orders;
    private final CallbackRequestService callbacks;
    public SiteOrderController(SiteOrderService orders, CallbackRequestService callbacks) {
        this.orders = orders;
        this.callbacks = callbacks;
    }
    @org.springframework.beans.factory.annotation.Autowired private ru.sklad.app.repository.ShopRepository shops;
    @org.springframework.beans.factory.annotation.Value("${app.site-order.shop-id:1}") private long shopId;
    @org.springframework.beans.factory.annotation.Value("${app.site-order.api-key:}") private String apiKey;
    @PostMapping(path = "/delivery-order", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> create(
            @RequestHeader(value = "X-Site-Order-Key", defaultValue = "") String providedKey,
            @RequestBody Order order) {
        if (!authorized(providedKey)) return unauthorized();
        var shop = shops.findById(shopId).filter(ru.sklad.app.entity.Shop::isActive).orElseThrow();
        ru.sklad.app.util.SecurityUtils.inShop(shop, () -> orders.create(order));
        return ResponseEntity.ok(Map.of("status", "success", "message", "Заявка создана"));
    }

    @PostMapping(path = "/callback-request", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> callback(
            @RequestHeader(value = "X-Site-Order-Key", defaultValue = "") String providedKey,
            @RequestBody Callback request) {
        if (!authorized(providedKey)) return unauthorized();
        var shop = shops.findById(shopId).filter(ru.sklad.app.entity.Shop::isActive).orElseThrow();
        try {
            ru.sklad.app.util.SecurityUtils.inShop(shop, () -> {
                callbacks.create(request.id(), request.name(), request.phone(), request.orderDetails());
                return null;
            });
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Проверьте имя и телефон"));
        }
        return ResponseEntity.ok(Map.of("status", "success", "message", "Заявка создана"));
    }

    private boolean authorized(String providedKey) {
        return apiKey.length() >= 32 && MessageDigest.isEqual(
                apiKey.getBytes(StandardCharsets.UTF_8), providedKey.getBytes(StandardCharsets.UTF_8));
    }

    private ResponseEntity<Map<String, String>> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("status", "error", "message", "Авторизация обязательна"));
    }
    @ExceptionHandler({IllegalArgumentException.class, org.springframework.http.converter.HttpMessageNotReadableException.class})
    public ResponseEntity<Map<String, String>> invalid(Exception exception) {
        return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Проверьте контакты, адрес, товары и дату доставки. Доставка возможна со следующего дня."));
    }
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> conflict(Exception exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("status", "error", "message", "Не удалось сохранить заявку. Повторите запрос"));
    }
}
