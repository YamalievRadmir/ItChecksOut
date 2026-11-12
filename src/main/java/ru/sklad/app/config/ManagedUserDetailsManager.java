package ru.sklad.app.config;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.provisioning.UserDetailsManager;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import ru.sklad.app.repository.UserRepository;
import ru.sklad.app.util.SecurityUtils;
import java.util.List;

@Service
@Transactional
public class ManagedUserDetailsManager implements UserDetailsManager {
    @org.springframework.beans.factory.annotation.Value("${app.demo-login.enabled:false}") private boolean demoEnabled;
    private final UserRepository repository;
    private final SecurityUtils security;
    public ManagedUserDetailsManager(UserRepository repository, SecurityUtils security) {
        this.repository = repository; this.security = security;
    }
    @Override
    public UserDetails loadUserByUsername(String username) {
        var user = repository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
        boolean active = user.isEnabled() && (user.getShop() == null || !user.getShop().isDemo() || demoEnabled) && ("SUPER_ADMIN".equals(user.getRole())
                ? user.getShop() == null : user.getShop() != null && user.getShop().isActive());
        return org.springframework.security.core.userdetails.User.withUsername(user.getUsername())
                .password(user.getPassword()).roles(user.getRole()).disabled(!active).build();
    }
    public List<String> usernames() {
        return repository.findAllByShop(security.requireCurrentShop()).stream()
                .map(ru.sklad.app.entity.User::getUsername).sorted().toList();
    }
    private ru.sklad.app.entity.User owned(String username) {
        var shop = security.requireCurrentShop();
        var user = repository.findByUsername(username).orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
        if (user.getShop() == null || !shop.getId().equals(user.getShop().getId()))
            throw new AccessDeniedException("Account belongs to another shop");
        return user;
    }
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    @Override public void createUser(UserDetails details) {
        var user = new ru.sklad.app.entity.User();
        user.setShop(security.requireCurrentShop());
        user.setUsername(details.getUsername());
        apply(user, details);
        repository.save(user);
    }
    private void apply(ru.sklad.app.entity.User user, UserDetails details) {
        String role = details.getAuthorities().iterator().next().getAuthority().replaceFirst("^ROLE_", "");
        if (!java.util.Set.of("ADMIN", "SELLER", "LOADER").contains(role))
            throw new AccessDeniedException("Unsupported shop role");
        if (details.getPassword() == null || !details.getPassword().matches("\\$2[aby]\\$[0-9]{2}\\$[./A-Za-z0-9]{53}"))
            throw new IllegalArgumentException("BCrypt password hash required");
        user.setRole(role); user.setPassword(details.getPassword()); user.setEnabled(details.isEnabled());
    }
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    @Override public void updateUser(UserDetails details) {
        var user = owned(details.getUsername()); apply(user, details); repository.save(user);
    }
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    @Override public void deleteUser(String username) { repository.delete(owned(username)); }
    @Override public boolean userExists(String username) { return repository.existsByUsername(username); }
    @Override public void changePassword(String oldPassword, String newPassword) {
        var user = owned(SecurityContextHolder.getContext().getAuthentication().getName());
        if (!new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().matches(oldPassword, user.getPassword()))
            throw new org.springframework.security.authentication.BadCredentialsException("Invalid password");
        user.setPassword(newPassword); repository.save(user);
    }
}