package ru.sklad.app.views;
import org.junit.jupiter.api.Test;
import com.vaadin.flow.component.*;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.textfield.*;
import com.vaadin.flow.component.combobox.ComboBox;
import ru.sklad.app.service.NoteService;
import ru.sklad.app.entity.Note;
import java.util.List;
import java.util.stream.Stream;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class NotesEditorTest {
    Stream<Component> tree(Component c) { return Stream.concat(Stream.of(c),c.getChildren().flatMap(this::tree)); }
    @Test void editorPrefillsFieldsAndUpdatesExistingNoteRatherThanCreatingAnother() throws Exception {
        UI ui=new UI(); UI.setCurrent(ui);
        try {
            NoteService service=mock(NoteService.class);
            Note note=new Note(); note.setId(7L); note.setTitle("Старый заголовок"); note.setContent("Старый текст"); note.setNoteType("ПРОЧЕЕ");
            when(service.list(null)).thenReturn(List.of(note)); when(service.get(7L)).thenReturn(note);
            var view=new NotesView(service);
            var open=NotesView.class.getDeclaredMethod("editNote",Long.class); open.setAccessible(true);
            Dialog dialog=(Dialog)open.invoke(view,7L);
            TextField title=tree(dialog).filter(TextField.class::isInstance).map(TextField.class::cast).findFirst().orElseThrow();
            TextArea content=tree(dialog).filter(TextArea.class::isInstance).map(TextArea.class::cast).findFirst().orElseThrow();
            ComboBox<?> type=tree(dialog).filter(ComboBox.class::isInstance).map(ComboBox.class::cast).findFirst().orElseThrow();
            assertEquals("Старый заголовок",title.getValue()); assertEquals("Старый текст",content.getValue()); assertEquals("ПРОЧЕЕ",type.getValue());
            title.setValue("Новый заголовок"); content.setValue("Новый текст");
            dialog.getFooter().getElement().getChildren().flatMap(element -> element.getComponent().stream())
                    .filter(Button.class::isInstance).map(Button.class::cast)
                    .filter(b -> "Сохранить".equals(b.getText())).findFirst().orElseThrow().click();
            verify(service).update(7L,"ПРОЧЕЕ","Новый заголовок","Новый текст"); verify(service,never()).create(any(),any(),any());
            assertFalse(dialog.isOpened()); verify(service,times(2)).list(null);
        } finally { UI.setCurrent(null); }
    }
}
