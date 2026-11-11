package ru.sklad.app.service;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.*;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.bean.override.mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import ru.sklad.app.repository.AnimalGuideRepository;
import org.jsoup.Jsoup;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(showSql=false) @Import({SiteParserService.class,SiteParserServiceTest.Config.class})
class SiteParserServiceTest {
    @TestConfiguration static class Config { @Bean ObjectMapper mapper() { return new ObjectMapper(); } }
    @MockitoSpyBean SiteParserService parser;
    @MockitoBean AuditService audit;
    @Autowired AnimalGuideRepository guides;
    @Test void importsDynamicSiteAndDoesNotDuplicateOrOverwriteArticles() throws Exception {
        doReturn("<html><body><div id='root'></div></body></html>").when(parser).download("https://agro-korma-shop.ru/",false);
        doReturn("""
            {"faq":[{"question":"Почему у кур понос?","answer":"Проверьте воду и обратитесь к ветеринару.","topic":"Куры — болезни"},
            {"question":"Чем кормить свиней?","answer":"Используйте подходящий рацион.","topic":"Свиньи"},
            {"question":"  Почему у кур понос?  ","answer":"Повтор"}],"products":[{"name":"Не статья"}]}
            """).when(parser).download("https://agro-korma-shop.ru/api/content",true);
        assertEquals(2,parser.parseKnowledgeBase()); assertEquals(2,guides.count());
        var chicken=guides.findAll().stream().filter(a -> a.getSpecies().equals("Куры")).findFirst().orElseThrow();
        assertEquals("Болезни",chicken.getCategory()); assertNotNull(chicken.getFeedingAdvice());
        chicken.setContent("Ручная правка"); guides.saveAndFlush(chicken);
        assertEquals(0,parser.parseKnowledgeBase()); assertEquals("Ручная правка",guides.findById(chicken.getId()).orElseThrow().getContent());
    }
    @Test void htmlSectionsExcludeNavigationAndPreserveHeadings() {
        var articles=SiteParserService.extractHtml(Jsoup.parse("""
            <nav><h2>Реклама</h2><p>Ненужный текст меню сайта.</p></nav>
            <article><h1>Кормление кур</h1><p>Подбирайте рацион по возрасту птицы и обеспечьте постоянный доступ к чистой воде.</p>
            <h2>Набор веса</h2><p>Контролируйте привесы и следите за качеством используемого корма каждый день.</p></article>
            """));
        assertEquals(2,articles.size()); assertEquals("Кормление кур",articles.get(0).title());
        assertFalse(articles.toString().contains("Реклама"));
        assertEquals("Свиньи",SiteParserService.species("Рацион поросят","Козы, свиньи, кролики",""));
    }
    @Test void failedImportWritesNothing() throws Exception {
        doThrow(new java.io.IOException("Unavailable")).when(parser).download("https://agro-korma-shop.ru/",false);
        assertThrows(IllegalStateException.class,parser::parseKnowledgeBase); assertEquals(0,guides.count());
    }
}
