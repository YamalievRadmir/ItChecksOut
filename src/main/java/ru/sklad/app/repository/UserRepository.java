package ru.sklad.app.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.sklad.app.entity.User;
import ru.sklad.app.entity.Shop;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    // Lifecycle callbacks must resolve the shop without triggering another flush.
    @org.springframework.data.jpa.repository.Query("select u from User u left join fetch u.shop where u.username = :username")
    @org.springframework.data.jpa.repository.QueryHints(@jakarta.persistence.QueryHint(name = "org.hibernate.flushMode", value = "COMMIT"))
    Optional<User> findForSecurityByUsername(@org.springframework.data.repository.query.Param("username") String username);
    boolean existsByUsername(String username);
    List<User> findAllByShop(Shop shop);
    List<User> findAllByShopAndRoleOrderByUsernameAsc(Shop shop, String role);
    Optional<User> findByIdAndShopAndRole(Long id, Shop shop, String role);
}