package ru.sklad.app;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.sklad.app.entity.Product;
import ru.sklad.app.repository.ProductRepository;
import ru.sklad.app.service.*;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
@DataJpaTest(showSql=false)
@org.junit.jupiter.api.extension.ExtendWith(ru.sklad.app.TestShopContext.class) @Import({ru.sklad.app.util.SecurityUtils.class, ProductRecommendationsInitializer.class})
class ProductRecommendationsInitializerTest {
    @Autowired ProductRecommendationsInitializer initializer;
    @Autowired ProductRepository products;
    @Autowired EntityManager em;
    @MockitoBean AuditService audit;
    Product product(String name,String category) {
        Product p=new Product(); p.setName(name); p.setCategory(category); p.setRetailPrice(BigDecimal.ONE);
        return products.saveAndFlush(p);
    }
    @Test void seedsSymmetricExactCodesAndFeedAdditivesAndSkipsSecondRun() {
        Product a=product("ПК-1 Глазов","Корма"), b=product("ПК 1 Оренбург","Корма"), different=product("ПК-10 Глазов","Корма");
        Product grain=product("Пшеница","Бакалея"), additive=product("Ракушка","Добавки");
        em.clear(); initializer.initializeIfEmpty(); em.flush(); em.clear();
        var loaded=products.findById(a.getId()).orElseThrow();
        assertEquals(Set.of(b.getId()),loaded.getAlternativeProducts().stream().map(Product::getId).collect(java.util.stream.Collectors.toSet()));
        assertTrue(products.findById(b.getId()).orElseThrow().getAlternativeProducts().stream().anyMatch(p -> p.getId().equals(a.getId())));
        assertTrue(products.findById(different.getId()).orElseThrow().getAlternativeProducts().isEmpty());
        assertTrue(loaded.getRecommendedAdditives().stream().anyMatch(p -> p.getId().equals(additive.getId())));
        assertTrue(products.findById(grain.getId()).orElseThrow().getRecommendedAdditives().isEmpty());
        loaded.getRecommendedAdditives().clear(); em.flush(); em.clear(); initializer.initializeIfEmpty(); em.flush(); em.clear();
        assertTrue(products.findById(a.getId()).orElseThrow().getRecommendedAdditives().isEmpty());
    }
    @Test void anyManualAlternativePreventsAllInitialization() {
        Product a=product("ПК-1 А","Корма"),b=product("ПК-1 Б","Корма"); product("Мел","Добавки");
        a.getAlternativeProducts().add(b); products.flush(); em.clear(); initializer.initializeIfEmpty();
        assertTrue(products.findById(a.getId()).orElseThrow().getRecommendedAdditives().isEmpty());
        assertTrue(products.findById(b.getId()).orElseThrow().getAlternativeProducts().isEmpty());
    }
}
