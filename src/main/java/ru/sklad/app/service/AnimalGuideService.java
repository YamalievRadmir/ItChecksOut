package ru.sklad.app.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sklad.app.entity.AnimalGuide;
import ru.sklad.app.repository.AnimalGuideRepository;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class AnimalGuideService {
    public static final List<String> SPECIES = List.of("Куры", "Индейки", "Свиньи", "Коровы", "Бакалея/Добавки");
    public static final List<String> CATEGORIES = List.of("Рацион", "Болезни", "Набор веса", "Лечение");
    private final AnimalGuideRepository repository;
    private final AuditService audit;

    public AnimalGuideService(AnimalGuideRepository repository, AuditService audit) {
        this.repository = repository;
        this.audit = audit;
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'SELLER') and @permissionService.canAccess('animals')")
    public List<AnimalGuide> search(String query, String species) {
        return repository.searchArticles(query == null ? "" : query.trim(), species);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public AnimalGuide save(Long id, String species, String category, String title, String content) {
        if (species == null || species.isBlank() || species.trim().length() > 255 || category == null || !CATEGORIES.contains(category)
                || title == null || title.isBlank() || title.trim().length() > 255 || content == null || content.isBlank()) {
            throw new IllegalArgumentException("Выберите вид, категорию, заполните заголовок (до 255 символов) и текст статьи");
        }
        AnimalGuide guide = id == null ? new AnimalGuide() : repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Статья больше не существует"));
        guide.setSpecies(species.trim()); guide.setCategory(category);
        guide.setTitle(title.trim()); guide.setContent(content.trim());
        // Keep the required legacy columns populated for existing installations.
        guide.setAnimalType(species.trim()); guide.setAgeGroup(category); guide.setFeedingAdvice(content.trim());
        if (guide.getRecommendedProducts() == null) guide.setRecommendedProducts("");
        AnimalGuide saved = repository.save(guide);
        audit.safeLogAction(id == null ? "СОЗДАНИЕ" : "ИЗМЕНЕНИЕ", "Статья", saved.getTitle() + " (#" + saved.getId() + ")", species + "; " + category);
        return saved;
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(Long id) {
        AnimalGuide guide = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Статья больше не существует"));
        repository.delete(guide);
        audit.safeLogAction("УДАЛЕНИЕ", "Статья", guide.getTitle() + " (#" + id + ")", "Статья удалена из базы знаний");
    }
}
