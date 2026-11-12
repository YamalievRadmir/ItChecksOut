package ru.sklad.app.repository;
import ru.sklad.app.entity.AnimalGuide;
import ru.sklad.app.entity.Shop;
import ru.sklad.app.util.SecurityUtils;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
public interface AnimalGuideRepository extends ShopRepositoryBase<AnimalGuide, Long> {
 @org.springframework.security.access.prepost.PreAuthorize("#p0 != null and #p0.id == T(ru.sklad.app.util.SecurityUtils).currentShop().id")
    boolean existsByShopAndSpeciesAndTitle(Shop shop, String species, String title);
 default boolean existsBySpeciesAndTitle(String species, String title) { return existsByShopAndSpeciesAndTitle(SecurityUtils.currentShop(),species,title); }
 @Query("select g from AnimalGuide g where g.shop = :#{T(ru.sklad.app.util.SecurityUtils).currentShop()} and (:species is null or g.species = :species) "
 + "and (lower(g.title) like lower(concat('%', :query, '%')) or lower(g.content) like lower(concat('%', :query, '%')) "
 + "or lower(g.category) like lower(concat('%', :query, '%')) or lower(g.species) like lower(concat('%', :query, '%'))) order by g.species, g.category, g.title, g.id")
 List<AnimalGuide> searchArticles(@Param("query") String query, @Param("species") String species);
 @org.springframework.security.access.prepost.PreAuthorize("#p0 != null and #p0.id == T(ru.sklad.app.util.SecurityUtils).currentShop().id")
    List<AnimalGuide> findByShopAndAnimalTypeContainingIgnoreCaseOrderByAnimalTypeAscAgeGroupAscIdAsc(Shop shop, String animalType);
 default List<AnimalGuide> findByAnimalTypeContainingIgnoreCaseOrderByAnimalTypeAscAgeGroupAscIdAsc(String animalType) {
 return findByShopAndAnimalTypeContainingIgnoreCaseOrderByAnimalTypeAscAgeGroupAscIdAsc(SecurityUtils.currentShop(),animalType); }
 @org.springframework.security.access.prepost.PreAuthorize("#p0 != null and #p0.id == T(ru.sklad.app.util.SecurityUtils).currentShop().id")
    boolean existsByShopAndAnimalTypeAndAgeGroup(Shop shop, String animalType, String ageGroup);
 default boolean existsByAnimalTypeAndAgeGroup(String animalType, String ageGroup) { return existsByShopAndAnimalTypeAndAgeGroup(SecurityUtils.currentShop(),animalType,ageGroup); }
}