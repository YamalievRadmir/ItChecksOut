package ru.sklad.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDate;

@Entity
@Table(name = "work_shifts", uniqueConstraints = @UniqueConstraint(
        name = "uk_shift_employee_date", columnNames = {"employee_id", "shift_date"}))
@Getter
@Setter
@NoArgsConstructor
public class WorkShift {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "shift_date", nullable = false)
    private LocalDate shiftDate;

    @Column(name = "is_worked", nullable = false)
    private boolean isWorked = false;
}
