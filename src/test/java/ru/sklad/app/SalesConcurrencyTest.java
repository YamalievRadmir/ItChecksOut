package ru.sklad.app;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.sklad.app.repository.*;
import ru.sklad.app.service.*;
import ru.sklad.app.entity.Product;
import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(showSql = false)
@Import(SalesService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SalesConcurrencyTest {
    @Autowired ProductRepository products;
    @Autowired SaleRepository sales;
    @Autowired SalesService service;
    @MockitoBean PermissionService permissions;
    @MockitoBean AuditService audit;

    @Test void onlyOneSellerCanBuyLastUnit() throws Exception {
        Product product = new Product();
        product.setName("Last unit"); product.setStockQuantity(BigDecimal.ONE);
        product.setRetailPrice(BigDecimal.TEN);
        Long id = products.saveAndFlush(product).getId();
        long before = sales.count();
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        Callable<Boolean> pay = () -> {
            ready.countDown(); start.await();
            try { service.conductSale(Map.of(id, BigDecimal.ONE), Map.of(id, BigDecimal.TEN)); return true; }
            catch (InsufficientStockException expected) { return false; }
        };
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(pay); var second = pool.submit(pay);
            assertTrue(ready.await(10, TimeUnit.SECONDS)); start.countDown();
            assertNotEquals(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
            assertEquals(0, products.findById(id).orElseThrow().getStockQuantity().signum());
            assertEquals(before + 1, sales.count());
        } finally { start.countDown(); pool.shutdownNow(); }
    }

    @Test void staleProductCannotRestoreSoldStock() {
        Product product = new Product(); product.setName("Stale editor");
        product.setStockQuantity(BigDecimal.ONE); product.setRetailPrice(BigDecimal.TEN);
        product = products.saveAndFlush(product);
        Product stale = products.findById(product.getId()).orElseThrow();
        service.conductSale(Map.of(product.getId(), BigDecimal.ONE), Map.of(product.getId(), BigDecimal.TEN));
        assertThrows(org.springframework.dao.OptimisticLockingFailureException.class, () -> products.saveAndFlush(stale));
    }
}
