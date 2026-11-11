package ru.sklad.app.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sklad.app.entity.Supplier;
import ru.sklad.app.repository.SupplierRepository;
import java.util.List;
import java.util.Objects;

@Service
@Transactional(readOnly = true)
@PreAuthorize("@permissionService.canAccess('supplies') and hasAnyRole('ADMIN', 'SELLER')")
public class SupplierService {
    private final SupplierRepository repository;
    private final AuditService audit;

    public SupplierService(SupplierRepository repository, AuditService audit) {
        this.repository = repository; this.audit = audit;
    }

    public List<Supplier> list() { return repository.findAllByOrderByNameAscIdAsc(); }

    @Transactional
    public Supplier save(Long id, String name, String phone, String address, String comment) {
        if (name == null || name.isBlank() || name.trim().length() > 255
                || (phone != null && phone.trim().length() > 255) || (address != null && address.trim().length() > 255))
            throw new IllegalArgumentException("Укажите название и проверьте длину полей: не более 255 символов");
        Supplier supplier = id == null ? new Supplier() : repository.findLockedById(id)
                .orElseThrow(() -> new IllegalArgumentException("Поставщик больше не существует"));
        String before = id == null ? "" : "Название: " + supplier.getName() + "; Телефон: " + supplier.getPhone()
                + "; Адрес: " + supplier.getAddress() + "; Комментарий: " + supplier.getComment() + " → ";
        supplier.setName(name.trim()); supplier.setPhone(Objects.toString(phone, "").trim());
        supplier.setAddress(Objects.toString(address, "").trim()); supplier.setComment(Objects.toString(comment, "").trim());
        Supplier saved = repository.saveAndFlush(supplier);
        audit.logAction(id == null ? "СОЗДАНИЕ" : "ИЗМЕНЕНИЕ", "Поставщик", saved.getName() + " (#" + saved.getId() + ")",
                before + "Название: " + saved.getName() + "; Телефон: " + saved.getPhone()
                        + "; Адрес: " + saved.getAddress() + "; Комментарий: " + saved.getComment());
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        if (id == null) throw new IllegalArgumentException("Выберите поставщика");
        Supplier supplier = repository.findLockedById(id)
                .orElseThrow(() -> new IllegalArgumentException("Поставщик больше не существует"));
        repository.delete(supplier); repository.flush();
        // Supply history keeps the supplier's name as a snapshot, without a cascading foreign key.
        audit.logAction("УДАЛЕНИЕ", "Поставщик", supplier.getName() + " (#" + id + ")", "Удален из справочника поставщиков");
    }
}
