package ru.sklad.app.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import ru.sklad.app.entity.User;
import ru.sklad.app.repository.UserRepository;
import ru.sklad.app.util.SecurityUtils;
import java.util.List;
import java.nio.charset.StandardCharsets;

@Service
@PreAuthorize("hasRole('ADMIN')")
@Transactional(readOnly = true)
public class ShopSellerService {
    private final UserRepository users;
    private final SecurityUtils security;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    public ShopSellerService(UserRepository users, SecurityUtils security, PasswordEncoder encoder, AuditService audit) {
        this.users = users; this.security = security; this.encoder = encoder; this.audit = audit;
    }
    public List<User> list() { return users.findAllByShopAndRoleOrderByUsernameAsc(security.requireCurrentShop(), "SELLER"); }
    @Transactional
    public void save(Long id, String username, String password) {
        var shop = security.requireCurrentShop();
        String login = username == null ? "" : username.trim();
        if (login.isBlank() || login.length() > 50) throw new IllegalArgumentException("Логин должен содержать от 1 до 50 символов");
        boolean changePassword = password != null && !password.isEmpty();
        if ((id == null && !changePassword) || (changePassword && (password.isBlank() || password.getBytes(StandardCharsets.UTF_8).length > 72)))
            throw new IllegalArgumentException("Введите пароль длиной до 72 байт UTF-8");
        if (changePassword) ru.sklad.app.util.PasswordPolicy.validate(password);
        User user;
        if (id == null) {
            user = new User(); user.setRole("SELLER"); user.setShop(shop);
        } else {
            user = users.findByIdAndShopAndRole(id, shop, "SELLER")
                    .orElseThrow(() -> new AccessDeniedException("Продавец не найден в вашем магазине"));
        }
        if (users.findByUsername(login).filter(other -> !other.getId().equals(id)).isPresent())
            throw new IllegalArgumentException("Логин уже занят");
        user.setUsername(login);
        if (changePassword) user.setPassword(encoder.encode(password));
        users.saveAndFlush(user);
        audit.safeLogAction(id == null ? "СОЗДАНИЕ" : "ИЗМЕНЕНИЕ", "Продавец", login,
                id == null ? "Создана учётная запись SELLER" : "Изменена учётная запись SELLER" );
    }
}