package ru.sklad.app.service;

import com.fasterxml.jackson.databind.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.sklad.app.repository.AnimalGuideRepository;
import ru.sklad.app.entity.AnimalGuide;
import java.io.IOException;
import java.net.URI;
import java.util.*;

@Service
public class SiteParserService {
    private static final String SITE="https://agro-korma-shop.ru";
    public record Article(String title,String content,String topic) {}
    private final AnimalGuideRepository guides;
    private final ObjectMapper mapper;
    private final AuditService audit;
    private final TransactionTemplate transaction;
    public SiteParserService(AnimalGuideRepository guides,ObjectMapper mapper,AuditService audit,PlatformTransactionManager tx) {
        this.guides=guides; this.mapper=mapper; this.audit=audit; this.transaction=new TransactionTemplate(tx);
    }
    protected String download(String url,boolean json) throws IOException {
        return Jsoup.connect(url).userAgent("SkladKnowledgeImporter/1.0").timeout(15000).maxBodySize(4*1024*1024)
                .followRedirects(false).ignoreContentType(json).execute().body();
    }
    @PreAuthorize("hasRole('ADMIN')")
    public synchronized int parseKnowledgeBase() {
        List<Article> articles=new ArrayList<>();
        try {
            Document home=Jsoup.parse(download(SITE+"/",false),SITE+"/");
            articles.addAll(extractHtml(home));
            // The real site renders FAQ from this public JSON endpoint, not from static HTML.
            try { articles.addAll(extractJson(download(SITE+"/api/content",true))); }
            catch(IOException exception) {
                org.slf4j.LoggerFactory.getLogger(getClass()).warn("Публичный JSON сайта недоступен, используется HTML",exception);
            }
            if(articles.isEmpty()) {
                Set<String> visited=new LinkedHashSet<>();
                for(Element link:home.select("a[href]")) {
                    String url=link.absUrl("href").replaceAll("#.*$","");
                    if(!knowledgeLink(url) || !visited.add(url)) continue;
                    articles.addAll(extractHtml(Jsoup.parse(download(url,false),url)));
                    if(visited.size()>=20) break;
                }
            }
        } catch(IOException exception) { throw new IllegalStateException("Не удалось загрузить базу знаний с сайта. Повторите позже.",exception); }
        if(articles.isEmpty()) throw new IllegalStateException("На сайте не найдены статьи для импорта");
        Integer count=transaction.execute(status -> store(articles));
        return count==null?0:count;
    }
    static boolean knowledgeLink(String url) {
        try {
            URI uri=URI.create(url);
            return "https".equals(uri.getScheme()) && "agro-korma-shop.ru".equals(uri.getHost())
                    && (uri.getPort()==-1 || uri.getPort()==443) && uri.getQuery()==null
                    && uri.getPath().matches("(?i).*(article|blog|knowledge|guide|faq|feeding|stati|sovet).*" );
        } catch(IllegalArgumentException ignored) { return false; }
    }
    static String clean(String value) {
        if(value==null) return "";
        Document doc=Jsoup.parseBodyFragment(value); doc.select("script,style,nav,header,footer,form").remove();
        if(!value.contains("<")) return value.trim();
        return doc.body().wholeText().trim();
    }
    List<Article> extractJson(String json) throws IOException {
        JsonNode root=mapper.readTree(json); List<Article> articles=new ArrayList<>();
        for(JsonNode item:root.path("faq")) {
            String title=clean(item.path("question").asText()), text=clean(item.path("answer").asText());
            if(!title.isBlank() && !text.isBlank()) articles.add(new Article(title,text,item.path("topic").asText()));
        }
        return articles;
    }
    static List<Article> extractHtml(Document document) {
        Document doc=document.clone(); doc.select("script,style,nav,header,footer,form,aside").remove();
        var blocks=doc.select("article, .article-content, .post-content, .entry-content");
        if(blocks.isEmpty()) { Element main=doc.selectFirst("main"); if(main!=null) blocks.add(main); }
        Map<String,Article> result=new LinkedHashMap<>();
        for(Element block:blocks) {
            String title=doc.title(); StringBuilder body=new StringBuilder();
            for(Element element:block.select("h1,h2,h3,p,li")) {
                if(element.tagName().matches("h[123]")) {
                    addSection(result,title,body.toString()); title=element.text(); body.setLength(0);
                } else if(!element.text().isBlank()) body.append(element.text()).append('\n');
            }
            addSection(result,title,body.toString());
        }
        return new ArrayList<>(result.values());
    }
    private static void addSection(Map<String,Article> result,String title,String text) {
        if(title!=null && !title.isBlank() && text.trim().length()>=40)
            result.putIfAbsent(key(title),new Article(title.trim(),text.trim(),""));
    }
    private static String key(String value) { return Objects.toString(value,"").trim().replaceAll("\\s+"," ").toLowerCase(Locale.ROOT).replace('ё','е'); }
    static String species(String title,String topic,String content) {
        for(String part:List.of(title,topic,content)) {
            String s=key(part);
            List<String> found=new ArrayList<>();
            String[][] rules={{"Куры","кур|цыпля|несуш|бройлер"},{"Индейки","индюк|индю|индей"},{"Гуси и утки","гус|гусен|утк|утят"},
                    {"Коровы","коров|крс|телят|телен|быч|сухосто"},{"Свиньи","свин|порося|поросен"},{"Кролики","крол"},{"Козы","коз"},{"Овцы","овец|овц|ягнят"},{"Перепела","перепел"}};
            for(var rule:rules) if(java.util.regex.Pattern.compile("(?<![\\p{L}])(?:"+rule[1]+")").matcher(s).find()) found.add(rule[0]);
            if(found.size()==1) return found.get(0);
            if(found.size()>1) return "Общие";
            if(s.contains("птиц")) return "Птица";
        }
        return "Общие";
    }
    static String category(String title,String topic,String content) {
        String heading=key(title+" "+topic), all=heading+" "+key(content);
        if(heading.matches(".*(лечени|лечить|терапи).*")) return "Лечение";
        if(heading.matches(".*(болез|понос|профилакти|здоров|симптом).*")) return "Болезни";
        if(heading.matches(".*(вес|привес|откорм|масс).*")) return "Набор веса";
        if(all.matches("(?s).*(болез|понос|симптом).*")) return "Болезни";
        return "Рацион";
    }
    private int store(List<Article> articles) {
        Set<String> existing=new HashSet<>(); guides.findAll().forEach(a -> existing.add(key(a.getTitle())));
        int count=0;
        for(Article article:articles) {
            String title=article.title().trim(); if(title.length()>255) title=title.substring(0,255);
            if(article.content().isBlank() || !existing.add(key(title))) continue;
            AnimalGuide guide=new AnimalGuide(); guide.setTitle(title); guide.setContent(article.content());
            guide.setSpecies(species(title,article.topic(),article.content())); guide.setCategory(category(title,article.topic(),article.content()));
            guide.setAnimalType(guide.getSpecies()); guide.setAgeGroup(guide.getCategory());
            guide.setFeedingAdvice(article.content()); guide.setRecommendedProducts(""); guides.save(guide); count++;
        }
        guides.flush(); audit.safeLogAction("СОЗДАНИЕ","База знаний",SITE,"Импортировано статей: "+count);
        return count;
    }
}
