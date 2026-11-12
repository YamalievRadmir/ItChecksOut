package ru.sklad.app.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sklad.app.entity.Employee;
import ru.sklad.app.entity.WorkShift;
import ru.sklad.app.repository.EmployeeRepository;
import ru.sklad.app.repository.WorkShiftRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.*;

@Service
@PreAuthorize("@permissionService.canAccess('employees')")
@Transactional(readOnly = true)
public class EmployeeService {
    private final EmployeeRepository employees;
    private final WorkShiftRepository shifts;
    private final AuditService audit;

    public EmployeeService(EmployeeRepository employees, WorkShiftRepository shifts, AuditService audit) {
        this.employees = employees;
        this.shifts = shifts;
        this.audit = audit;
    }

    public List<Employee> employees() {
        return employees.findAllByOrderByFullNameAscIdAsc();
    }

    @Transactional
    public Employee create(String fullName, String role, BigDecimal rate, String phone) {
        rate = validate(fullName, role, rate, phone);
        Employee employee = new Employee();
        employee.setFullName(fullName.trim());
        employee.setRole(role);
        employee.setBaseSalary(rate);
        employee.setPhone(phone);
        Employee saved = employees.save(employee);
        audit.safeLogAction("СОЗДАНИЕ", "Сотрудник", saved.getFullName() + " (#" + saved.getId() + ")", "Должность: " + role + "; Ставка: " + saved.getBaseSalary());
        return saved;
    }

    private static BigDecimal validate(String fullName, String role, BigDecimal rate, String phone) {
        if (fullName == null || fullName.isBlank() || fullName.trim().length() > 255
                || rate == null || rate.signum() < 0 || rate.compareTo(new BigDecimal("99999999.99")) > 0
                || (role != null && role.length() > 255) || (phone != null && phone.length() > 255)) {
            throw new IllegalArgumentException("Проверьте ФИО, ставку и длину полей");
        }
        return rate.setScale(2, RoundingMode.UNNECESSARY);
    }

    @Transactional
    public Employee update(Long id, String fullName, String role, BigDecimal rate, String phone) {
        rate = validate(fullName, role, rate, phone);
        if (id == null) throw new IllegalArgumentException("Выберите сотрудника");
        Employee employee = employees.findLockedById(id)
                .orElseThrow(() -> new IllegalArgumentException("Сотрудник больше не существует"));
        String details = "ФИО: " + employee.getFullName() + " → " + fullName.trim()
                + "; Должность: " + employee.getRole() + " → " + role
                + "; Ставка: " + employee.getBaseSalary() + " → " + rate
                + "; Телефон: " + Objects.toString(employee.getPhone(), "") + " → " + Objects.toString(phone, "");
        // Update the existing row in place; WorkShift references and IDs remain intact.
        employee.setFullName(fullName.trim()); employee.setRole(role);
        employee.setBaseSalary(rate); employee.setPhone(phone);
        Employee saved = employees.save(employee);
        audit.safeLogAction("ИЗМЕНЕНИЕ", "Сотрудник", saved.getFullName() + " (#" + saved.getId() + ")", details);
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        if (id == null) throw new IllegalArgumentException("Выберите сотрудника");
        Employee employee = employees.findLockedById(id)
                .orElseThrow(() -> new IllegalArgumentException("Сотрудник больше не существует"));
        int removedShifts = shifts.deleteForEmployee(id);
        employees.delete(employee);
        employees.flush();
        audit.safeLogAction("УДАЛЕНИЕ", "Сотрудник", employee.getFullName() + " (#" + id + ")",
                "Удален сотрудник и связанные записи смен: " + removedShifts);
    }

    public Map<Long, Set<Integer>> month(YearMonth month) {
        Map<Long, Set<Integer>> result = new HashMap<>();
        for (WorkShift shift : shifts.findByShiftDateBetween(month.atDay(1), month.atEndOfMonth())) {
            if (shift.isWorked()) {
                result.computeIfAbsent(shift.getEmployee().getId(), id -> new HashSet<>())
                        .add(shift.getShiftDate().getDayOfMonth());
            }
        }
        return result;
    }

    @Transactional
    public void saveMonth(YearMonth month, Map<Long, Set<Integer>> workedDays) {
        if (month == null || workedDays == null) {
            throw new IllegalArgumentException("Укажите месяц и табель");
        }
        for (Long employeeId : workedDays.keySet().stream().sorted().toList()) {
            Set<Integer> days = workedDays.get(employeeId);
            if (days == null || days.stream().anyMatch(day -> day == null || day < 1 || day > month.lengthOfMonth())) {
                throw new IllegalArgumentException("В табеле есть несуществующий день месяца");
            }
            Employee employee = employees.findLockedById(employeeId)
                    .orElseThrow(() -> new IllegalArgumentException("Сотрудник больше не существует"));
            Map<Integer, WorkShift> existing = new HashMap<>();
            shifts.findByEmployeeIdAndShiftDateBetween(employeeId, month.atDay(1), month.atEndOfMonth())
                    .forEach(shift -> existing.put(shift.getShiftDate().getDayOfMonth(), shift));
            var before = existing.values().stream().filter(WorkShift::isWorked).map(s -> s.getShiftDate().getDayOfMonth()).sorted().toList();
            List<WorkShift> updated = new ArrayList<>();
            for (int day = 1; day <= month.lengthOfMonth(); day++) {
                WorkShift shift = existing.getOrDefault(day, new WorkShift());
                shift.setEmployee(employee);
                shift.setShiftDate(month.atDay(day));
                shift.setWorked(days.contains(day));
                updated.add(shift);
            }
            shifts.saveAll(updated);
            audit.safeLogAction("ИЗМЕНЕНИЕ", "Сотрудник", employee.getFullName() + " (#" + employeeId + ")",
                    "Сохранен табель за " + month + "; Отработанные дни: " + before + " → " + new java.util.TreeSet<>(days));
        }
    }

    public static BigDecimal calculatePay(Employee employee, Set<Integer> workedDays) {
        BigDecimal rate = employee.getBaseSalary() == null ? BigDecimal.ZERO : employee.getBaseSalary();
        return rate.multiply(BigDecimal.valueOf(workedDays.size())).setScale(2, RoundingMode.HALF_UP);
    }
}
