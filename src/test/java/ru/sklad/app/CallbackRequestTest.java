package ru.sklad.app;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import ru.sklad.app.entity.Shop;
import ru.sklad.app.repository.ShopRepository;
import ru.sklad.app.service.CallbackRequestService;
import ru.sklad.app.service.DeliveryService;
import ru.sklad.app.util.SecurityUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:callback-test;DB_CLOSE_DELAY=-1",
        "app.bootstrap.super-admin-password=CallbackTestPassword",
        "app.demo-data.enabled=false",
        "app.site-order.api-key=callback-test-key-with-at-least-32-characters"
})
class CallbackRequestTest {
    @LocalServerPort
    int port;
    @Autowired
    CallbackRequestService callbacks;
    @Autowired
    DeliveryService deliveries;
    @Autowired
    ShopRepository shops;

    @Test
    void websiteCanCreateOneShopScopedRequestAndStaffCanCompleteIt() throws Exception {
        UUID id = UUID.randomUUID();
        String body = """
                {"id":"%s","name":"Проверка","phone":"8 (900) 123-45-67","orderDetails":"Корм — 2"}
                """.formatted(id);
        assertEquals(401, post(body, false));
        assertEquals(400, post(body.replace("8 (900) 123-45-67", "123"), true));
        assertEquals(200, post(body, true));
        assertEquals(200, post(body, true));
        assertEquals(400, post(body.replace("Проверка", "Другое имя"), true));
        String delivery = """
                {"requestId":"%s","customerName":"Доставка","phone":"89001234568","address":"Тестовый адрес",
                 "items":[{"productName":"Корм","quantity":2}]}
                """.formatted(UUID.randomUUID());
        assertEquals(200, post("/delivery-order", delivery, true));
        assertEquals(200, post("/delivery-order", delivery, true));
        LocalDate selectedDate = LocalDate.now().plusDays(5);
        String scheduled = """
                {"requestId":"%s","customerName":"Оформленная доставка","phone":"89001234569","address":"Тестовый адрес",
                 "items":[{"productName":"Корм","quantity":2}],"deliveryDate":"%s","deliveryTime":"14:35"}
                """.formatted(UUID.randomUUID(), selectedDate);
        assertEquals(400, post("/delivery-order", scheduled.replace(selectedDate.toString(), LocalDate.now().toString()), true));
        assertEquals(200, post("/delivery-order", scheduled, true));

        Shop shop = shops.findById(1L).orElseThrow();
        Shop other = new Shop();
        other.setName("Другой магазин");
        Shop otherShop = shops.save(other);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "callback-test", null, AuthorityUtils.createAuthorityList("ROLE_ADMIN")));
        try {
            SecurityUtils.inShop(otherShop, () -> {
                assertTrue(callbacks.pending().isEmpty());
                assertEquals(0, deliveries.countOpen());
                assertTrue(deliveries.listOpen().isEmpty());
                callbacks.complete(id);
                return null;
            });
            SecurityUtils.inShop(shop, () -> {
                var pending = callbacks.pending();
                assertEquals(1, pending.size());
                assertEquals(id, pending.get(0).getId());
                assertEquals("79001234567", pending.get(0).getPhone());
                assertEquals("Корм — 2", pending.get(0).getOrderDetails());
                assertEquals(2, deliveries.countOpen());
                assertEquals(0, deliveries.countNewToday());
                var open = deliveries.listOpen();
                assertEquals(LocalDate.now().plusDays(1), open.get(0).getDeliveryDate());
                assertEquals(LocalTime.of(9, 0), open.get(0).getDeliveryTime());
                assertEquals(selectedDate, open.get(1).getDeliveryDate());
                assertEquals(LocalTime.of(14, 35), open.get(1).getDeliveryTime());
                callbacks.complete(id);
                assertTrue(callbacks.pending().isEmpty());
                return null;
            });
            assertEquals(200, post(body, true));
            assertTrue(SecurityUtils.inShop(shop, callbacks::pending).isEmpty());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private int post(String body, boolean authorized) throws Exception {
        return post("/callback-request", body, authorized);
    }

    private int post(String path, String body, boolean authorized) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/public" + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (authorized) request.header("X-Site-Order-Key", "callback-test-key-with-at-least-32-characters");
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }
}
