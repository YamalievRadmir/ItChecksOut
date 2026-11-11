package ru.sklad.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.sklad.app.entity.Note;

public interface NoteRepository extends JpaRepository<Note, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select n from Note n where n.id = :id")
    java.util.Optional<Note> findLockedById(@org.springframework.data.repository.query.Param("id") Long id);
}
