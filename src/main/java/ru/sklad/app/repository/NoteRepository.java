package ru.sklad.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.sklad.app.entity.Note;

public interface NoteRepository extends ShopRepositoryBase<Note, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select n from Note n where n.shop = :#{T(ru.sklad.app.util.SecurityUtils).currentShop()} and n.id = :id")
    @org.springframework.security.access.prepost.PreAuthorize("#p0 != null and #p0.id == T(ru.sklad.app.util.SecurityUtils).currentShop()?.id")
    java.util.Optional<Note> findLockedByShopAndId(@org.springframework.data.repository.query.Param("shop") ru.sklad.app.entity.Shop shop, @org.springframework.data.repository.query.Param("id") Long id);
    default java.util.Optional<Note> findLockedById(@org.springframework.data.repository.query.Param("id") Long id) { return findLockedByShopAndId(ru.sklad.app.util.SecurityUtils.currentShop(), id); }
}
