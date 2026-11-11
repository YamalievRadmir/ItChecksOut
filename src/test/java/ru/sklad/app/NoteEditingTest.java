package ru.sklad.app;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.sklad.app.service.*;
import ru.sklad.app.repository.NoteRepository;
import static org.junit.jupiter.api.Assertions.*;
@DataJpaTest(showSql=false) @Import(NoteService.class)
class NoteEditingTest {
    @Autowired NoteService service;
    @Autowired NoteRepository notes;
    @MockitoBean AuditService audit;
    @Test void updateKeepsIdentityCreationDateAndResolvedState() {
        var note=service.create("ДОЛГ","Первый заголовок","Долг 500 руб.");
        var created=note.getCreatedAt(); service.resolveDebt(note.getId());
        var saved=service.update(note.getId(),"ПРОЧЕЕ"," Новый заголовок "," Новый текст ");
        assertEquals(note.getId(),saved.getId()); assertEquals(1,notes.count());
        assertEquals(created,saved.getCreatedAt()); assertTrue(saved.isResolved());
        assertEquals("Новый заголовок",saved.getTitle()); assertEquals("Новый текст",saved.getContent());
        assertEquals("ПРОЧЕЕ",saved.getNoteType());
    }
    @Test void deletingRemovesNoteAndUpdatingMissingNoteDoesNotRecreateIt() {
        var note=service.create("ПРОЧЕЕ","Заметка","Текст"); Long id=note.getId();
        service.delete(id); assertFalse(notes.existsById(id));
        assertThrows(IllegalArgumentException.class,()->service.update(id,"ПРОЧЕЕ","Новый","Текст"));
        assertEquals(0,notes.count());
    }
    @Test void invalidUpdateLeavesOriginalFieldsUntouched() {
        var note=service.create("ИНСТРУКЦИЯ","Заголовок","Текст");
        assertThrows(IllegalArgumentException.class,()->service.update(note.getId(),"ПРОЧЕЕ"," ","Новый текст"));
        assertEquals("Заголовок",service.get(note.getId()).getTitle());
        assertEquals("ИНСТРУКЦИЯ",service.get(note.getId()).getNoteType());
    }
}
