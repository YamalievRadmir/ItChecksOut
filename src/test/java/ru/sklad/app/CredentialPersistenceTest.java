package ru.sklad.app;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import ru.sklad.app.config.ManagedUserDetailsManager;
import ru.sklad.app.repository.UserRepository;
import ru.sklad.app.repository.ShopRepository;
import ru.sklad.app.entity.Shop;
import ru.sklad.app.util.SecurityUtils;
import jakarta.persistence.EntityManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(showSql = false)
@org.springframework.context.annotation.Import(ru.sklad.app.util.SecurityUtils.class)
@org.junit.jupiter.api.extension.ExtendWith(ru.sklad.app.TestShopContext.class)
class CredentialPersistenceTest {
    @Autowired UserRepository repository;
    @Autowired ShopRepository shops;
    @Autowired EntityManager em;
    @Test void passwordChangeSurvivesManagerRecreation() {
        Shop shop = new Shop(); shop.setName("Test"); shops.saveAndFlush(shop);
        var security = mock(SecurityUtils.class);
        when(security.requireCurrentShop()).thenReturn(shop);
        var encoder = new BCryptPasswordEncoder();
        var first = new ManagedUserDetailsManager(repository, security);
        first.createUser(User.withUsername("test").password(encoder.encode("initial password")).roles("ADMIN").build());
        first.updateUser(User.withUserDetails(first.loadUserByUsername("test")).password(encoder.encode("changed password")).build());
        repository.flush(); em.clear();
        var second = new ManagedUserDetailsManager(repository, security);
        String hash = second.loadUserByUsername("test").getPassword();
        assertTrue(encoder.matches("changed password", hash));
        assertFalse(encoder.matches("initial password", hash));
        assertEquals(java.util.List.of("test"), second.usernames());
        Shop other = new Shop(); other.setName("Other"); shops.saveAndFlush(other);
        when(security.requireCurrentShop()).thenReturn(other);
        assertTrue(second.usernames().isEmpty());
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> second.updateUser(User.withUsername("test").password(hash).roles("ADMIN").build()));
    }
}