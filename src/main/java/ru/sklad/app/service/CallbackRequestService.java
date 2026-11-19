package ru.sklad.app.service;

import jakarta.persistence.EntityManager;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sklad.app.entity.CallbackRequest;
import ru.sklad.app.repository.CallbackRequestRepository;
import ru.sklad.app.util.SecurityUtils;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class CallbackRequestService {
    private final CallbackRequestRepository requests;
    private final EntityManager entityManager;

    public CallbackRequestService(CallbackRequestRepository requests, EntityManager entityManager) {
        this.requests = requests;
        this.entityManager = entityManager;
    }

    @Transactional
    public void create(UUID id, String name, String phone, String orderDetails) {
        var shop = SecurityUtils.businessShop();
        if (id == null || name == null || name.isBlank() || name.strip().length() > 100
                || phone == null || phone.length() > 32 || !phone.matches("\\+?[0-9 ()-]+")
                || (orderDetails != null && orderDetails.length() > 32_000)) {
            throw new IllegalArgumentException("Invalid callback request");
        }
        String normalizedPhone = phone.replaceAll("\\D", "");
        if (normalizedPhone.length() < 10 || normalizedPhone.length() > 15) {
            throw new IllegalArgumentException("Invalid callback phone");
        }
        if (normalizedPhone.length() == 10 && !phone.startsWith("+")) normalizedPhone = "7" + normalizedPhone;
        if (normalizedPhone.length() == 11 && normalizedPhone.startsWith("8") && !phone.startsWith("+")) {
            normalizedPhone = "7" + normalizedPhone.substring(1);
        }
        var existing = requests.findById(id);
        if (existing.isPresent()) {
            var request = existing.get();
            if (!request.getCustomerName().equals(name.strip()) || !request.getPhone().equals(normalizedPhone)
                    || !java.util.Objects.equals(request.getOrderDetails(), orderDetails)) {
                throw new IllegalArgumentException("Callback request ID reused with different details");
            }
            return;
        }
        var request = new CallbackRequest();
        request.setId(id);
        request.setShop(shop);
        request.setCustomerName(name.strip());
        request.setPhone(normalizedPhone);
        request.setCreatedAt(Instant.now());
        request.setOrderDetails(orderDetails);
        entityManager.persist(request);
        requests.flush();
    }

    @PreAuthorize("@permissionService.canAccess('deliveries')")
    @Transactional(readOnly = true)
    public List<CallbackRequest> pending() {
        return requests.findPending();
    }

    @PreAuthorize("@permissionService.canEdit('deliveries')")
    @Transactional
    public void complete(UUID id) {
        requests.complete(id, Instant.now());
    }
}
