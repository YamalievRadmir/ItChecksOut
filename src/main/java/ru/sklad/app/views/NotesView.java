package ru.sklad.app.views;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
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
    private final Div debts = new Div();
    public NotesView(NoteService service) {
        this.service = service;
        setSizeFull(); addClassName("notes-view"); getStyle().set("overflow", "hidden");
        Button create = new Button("+ Добавить заметку", VaadinIcon.PLUS.create(), event -> openEditor(null, "ИНСТРУКЦИЯ"));
        Button createDebt = new Button("+ Добавить долг", VaadinIcon.PLUS.create(), event -> openEditor(null, "ДОЛГ"));
        create.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        createDebt.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        VerticalLayout left = column("Заметки и Инструкции", create, cards);
        VerticalLayout right = column("Долги", createDebt, debts);
        left.setWidth("60%"); right.setWidth("40%");
        HorizontalLayout columns = new HorizontalLayout(left, right);
        columns.addClassName("notes-columns"); columns.setSizeFull(); columns.setMinHeight("0");
        add(columns); setFlexGrow(1, columns); refresh();
    }
    private VerticalLayout column(String title, Button create, Div list) {
        H3 heading = new H3(title);
        HorizontalLayout toolbar = new HorizontalLayout(heading, create);
        toolbar.addClassName("notes-column-toolbar"); toolbar.setWidthFull();
        toolbar.setAlignItems(Alignment.CENTER); toolbar.setFlexGrow(1, heading);
        list.addClassName("notes-list"); list.setWidthFull();
        VerticalLayout column = new VerticalLayout(toolbar, list);
        column.addClassName("notes-column"); column.setHeightFull(); column.setPadding(false);
        column.setMinWidth("0"); column.setMinHeight("0"); column.setFlexGrow(1, list);
        return column;
    }
    private void refresh() {
        cards.removeAll(); debts.removeAll();
        for (Note note : service.list(null)) {
            if ("ДОЛГ".equals(note.getNoteType())) debts.add(card(note));
            else if ("ПРОЧЕЕ".equals(note.getNoteType()) || "ИНСТРУКЦИЯ".equals(note.getNoteType())) cards.add(card(note));
        }
        if (cards.getComponentCount() == 0) cards.add(new Paragraph("Заметок пока нет"));
        if (debts.getComponentCount() == 0) debts.add(new Paragraph("Долгов пока нет"));
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
        card.add(heading, date);
        if ("ДОЛГ".equals(note.getNoteType())) {
            Span badge = new Span(NoteService.debtAmount(note.getContent()).orElse("Долг — сумма не указана"));
            badge.addClassName("note-debt-badge"); card.add(badge, content);
            if (!note.isResolved()) {
                Button resolve = new Button("Закрыть долг", e -> {
                    try { service.resolveDebt(note.getId()); refresh(); Notification.show("Долг закрыт"); }
                    catch (IllegalArgumentException exception) { Notification.show(exception.getMessage()); }
                });
                card.add(resolve);
            }
        }
        if (!"ДОЛГ".equals(note.getNoteType())) card.add(content);
        if (note.isResolved()) { card.addClassName("note-resolved"); card.add(new Span("Закрыто")); }
        Button edit = new Button("Редактировать", VaadinIcon.EDIT.create(), event -> editNote(note.getId()));
        edit.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        card.add(edit);
        return card;
    }
    private Dialog editNote(Long id) {
        try { return openEditor(service.get(id)); }
        catch (IllegalArgumentException exception) { refresh(); Notification.show(exception.getMessage()); return null; }
    }
    private Dialog openEditor(Note note) { return openEditor(note, "ИНСТРУКЦИЯ"); }
    private Dialog openEditor(Note note, String defaultType) {
        Dialog dialog = new Dialog(); dialog.setHeaderTitle(note == null ? ("ДОЛГ".equals(defaultType) ? "Добавить долг" : "Добавить заметку") : "Редактировать заметку");
        dialog.setWidth("560px"); dialog.setMaxWidth("95vw"); dialog.setCloseOnOutsideClick(false);
        ComboBox<String> type = new ComboBox<>("Тип заметки"); type.setItems("ДОЛГ", "ИНСТРУКЦИЯ", "ПРОЧЕЕ");
        type.setItemLabelGenerator(value -> switch (value) { case "ДОЛГ" -> "Долг"; case "ИНСТРУКЦИЯ" -> "Инструкция"; default -> "Прочее"; });
        type.setValue(defaultType); type.setRequiredIndicatorVisible(true);
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
