package ru.sklad.app.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.sklad.app.entity.AnimalGuide;
import ru.sklad.app.repository.AnimalGuideRepository;

import java.nio.file.Path;

@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "app.demo-data.enabled", havingValue = "true")
public class DataInitializer implements CommandLineRunner {
    private final ru.sklad.app.service.DemoDataService demoData;
    @org.springframework.beans.factory.annotation.Value("${app.demo-data.reset:false}")
    private boolean resetDemoData;
    @org.springframework.beans.factory.annotation.Value("${app.demo-data.generate:true}")
    private boolean generateDemoHistory;
    private final AnimalGuideRepository animalGuides;
    private final ru.sklad.app.repository.NoteRepository notes;

    public DataInitializer(ru.sklad.app.service.DemoDataService demoData, AnimalGuideRepository animalGuides,
                           ru.sklad.app.repository.NoteRepository notes) {
        this.demoData = demoData;
        this.animalGuides = animalGuides;
        this.notes = notes;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(String... args) throws Exception {
        initializeAnimalGuides();
        initializeKnowledgeArticles();
        initializeNotes();
        demoData.initialize(Path.of("price.xlsx"), resetDemoData, generateDemoHistory);
    }

    private void initializeNotes() {
        if (notes.count() > 0) return;
        seedNote("ИНСТРУКЦИЯ", "Как работать с кассой",
                "1. Выберите товар. 2. Проверьте остаток. 3. Нажмите Оплатить.");
        seedNote("ДОЛГ", "Долг — Иван Петров",
                "Иван Петров — 5000 руб. за комбикорм, обещал вернуть в пятницу");

    }

    private void seedNote(String type, String title, String content) {
        var note = new ru.sklad.app.entity.Note();
        note.setNoteType(type); note.setTitle(title); note.setContent(content); notes.save(note);
    }

    private void initializeAnimalGuides() {
        if (animalGuides.count() > 0) return;
        seedGuide("Куры-несушки", "Взрослые",
                "Для хорошей яйценоскости необходим корм с высоким содержанием кальция. Рекомендуем ПК-1. "
                        + "Добавляйте ракушку и мел для укрепления скорлупы.", "ПК-1, ракушка, мел");
        seedGuide("Бройлеры", "Молодняк (0-4 недели)",
                "Используйте стартовые корма ПК-5. Следите за постоянным доступом к чистой воде.", "Стартовые корма ПК-5");
        seedGuide("Кролики", "Взрослые",
                "Основной рацион — комбикорм для кроликов и сено. Избегайте резкой смены корма.",
                "Комбикорм для кроликов, сено");
    }

    private void initializeKnowledgeArticles() {
        // Extend legacy records in place, preserving age groups and product recommendations.
        for (AnimalGuide guide : animalGuides.findAll()) {
            if (guide.getSpecies() == null) {
                String oldType = java.util.Objects.toString(guide.getAnimalType(), "Другие");
                guide.setSpecies(oldType.startsWith("Куры") || oldType.startsWith("Бройлер") ? "Куры" : oldType);
            }
            if (guide.getCategory() == null) guide.setCategory("Рацион");
            if (guide.getTitle() == null) {
                String title = guide.getAnimalType() + " — " + guide.getAgeGroup();
                guide.setTitle(title.substring(0, Math.min(255, title.length())));
            }
            if (guide.getContent() == null) {
                String text = java.util.Objects.toString(guide.getFeedingAdvice(), "");
                if (guide.getRecommendedProducts() != null && !guide.getRecommendedProducts().isBlank())
                    text += "\n\nРекомендуемые корма и категории: " + guide.getRecommendedProducts();
                guide.setContent(text);
            }
            animalGuides.save(guide);
        }
        seedArticle("Куры", "Рацион", "Кормление несушек",
                "Уточните возраст птицы и назначение корма. Подбирайте рацион по возрастной маркировке и инструкции производителя. "
                        + "Для несушек проверьте назначение выбранного ПК-1. Ракушку, мел и другие добавки предлагайте с учетом состава основного корма и указаний на упаковке.");
        seedArticle("Свиньи", "Набор веса", "Почему свиньи плохо набирают вес?",
                "Уточните возраст, используемый корм и режим кормления. Запишите наблюдения владельца и проверьте, соответствует ли корм возрастной группе. "
                        + "Премиксы ищите в разделе «Бакалея/Добавки» и сверяйте с инструкцией производителя. Причину плохого набора веса и необходимость лечения определяет ветеринар.");
        seedArticle("Индейки", "Болезни", "Что уточнить при обращении с жалобой на понос?",
                "Запишите возраст птицы, когда появилась проблема и менялся ли корм. Не назначайте лекарства или дозировки по описанию продавцу. "
                        + "Предложите владельцу обратиться к ветеринару и подготовить сведения о текущем рационе.");
        seedArticle("Бакалея/Добавки", "Рацион", "Витамины и премиксы: памятка продавцу",
                "Перед подбором уточните вид и возраст животных, название основного корма и уже используемые добавки. "
                        + "Витамины и премиксы подбирайте по назначению на этикетке. Способ применения и дозировку сверяйте с инструкцией конкретного продукта.");
    }

    private void seedArticle(String species, String category, String title, String content) {
        if (animalGuides.existsBySpeciesAndTitle(species, title)) return;
        AnimalGuide guide = new AnimalGuide(); guide.setSpecies(species); guide.setCategory(category);
        guide.setTitle(title); guide.setContent(content);
        guide.setAnimalType(species); guide.setAgeGroup(category); guide.setFeedingAdvice(content); guide.setRecommendedProducts("");
        animalGuides.save(guide);
    }

    private void seedGuide(String animalType, String age, String advice, String products) {
        if (animalGuides.existsByAnimalTypeAndAgeGroup(animalType, age)) return;
        AnimalGuide guide = new AnimalGuide();
        guide.setAnimalType(animalType);
        guide.setAgeGroup(age);
        guide.setFeedingAdvice(advice);
        guide.setRecommendedProducts(products);
        animalGuides.save(guide);
    }

}
