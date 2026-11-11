package ru.sklad.app;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.sklad.app.controller.SiteOrderController.*;
import ru.sklad.app.entity.*;
import ru.sklad.app.repository.*;
import ru.sklad.app.service.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(showSql=false) @Import(SiteOrderService.class)
class SiteOrderTest {
    @Autowired SiteOrderService service;
    @Autowired CustomerRepository customers;
    @Autowired ProductRepository products;
    @Autowired DeliveryRepository deliveries;
    @MockitoBean AuditService audit;
    @Test void matchesProductsMergesLinesAndReusesCustomer() {
        Product p = new Product(); p.setName("Корм"); p.setRetailPrice(BigDecimal.TEN); p.setStockQuantity(BigDecimal.TEN);
        products.saveAndFlush(p);
        var request = new Order("Клиент", "+7 (900) 123-45-67", "Адрес", "Позвонить", List.of(
            new Item("корм", BigDecimal.ONE), new Item("Корм", BigDecimal.ONE), new Item("Неизвестный", BigDecimal.ONE)));
        var d = service.create(request);
        assertEquals("Новая", d.getStatus()); assertEquals(LocalDate.now().plusDays(1), d.getDeliveryDate());
        assertEquals(1,d.getItems().size()); assertEquals(0,BigDecimal.valueOf(2).compareTo(d.getItems().get(0).getQuantity()));
        assertEquals(0,BigDecimal.valueOf(20).compareTo(d.getTotalSum())); assertTrue(d.getComment().contains("Неизвестный"));
        assertEquals("79001234567", d.getCustomer().getPhone());
        assertEquals(d.getCustomer().getId(), service.create(request).getCustomer().getId());
        assertEquals(1,customers.count()); assertEquals(0,BigDecimal.TEN.compareTo(products.findById(p.getId()).orElseThrow().getStockQuantity()));
    }
    @Test void rejectsInvalidQuantityBeforeWritingAnything() {
        assertThrows(IllegalArgumentException.class, () -> service.create(new Order("Клиент", "123456", "Адрес", "", List.of(new Item("Корм", BigDecimal.ZERO)))));
        assertEquals(0,customers.count()); assertEquals(0,deliveries.count());
    }
    @Test void preservesOrderEvenIfAllItemsAreUnknown() {
        var d = service.create(new Order("Клиент", "123456", "Адрес", "", List.of(new Item("Неизвестный", BigDecimal.ONE))));
        assertTrue(d.getItems().isEmpty()); assertTrue(d.getComment().contains("Неизвестный")); assertEquals(0,d.getTotalSum().signum());
    }
}
