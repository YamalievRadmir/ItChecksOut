package ru.sklad.app.config;

import org.springframework.stereotype.Component;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import ru.sklad.app.entity.Shop;
import ru.sklad.app.entity.User;
import ru.sklad.app.repository.ShopRepository;
import ru.sklad.app.repository.UserRepository;

@Component
@Order(-100)
public class UserDataInitializer implements CommandLineRunner {
    private final ShopRepository shops;
    private final UserRepository users;
    private final JdbcTemplate jdbc;
    private final Environment environment;
    private final PasswordEncoder encoder;
    public UserDataInitializer(ShopRepository shops, UserRepository users, JdbcTemplate jdbc,
                               Environment environment, PasswordEncoder encoder) {
        this.shops = shops; this.users = users; this.jdbc = jdbc;
        this.environment = environment; this.encoder = encoder;
    }
    @Override @Transactional
    public void run(String... args) {
        Shop shop = shops.findById(1L).orElseGet(() -> {
            Shop first = new Shop(); first.setName("Мой магазин"); return shops.save(first);
        });
        for (String table : java.util.List.of("products", "sales", "sale_items", "delivery", "delivery_item", "customer", "supplies", "suppliers", "inventory", "note", "purchase_order", "purchase_order_item", "held_cart", "employees", "work_shifts", "animal_guides")) {
            jdbc.update("UPDATE " + table + " SET shop_id = ? WHERE shop_id IS NULL", shop.getId());
        }
        String schema = jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<String>) connection -> {
            String current = connection.getSchema(); return current == null ? connection.getCatalog() : current;
        });
        // Replace old global unique constraints with the shop-scoped constraints from the mappings.
        for (var entry : java.util.Map.of("products", "barcode", "customer", "phone").entrySet()) {
            var constraints = jdbc.queryForList("SELECT tc.CONSTRAINT_NAME FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc "
                    + "JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE k ON tc.CONSTRAINT_NAME=k.CONSTRAINT_NAME "
                    + "AND tc.TABLE_SCHEMA=k.TABLE_SCHEMA AND tc.TABLE_NAME=k.TABLE_NAME "
                    + "WHERE UPPER(tc.TABLE_NAME)=? AND tc.CONSTRAINT_TYPE='UNIQUE' AND tc.TABLE_SCHEMA=? "
                    + "GROUP BY tc.CONSTRAINT_NAME HAVING COUNT(*)=1 AND MAX(UPPER(k.COLUMN_NAME))=?",
                    String.class, entry.getKey().toUpperCase(java.util.Locale.ROOT), schema, entry.getValue().toUpperCase(java.util.Locale.ROOT));
            for (String constraint : constraints) {
                if (!constraint.matches("[A-Za-z0-9_]+")) throw new IllegalStateException("Invalid constraint name");
                boolean mysql = jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Boolean>) connection ->
                        connection.getMetaData().getDatabaseProductName().equalsIgnoreCase("MySQL"));
                jdbc.execute("ALTER TABLE " + entry.getKey() + (mysql ? " DROP INDEX " : " DROP CONSTRAINT ") + constraint);
            }
        }
        users.findByUsername("demo").filter(user -> user.getShop() != null && "Демо-магазин".equals(user.getShop().getName()))
                .ifPresent(user -> { user.getShop().setDemo(true); shops.save(user.getShop()); });
        boolean legacy;
        try (var connection = jdbc.getDataSource().getConnection();
             var tables = connection.getMetaData().getTables(connection.getCatalog(), connection.getSchema(), "%", new String[]{"TABLE"})) {
            legacy = false;
            while (tables.next()) if ("users".equalsIgnoreCase(tables.getString("TABLE_NAME"))) legacy = true;
        } catch (java.sql.SQLException ex) { throw new IllegalStateException("Cannot inspect legacy users", ex); }
        if (legacy) jdbc.query("SELECT u.username, u.password, u.enabled, a.authority FROM users u JOIN authorities a ON a.username=u.username", rs -> {
            String username = rs.getString("username");
            if (!users.existsByUsername(username)) {
                User user = new User(); user.setUsername(username); user.setPassword(rs.getString("password"));
                user.setEnabled(rs.getBoolean("enabled"));
                user.setRole(rs.getString("authority").replaceFirst("^ROLE_", ""));
                user.setShop(shop); users.save(user);
            }
        });
        for (String role : java.util.List.of("admin", "seller", "super_admin")) {
            if (users.existsByUsername(role)) continue;
            String password = environment.getProperty("app.bootstrap." + role.replace('_', '-') + "-password");
            if (password == null || password.isBlank()) {
                if (role.equals("super_admin")) {
                    throw new IllegalStateException("Set APP_BOOTSTRAP_SUPER_ADMIN_PASSWORD before first startup");
                } else continue;
            }
            ru.sklad.app.util.PasswordPolicy.validate(password);
            User user = new User(); user.setUsername(role); user.setPassword(encoder.encode(password));
            user.setRole(role.toUpperCase(java.util.Locale.ROOT));
            user.setShop(role.equals("super_admin") ? null : shop); users.save(user);
        }
    }
}