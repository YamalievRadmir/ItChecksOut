package ru.sklad.app.repository;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.query.Param;
import ru.sklad.app.entity.*;
import java.util.*;
@NoRepositoryBean
public interface ShopRepositoryBase<T extends ShopOwned, ID> extends JpaRepository<T, ID> {
 @Query("select e from #{#entityName} e where e.shop = :shop and e.shop = :#{T(ru.sklad.app.util.SecurityUtils).currentShop()}")
 List<T> findAllByShop(@Param("shop") Shop shop);
 @Override @Query("select e from #{#entityName} e where e.shop = :#{T(ru.sklad.app.util.SecurityUtils).currentShop()}")
 List<T> findAll();
 @Override @Query("select e from #{#entityName} e where e.shop = :#{T(ru.sklad.app.util.SecurityUtils).currentShop()}")
 List<T> findAll(org.springframework.data.domain.Sort sort);
 @Override @Query("select e from #{#entityName} e where e.shop = :#{T(ru.sklad.app.util.SecurityUtils).currentShop()}")
 org.springframework.data.domain.Page<T> findAll(org.springframework.data.domain.Pageable pageable);
 @Override @Query("select e from #{#entityName} e where e.id = :id and e.shop = :#{T(ru.sklad.app.util.SecurityUtils).currentShop()}")
 Optional<T> findById(@Param("id") ID id);
 @Override @Query("select count(e) from #{#entityName} e where e.shop = :#{T(ru.sklad.app.util.SecurityUtils).currentShop()}")
 long count();
 @Override default boolean existsById(ID id) { return findById(id).isPresent(); }
 @Override @Query("select e from #{#entityName} e where e.id in :ids and e.shop = :#{T(ru.sklad.app.util.SecurityUtils).currentShop()}")
 List<T> findAllById(@Param("ids") Iterable<ID> ids);
 @Override default T getReferenceById(ID id) { return findById(id).orElseThrow(jakarta.persistence.EntityNotFoundException::new); }
 @Override default T getById(ID id) { return getReferenceById(id); }
 @Override default T getOne(ID id) { return getReferenceById(id); }
 @Override default void deleteById(ID id) { findById(id).ifPresent(this::delete); }
 @Override default void deleteAll() { deleteAll(findAll()); }
 @Override default void deleteAllInBatch() { deleteAll(); }
 @Override default void deleteAllInBatch(Iterable<T> entities) { deleteAll(entities); }
 @Override default void deleteAllByIdInBatch(Iterable<ID> ids) { ids.forEach(this::deleteById); }

 @Override default void deleteInBatch(Iterable<T> entities) { deleteAll(entities); }
 @Override default <S extends T> List<S> findAll(org.springframework.data.domain.Example<S> example) { throw new UnsupportedOperationException("Use shop-scoped queries"); }
 @Override default <S extends T> List<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Sort sort) { throw new UnsupportedOperationException("Use shop-scoped queries"); }
 @Override default <S extends T> org.springframework.data.domain.Page<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Pageable page) { throw new UnsupportedOperationException("Use shop-scoped queries"); }
 @Override default <S extends T> Optional<S> findOne(org.springframework.data.domain.Example<S> example) { throw new UnsupportedOperationException("Use shop-scoped queries"); }
 @Override default <S extends T> long count(org.springframework.data.domain.Example<S> example) { throw new UnsupportedOperationException("Use shop-scoped queries"); }
 @Override default <S extends T> boolean exists(org.springframework.data.domain.Example<S> example) { throw new UnsupportedOperationException("Use shop-scoped queries"); }
 @Override default <S extends T, R> R findBy(org.springframework.data.domain.Example<S> example, java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> query) { throw new UnsupportedOperationException("Use shop-scoped queries"); }
}