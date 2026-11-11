package ru.sklad.app;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import ru.sklad.app.config.ManagedUserDetailsManager;
import static org.junit.jupiter.api.Assertions.*;
class CredentialPersistenceTest {
    @Test void passwordChangeSurvivesManagerRecreation() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:accounts;DB_CLOSE_DELAY=-1", "sa", "");
        var encoder = new BCryptPasswordEncoder();
        var first = new ManagedUserDetailsManager(source);
        first.createUser(User.withUsername("test").password(encoder.encode("initial password")).roles("ADMIN").build());
        first.updateUser(User.withUserDetails(first.loadUserByUsername("test")).password(encoder.encode("changed password")).build());
        var second = new ManagedUserDetailsManager(source);
        String hash = second.loadUserByUsername("test").getPassword();
        assertTrue(encoder.matches("changed password", hash));
        assertFalse(encoder.matches("initial password", hash));
        assertEquals(java.util.List.of("test"), second.usernames());
    }
}
