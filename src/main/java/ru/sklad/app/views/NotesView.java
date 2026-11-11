package ru.sklad.app.views;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.*;
import com.vaadin.flow.component.textfield.*;
import com.vaadin.flow.router.*;
import jakarta.annotation.security.RolesAllowed;
import ru.sklad.app.entity.Note;
import ru.sklad.app.service.NoteService;
import java.time.format.DateTimeFormatter;

@Route(value = "notes", layout = MainLayout.class)
@PageTitle("Заметки — Всё сходится!")
@RolesAllowed({"ADMIN", "SELLER"})
public class NotesView extends VerticalLayout {
    private final NoteService service;
    private final Div cards = new Div();
    private final Tabs filters = new Tabs(new Tab("Все"), new Tab("Долги"), new Tab("Инструкции"));
    public NotesView(NoteService service) {
        this.service = service;
        setSizeFull(); getStyle().set("overflow", "auto");
        Button create = new Button("Добавить заметку", VaadinIcon.PLUS.create(), e -> createNote());
        create.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        cards.addClassName("notes-list"); cards.setWidthFull();
        filters.addSelectedChangeListener(e -> refresh());
        add(new H2("Заметки"), create, filters, cards); refresh();
    }
    private void refresh() {
        cards.removeAll();
        String type = switch (filters.getSelectedIndex()) { case 1 -> "ДОЛГ"; case 2 -> "ИНСТРУКЦИЯ"; default -> null; };
        var notes = service.list(type);
        if (notes.isEmpty()) cards.add(new Paragraph("Заметок пока нет"));
        notes.forEach(note -> cards.add(card(note)));
    }
    private Div card(Note note) {
        Div card = new Div(); card.addClassNames("custom-card", "app-card", "note-card");
        card.getElement().addEventListener("dblclick", event -> editNote(note.getId()))
                .setFilter("!event.target.closest('vaadin-button')");
        Div heading = new Div(); heading.addClassName("note-heading");
        if ("ИНСТРУКЦИЯ".equals(note.getNoteType())) {
            heading.add(VaadinIcon.BOOK.create()); card.addClassName("note-instruction");
        }
        H3 title = new H3(note.getTitle()); title.getStyle().set("margin", "0"); heading.add(title);
        Span date = new Span(note.getCreatedAt().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")));
        date.addClassName("metric-label");
        Paragraph content = new Paragraph(note.getContent()); content.addClassName("note-content");
        card.add(heading, date, content);
        if ("ДОЛГ".equals(note.getNoteType())) {
            Span badge = new Span(NoteService.debtAmount(note.getContent()).orElse("Долг — сумма не указана"));
            badge.addClassName("note-debt-badge"); card.add(badge);
            if (!note.isResolved()) {
                Button resolve = new Button("Закрыть долг", e -> {
                    try { service.resolveDebt(note.getId()); refresh(); Notification.show("Долг закрыт"); }
                    catch (IllegalArgumentException exception) { Notification.show(exception.getMessage()); }
                });
                card.add(resolve);
            }
        }
        if (note.isResolved()) { card.addClassName("note-resolved"); card.add(new Span("Закрыто")); }
        Button edit = new Button("Редактировать", VaadinIcon.EDIT.create(), event -> editNote(note.getId()));
        edit.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        card.add(edit);
        return card;
    }
    private void createNote() {
        openEditor(null);
    }
    private Dialog editNote(Long id) {
        try { return openEditor(service.get(id)); }
        catch (IllegalArgumentException exception) { refresh(); Notification.show(exception.getMessage()); return null; }
    }
    private Dialog openEditor(Note note) {
        Dialog dialog = new Dialog(); dialog.setHeaderTitle(note == null ? "Добавить заметку" : "Редактировать заметку");
        dialog.setWidth("560px"); dialog.setMaxWidth("95vw"); dialog.setCloseOnOutsideClick(false);
        ComboBox<String> type = new ComboBox<>("Тип заметки"); type.setItems("ДОЛГ", "ИНСТРУКЦИЯ", "ПРОЧЕЕ");
        type.setItemLabelGenerator(value -> switch (value) { case "ДОЛГ" -> "Долг"; case "ИНСТРУКЦИЯ" -> "Инструкция"; default -> "Прочее"; });
        type.setValue("ПРОЧЕЕ"); type.setRequiredIndicatorVisible(true);
        TextField title = new TextField("Заголовок"); title.setMaxLength(255); title.setRequiredIndicatorVisible(true);
        TextArea content = new TextArea("Текст заметки"); content.setRequiredIndicatorVisible(true); content.setMinHeight("180px");
        if (note != null) {
            type.setValue(note.getNoteType());
            title.setValue(java.util.Objects.toString(note.getTitle(), ""));
            content.setValue(java.util.Objects.toString(note.getContent(), ""));
        }
        content.setHelperText("Для долга укажите сумму с валютой, например: 5000 руб.");
        type.setWidthFull(); title.setWidthFull(); content.setWidthFull();
        VerticalLayout form = new VerticalLayout(type, title, content); form.setPadding(false); dialog.add(form);
        Button save = new Button("Сохранить", e -> {
            title.setInvalid(title.isEmpty() || title.getValue().isBlank());
            content.setInvalid(content.isEmpty() || content.getValue().isBlank());
            try {
                if (note == null) service.create(type.getValue(), title.getValue(), content.getValue());
                else service.update(note.getId(), type.getValue(), title.getValue(), content.getValue());
                dialog.close(); refresh(); Notification.show("Заметка сохранена");
            }
            catch (IllegalArgumentException exception) { Notification.show(exception.getMessage()); }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        if (note != null) {
            Button delete = new Button("Удалить", VaadinIcon.TRASH.create(), event -> {
                var confirm = new com.vaadin.flow.component.confirmdialog.ConfirmDialog();
                confirm.setHeader("Удалить заметку?");
                confirm.setText("Заметка «" + note.getTitle() + "» будет удалена.");
                confirm.setCancelable(true); confirm.setCancelText("Отмена");
                confirm.setConfirmText("Удалить"); confirm.setConfirmButtonTheme("error primary");
                confirm.addConfirmListener(confirmed -> {
                    try { service.delete(note.getId()); dialog.close(); refresh(); Notification.show("Заметка удалена"); }
                    catch (IllegalArgumentException exception) { dialog.close(); refresh(); Notification.show(exception.getMessage()); }
                });
                confirm.open();
            });
            delete.addThemeVariants(ButtonVariant.LUMO_ERROR);
            dialog.getFooter().add(delete);
        }
        dialog.getFooter().add(new Button("Отмена", e -> dialog.close()), save); dialog.open();
        return dialog;
    }
}
