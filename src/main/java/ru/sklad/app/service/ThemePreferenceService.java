package ru.sklad.app.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import ru.sklad.app.entity.User;
import ru.sklad.app.repository.UserRepository;
import java.util.Set;

@Service
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SELLER', 'SUPER_ADMIN')")
public class ThemePreferenceService {
    private final UserRepository users;
    private static final Set<String> SCHEMES = Set.of("corporate", "nature", "ocean", "sunset");
    public record Preference(String scheme, boolean dark) {}

    private User currentUser() {
        return users.findByUsername(SecurityContextHolder.getContext().getAuthentication().getName())
                .filter(User::isEnabled).orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("Account unavailable"));
    }

    @Transactional(readOnly = true)
    public Preference load() {
        String value = currentUser().getThemePreference();
        if (value == null) return new Preference("corporate", false);
        String[] parts = value.trim().split("\\s+");
        String scheme = SCHEMES.contains(parts[0]) ? parts[0] : "corporate";
        boolean dark = java.util.Arrays.asList(parts).contains("dark");
        return new Preference(scheme, dark);
    }

    @Transactional
    public void save(String scheme, boolean dark) {
        if (!SCHEMES.contains(scheme)) throw new IllegalArgumentException("Неизвестная цветовая схема");
        User user = currentUser();
        user.setThemePreference(scheme + (dark ? " dark" : ""));
        users.save(user);
    }
}
