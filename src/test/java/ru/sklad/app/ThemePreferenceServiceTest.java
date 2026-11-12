package ru.sklad.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import ru.sklad.app.entity.User;
import ru.sklad.app.repository.UserRepository;
import ru.sklad.app.service.ThemePreferenceService;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ThemePreferenceServiceTest {
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    @Test void keepsModeAndAccentSeparateForEachAccount() {
        var repository = mock(UserRepository.class);
        User first = new User(); User second = new User();
        when(repository.findByUsername("first")).thenReturn(Optional.of(first));
        when(repository.findByUsername("second")).thenReturn(Optional.of(second));
        var service = new ThemePreferenceService(repository);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("first", ""));
        assertEquals(new ThemePreferenceService.Preference("corporate", false), service.load());
        service.save("ocean", true);
        assertEquals(new ThemePreferenceService.Preference("ocean", true), service.load());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("second", ""));
        assertEquals(new ThemePreferenceService.Preference("corporate", false), service.load());
        service.save("nature", false);
        assertEquals("ocean dark", first.getThemePreference());
        assertEquals("nature", second.getThemePreference());
        assertThrows(IllegalArgumentException.class, () -> service.save("invalid", true));
    }
}
