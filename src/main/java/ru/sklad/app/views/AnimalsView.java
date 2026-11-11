package ru.sklad.app.views;

import com.vaadin.flow.component.accordion.Accordion;
import com.vaadin.flow.component.button.*;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.*;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.textfield.*;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.*;
import jakarta.annotation.security.RolesAllowed;
import ru.sklad.app.entity.AnimalGuide;
import ru.sklad.app.service.AnimalGuideService;
import ru.sklad.app.util.RoleAccess;
import java.util.*;

@Route(value = "animals", layout = MainLayout.class)
@PageTitle("Справочник — Всё сходится!")
@RolesAllowed({"ADMIN", "SELLER"})
public class AnimalsView extends VerticalLayout {
    private final AnimalGuideService service;
    private final TextField search = new TextField();
    private final TabSheet tabs = new TabSheet();
    private final List<String> species = new ArrayList<>();
    private final List<Div> contents = new ArrayList<>();

    public AnimalsView(AnimalGuideService service) {
        this.service = service; setSizeFull(); getStyle().set("overflow", "auto");
        search.setPlaceholder("Поиск по всем статьям: вес, понос, витамины...");
        search.setAriaLabel("Поиск по всей базе знаний"); search.setClearButtonVisible(true);
        search.setPrefixComponent(VaadinIcon.SEARCH.create()); search.setWidthFull();
        search.setValueChangeMode(ValueChangeMode.LAZY);
        HorizontalLayout toolbar = new HorizontalLayout(search); toolbar.setWidthFull();
        toolbar.setFlexGrow(1, search); toolbar.setAlignItems(Alignment.CENTER); toolbar.addClassName("app-toolbar");
        if (RoleAccess.isAdmin()) {
            Button create = new Button("Добавить статью", VaadinIcon.PLUS.create(), e -> openEditor(null));
            create.addThemeVariants(ButtonVariant.LUMO_PRIMARY); toolbar.add(create);
        }
        var kinds = new LinkedHashSet<>(AnimalGuideService.SPECIES);
        service.search("", null).stream().map(AnimalGuide::getSpecies).filter(Objects::nonNull).forEach(kinds::add);
        species.add(null); species.addAll(kinds);
        for (String kind : species) {
            Div content = new Div(); content.addClassName("app-card"); content.setWidthFull(); contents.add(content);
            tabs.add(kind == null ? "Все" : kind, content);
        }
        tabs.setWidthFull();
        tabs.addSelectedChangeListener(e -> refresh());
        search.addValueChangeListener(e -> { tabs.setSelectedIndex(0); refresh(); });
        add(new H2("База знаний продавца"), toolbar,
                new Paragraph("Введите запрос для поиска по всем видам животных или выберите вкладку."), tabs);
        refresh();
    }
    private void refresh() {
        int index = tabs.getSelectedIndex(); if (index < 0) return;
        Div content = contents.get(index); content.removeAll();
        var articles = service.search(search.getValue(), species.get(index));
        content.add(new Paragraph(articles.isEmpty() ? "Статьи не найдены" : "Найдено статей: " + articles.size()));
        Accordion accordion = new Accordion(); accordion.setWidthFull();
        for (AnimalGuide article : articles) {
            Span category = new Span(article.getSpecies() + " · " + article.getCategory()); category.addClassName("metric-label");
            Paragraph text = new Paragraph(article.getContent()); text.getStyle().set("white-space", "pre-wrap").set("overflow-wrap", "anywhere");
            VerticalLayout body = new VerticalLayout(category, text); body.setPadding(false);
            if (RoleAccess.isAdmin()) {
                Button edit = new Button("Редактировать", e -> openEditor(article));
                Button delete = new Button("Удалить", e -> confirmDelete(article)); delete.addThemeVariants(ButtonVariant.LUMO_ERROR);
                body.add(new HorizontalLayout(edit, delete));
            }
            accordion.add(article.getTitle(), body);
        }
        accordion.close(); content.add(accordion);
    }
    private void openEditor(AnimalGuide article) {
        if (!RoleAccess.isAdmin()) return;
        Dialog dialog = new Dialog(); dialog.setHeaderTitle(article == null ? "Добавить статью" : "Редактировать статью");
        dialog.setWidth("720px"); dialog.setMaxWidth("95vw"); dialog.setCloseOnOutsideClick(false);
        ComboBox<String> kind = new ComboBox<>("Вид животного");
        kind.setItems(species.stream().filter(Objects::nonNull).toList()); kind.setRequiredIndicatorVisible(true);
        ComboBox<String> category = new ComboBox<>("Категория"); category.setItems(AnimalGuideService.CATEGORIES); category.setRequiredIndicatorVisible(true);
        TextField title = new TextField("Заголовок статьи"); title.setMaxLength(255); title.setRequiredIndicatorVisible(true);
        TextArea text = new TextArea("Полный текст статьи"); text.setMinHeight("300px"); text.setRequiredIndicatorVisible(true);
        if (article != null) {
            kind.setValue(article.getSpecies()); category.setValue(article.getCategory());
            title.setValue(article.getTitle()); text.setValue(article.getContent());
        } else { if (tabs.getSelectedIndex() > 0) kind.setValue(species.get(tabs.getSelectedIndex())); category.setValue("Рацион"); }
        FormLayout form = new FormLayout(kind, category, title, text);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("500px", 2));
        form.setColspan(title, 2); form.setColspan(text, 2); dialog.add(form);
        Button save = new Button("Сохранить", e -> {
            try {
                service.save(article == null ? null : article.getId(), kind.getValue(), category.getValue(), title.getValue(), text.getValue());
                refresh(); dialog.close(); Notification.show("Статья сохранена");
            } catch (IllegalArgumentException exception) { Notification.show(exception.getMessage()); }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(new Button("Отмена", e -> dialog.close()), save); dialog.open();
    }
    private void confirmDelete(AnimalGuide article) {
        if (!RoleAccess.isAdmin()) return;
        ConfirmDialog confirm = new ConfirmDialog(); confirm.setHeader("Удалить статью?"); confirm.setText(article.getTitle());
        confirm.setCancelable(true); confirm.setCancelText("Отмена"); confirm.setConfirmText("Удалить"); confirm.setConfirmButtonTheme("error primary");
        confirm.addConfirmListener(e -> {
            try { service.delete(article.getId()); refresh(); } catch (IllegalArgumentException exception) { Notification.show(exception.getMessage()); }
        }); confirm.open();
    }
}