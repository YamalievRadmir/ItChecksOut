package ru.sklad.app.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import ru.sklad.app.entity.Shop;
import ru.sklad.app.entity.User;
import ru.sklad.app.repository.ShopRepository;
import ru.sklad.app.repository.UserRepository;

@Service
public class ShopAdminService {
    private final ShopRepository shops;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    public ShopAdminService(ShopRepository shops, UserRepository users, PasswordEncoder encoder) {
        this.shops = shops; this.users = users; this.encoder = encoder;
    }
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Transactional(readOnly = true)
    public java.util.List<Shop> list() { return shops.findAll(); }
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Transactional
    public Shop create(String name, String username, String password) {
        if (name == null || name.isBlank() || username == null || username.isBlank()
                || username.length() > 50 || password == null || password.isBlank()
                || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
            throw new IllegalArgumentException("Заполните название, логин и пароль (до 72 байт)");
        if (users.existsByUsername(username.trim())) throw new IllegalArgumentException("Логин уже занят");
        ru.sklad.app.util.PasswordPolicy.validate(password);
        Shop shop = new Shop(); shop.setName(name.trim()); shops.save(shop);
        User user = new User(); user.setUsername(username.trim()); user.setPassword(encoder.encode(password));
        user.setRole("ADMIN"); user.setShop(shop); users.save(user);
        return shop;
    }
}