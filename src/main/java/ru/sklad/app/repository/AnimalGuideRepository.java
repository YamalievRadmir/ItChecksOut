package ru.sklad.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.sklad.app.entity.AnimalGuide;
import java.util.List;

public interface AnimalGuideRepository extends JpaRepository<AnimalGuide, Long> {
    boolean existsBySpeciesAndTitle(String species, String title);
    @org.springframework.data.jpa.repository.Query("select g from AnimalGuide g where (:species is null or g.species = :species) "
            + "and (lower(g.title) like lower(concat('%', :query, '%')) or lower(g.content) like lower(concat('%', :query, '%')) "
            + "or lower(g.category) like lower(concat('%', :query, '%')) or lower(g.species) like lower(concat('%', :query, '%'))) "
            + "order by g.species, g.category, g.title, g.id")
    List<AnimalGuide> searchArticles(@org.springframework.data.repository.query.Param("query") String query,
                                    @org.springframework.data.repository.query.Param("species") String species);
    List<AnimalGuide> findByAnimalTypeContainingIgnoreCaseOrderByAnimalTypeAscAgeGroupAscIdAsc(String animalType);
    boolean existsByAnimalTypeAndAgeGroup(String animalType, String ageGroup);
}
