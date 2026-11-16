package ru.sklad.app;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.sklad.app.entity.*;
import ru.sklad.app.repository.*;
import ru.sklad.app.service.*;
import java.math.BigDecimal;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(showSql = false)
@org.junit.jupiter.api.extension.ExtendWith(ru.sklad.app.TestShopContext.class)
@Import({ru.sklad.app.util.SecurityUtils.class, DeliveryService.class})
class DeliveryBadgeTest {
    @Autowired jakarta.persistence.EntityManager entityManager;
    @Test void deliveryTimeSurvivesReloadAndEdit() {
        Customer c = new Customer(); c.setName("Time test"); c.setAddress("Address"); c.setPhone("89123456789");
        c = customers.saveAndFlush(c);
        Delivery d = delivery(c, LocalDate.now(), "New");
        d.setDeliveryTime(LocalTime.of(14, 37)); deliveries.saveAndFlush(d);
        Long id = d.getId(); entityManager.clear();
        d = deliveries.findById(id).orElseThrow(); assertEquals(LocalTime.of(14,37),d.getDeliveryTime());
        d.setDeliveryTime(LocalTime.of(18, 15)); deliveries.saveAndFlush(d); entityManager.clear();
        assertEquals(LocalTime.of(18,15),deliveries.findById(id).orElseThrow().getDeliveryTime());
    }
    @Autowired DeliveryRepository deliveries;
    @Autowired CustomerRepository customers;
    @Autowired DeliveryService service;
    @MockitoBean AuditService audit;

    private Delivery delivery(Customer customer, LocalDate date, String status) {
        Delivery d = new Delivery(); d.setCustomer(customer); d.setDeliveryDate(date);
        d.setDeliveryTime(LocalTime.MIDNIGHT); d.setPaymentMethod("Нал");
        d.setTotalSum(BigDecimal.ZERO); d.setStatus(status);
        return deliveries.saveAndFlush(d);
    }

    @Test void countsOnlyNewTodayAndDoesNotCompletePastDeliveriesOnRead() {
        Customer c = new Customer(); c.setName("Test"); c.setAddress("Test"); c.setPhone("123456789");
        c = customers.saveAndFlush(c);
        LocalDate today = LocalDate.now();
        Delivery active = delivery(c, today, "Новая");
        Delivery yesterday = delivery(c, today.minusDays(1), "Новая");
        delivery(c, today.plusDays(1), "Новая");
        delivery(c, today, "Выполнена"); delivery(c, today, "Отменена");
        assertEquals(1, service.countNewToday());
        service.list(); service.list(today.minusDays(1));
        assertEquals("Новая", deliveries.findById(yesterday.getId()).orElseThrow().getStatus());
        assertEquals("Новая", deliveries.findById(active.getId()).orElseThrow().getStatus());
        service.cancel(active.getId());
        assertEquals(0, service.countNewToday());
    }
}
