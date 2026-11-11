package ru.sklad.app;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import jakarta.persistence.EntityManager;
import ru.sklad.app.entity.Product;
import ru.sklad.app.repository.ProductRepository;
import ru.sklad.app.service.*;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(showSql=false) @Import({ProductService.class, ProductAlternativeService.class})
class ProductRelationsTest {
    @Autowired ProductRepository products;
    @Autowired ProductService service;
    @Autowired ProductAlternativeService suggestions;
    @Autowired EntityManager em;
    @MockitoBean PermissionService permissions;
    @MockitoBean AuditService audit;
    @BeforeEach void admin() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("admin","",AuthorityUtils.createAuthorityList("ROLE_ADMIN")));
        when(permissions.hasPermission(anyString())).thenReturn(true);
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }
    Product product(String name) {
        Product p=new Product(); p.setName(name); p.setStockQuantity(BigDecimal.TEN); p.setRetailPrice(BigDecimal.ONE);
        return products.saveAndFlush(p);
    }
    Product proposal(Product p) {
        Product n=new Product(); n.setId(p.getId()); n.setVersion(p.getVersion()); n.setName(p.getName());
        n.setStockQuantity(p.getStockQuantity()); n.setRetailPrice(p.getRetailPrice()); return n;
    }
    @Test void reverseLinksAreAddedRemovedAndAdditivesAreDirectional() {
        Product a=product("Корм"), b=product("Замена"), c=product("Витамины");
        Product change=proposal(a); change.getAlternativeProducts().add(b); change.getRecommendedAdditives().add(c);
        em.clear(); service.save(change); em.flush(); em.clear();
        assertEquals(Set.of(b.getId()),service.relations(a.getId()).alternatives());
        assertEquals(Set.of(a.getId()),service.relations(b.getId()).alternatives());
        assertEquals(Set.of(c.getId()),service.relations(a.getId()).additives());
        assertTrue(service.relations(c.getId()).additives().isEmpty());
        Product remove=proposal(products.findById(b.getId()).orElseThrow());
        em.clear(); service.save(remove); em.flush(); em.clear();
        assertTrue(service.relations(a.getId()).alternatives().isEmpty());
        assertTrue(service.relations(b.getId()).alternatives().isEmpty());
    }
    @Test void explicitReplacementWorksWithoutSimilarNamesAndRespectsCartStock() {
        Product a=product("Один"), b=product("Совсем другое");
        Product change=proposal(a); change.getAlternativeProducts().add(b);
        em.clear(); service.save(change); em.flush(); em.clear();
        assertEquals(b.getId(),suggestions.find(a.getId(),BigDecimal.ONE,Map.of()).orElseThrow().getId());
        assertTrue(suggestions.find(a.getId(),BigDecimal.ONE,Map.of(b.getId(),BigDecimal.TEN)).isEmpty());
    }
    @Test void sellerCannotChangeRelations() {
        Product a=product("Один"), b=product("Другой");
        Product change=proposal(a); change.getAlternativeProducts().add(b);
        em.clear(); SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("seller","",AuthorityUtils.createAuthorityList("ROLE_SELLER")));
        service.save(change); em.flush(); em.clear();
        assertTrue(products.findById(a.getId()).orElseThrow().getAlternativeProducts().isEmpty());
    }
    @Test void similarProductsAreNotSuggestedWithoutExplicitRelation() {
        Product a=product("Корм ПК-1"), b=product("Корм ПК-1 другой");
        a.setCategory("Корма"); b.setCategory("Корма"); products.saveAllAndFlush(List.of(a,b)); em.clear();
        assertTrue(suggestions.choices(a.getId(),BigDecimal.ONE,Map.of()).isEmpty());
    }
}
