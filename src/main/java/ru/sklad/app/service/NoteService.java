package ru.sklad.app.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import ru.sklad.app.entity.Note;
import ru.sklad.app.repository.NoteRepository;
import java.util.*;
import java.util.regex.Pattern;

@Service
@Transactional(readOnly = true)
@PreAuthorize("hasAnyRole('ADMIN', 'SELLER') and @permissionService.canAccess('notes')")
public class NoteService {
    private final NoteRepository repository;
    private final AuditService audit;
    private static final Pattern AMOUNT = Pattern.compile(
            "(?iu)(?<![\\d.,])(?:\\d{1,3}(?:[ \\u00a0\\u202f]\\d{3})+|\\d+)(?:[.,]\\d{1,2})?\\s*(?:₽|руб(?:лей|ля|ль|\\.)?|р\\.)(?![\\p{L}])");
    public NoteService(NoteRepository repository, AuditService audit) { this.repository = repository; this.audit = audit; }
    public List<Note> list(String type) {
        return repository.findAllByShop(ru.sklad.app.util.SecurityUtils.currentShop()).stream().filter(n -> type == null || type.equals(n.getNoteType()))
                .sorted(Comparator.comparing(Note::isResolved)
                        .thenComparing(n -> !"ИНСТРУКЦИЯ".equals(n.getNoteType()))
                        .thenComparing(Note::getCreatedAt, Comparator.reverseOrder())
                        .thenComparing(Note::getId, Comparator.reverseOrder())).toList();
    }
    public static Optional<String> debtAmount(String text) {
        var matcher = AMOUNT.matcher(Objects.toString(text, ""));
        return matcher.find() ? Optional.of(matcher.group().trim()) : Optional.empty();
    }
    @Transactional
    public Note create(String type, String title, String content) {
        validate(type, title, content);
        Note note = new Note(); note.setNoteType(type); note.setTitle(title.trim()); note.setContent(content.trim());
        Note saved = repository.saveAndFlush(note);
        audit.safeLogAction("СОЗДАНИЕ", "Заметка", saved.getTitle() + " (#" + saved.getId() + ")", "Тип: " + type + "; " + saved.getContent());
        return saved;
    }
    private static void validate(String type, String title, String content) {
        if (!Set.of("ДОЛГ", "ИНСТРУКЦИЯ", "ПРОЧЕЕ").contains(Objects.toString(type, "")))
            throw new IllegalArgumentException("Выберите тип заметки");
        if (title == null || title.isBlank() || title.trim().length() > 255 || content == null || content.isBlank())
            throw new IllegalArgumentException("Заполните заголовок (до 255 символов) и текст заметки");
    }
    public Note get(Long id) {
        if (id == null) throw new IllegalArgumentException("Заметка больше не существует");
        return repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Заметка больше не существует"));
    }
    @Transactional
    public Note update(Long id, String type, String title, String content) {
        validate(type, title, content);
        Note note = lock(id);
        String before = note.getNoteType() + "; " + note.getTitle() + "; " + note.getContent();
        note.setNoteType(type); note.setTitle(title.trim()); note.setContent(content.trim());
        Note saved = repository.saveAndFlush(note);
        audit.safeLogAction("ИЗМЕНЕНИЕ", "Заметка", saved.getTitle() + " (#" + id + ")",
                "До: " + before + "; После: " + type + "; " + saved.getTitle() + "; " + saved.getContent());
        return saved;
    }
    @Transactional
    public void delete(Long id) {
        Note note = lock(id);
        audit.safeLogAction("УДАЛЕНИЕ", "Заметка", note.getTitle() + " (#" + id + ")", "Тип: " + note.getNoteType());
        repository.delete(note); repository.flush();
    }
    private Note lock(Long id) {
        if (id == null) throw new IllegalArgumentException("Заметка больше не существует");
        return repository.findLockedById(id).orElseThrow(() -> new IllegalArgumentException("Заметка больше не существует"));
    }
    @Transactional
    public void resolveDebt(Long id) {
        Note note = lock(id);
        if (!"ДОЛГ".equals(note.getNoteType())) throw new IllegalArgumentException("Закрывать можно только долги");
        note.setResolved(true); repository.save(note);
        audit.safeLogAction("ИЗМЕНЕНИЕ", "Заметка", note.getTitle() + " (#" + id + ")", "Долг закрыт");
    }
}
