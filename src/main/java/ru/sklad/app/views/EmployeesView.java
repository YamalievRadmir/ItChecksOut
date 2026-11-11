package ru.sklad.app.views;

import ru.sklad.app.util.GridColumns;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import ru.sklad.app.entity.Employee;
import ru.sklad.app.service.EmployeeService;
import ru.sklad.app.util.ExcelExporter;
import ru.sklad.app.util.WordExporter;

import java.math.BigDecimal;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.*;

@Route(value = "employees", layout = MainLayout.class)
@PageTitle("Сотрудники — Всё сходится!")
@jakarta.annotation.security.PermitAll
public class EmployeesView extends VerticalLayout {
    private final EmployeeService service;
    private final Grid<Employee> employees = new Grid<>(Employee.class, false);
    private final Grid<Employee> timesheet = new Grid<>(Employee.class, false);
    private final ComboBox<Month> month = new ComboBox<>("Месяц");
    private final NumberField year = new NumberField("Год");
    private final Span status = new Span();
    private final Map<Long, Set<Integer>> draft = new LinkedHashMap<>();
    private final Map<Long, Span> totals = new HashMap<>();
    private final Button save = new Button("Сохранить табель за месяц");
    private YearMonth loadedMonth;
    private List<Employee> displayedStaff = List.of();
    private HorizontalLayout export;
    private HorizontalLayout wordExport;

    public EmployeesView(EmployeeService service) {
        this.service = service;

        setSizeFull();
        addClassNames("employees-view", "compact-page");
        getStyle().set("overflow", "auto");
        add(new H2("Сотрудники"));

        employees.addColumn(Employee::getFullName).setHeader("ФИО").setFlexGrow(2);
        employees.addColumn(Employee::getRole).setHeader("Должность");
        employees.addColumn(Employee::getBaseSalary).setHeader("Ставка за смену");
        employees.addColumn(Employee::getPhone).setHeader("Телефон");
        GridColumns.configure(employees, "ФИО", "Должность");
        employees.setItems(service.employees());
        employees.setSizeFull();
        employees.addItemDoubleClickListener(event -> openEmployeeDialog(event.getItem()));
        Button addEmployee = new Button("Добавить сотрудника", event -> openEmployeeDialog(null));
        addEmployee.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        VerticalLayout employeeTab = new VerticalLayout(addEmployee, employees);
        employeeTab.setSizeFull();
        employeeTab.setPadding(false);
        employeeTab.addClassNames("compact-tab-body", "app-card");
        employeeTab.setFlexGrow(1, employees);

        YearMonth current = YearMonth.now();
        month.setItems(Month.values());
        month.setItemLabelGenerator(value -> value.getDisplayName(TextStyle.FULL_STANDALONE,
                Locale.forLanguageTag("ru-RU")));
        month.setValue(current.getMonth());
        year.setMin(1);
        year.setMax(9999);
        year.setStep(1);
        year.setStepButtonsVisible(true);
        year.setValue((double) current.getYear());
        year.setErrorMessage("Введите целый год от 1 до 9999");
        HorizontalLayout period = new HorizontalLayout(month, year);
        period.setAlignItems(Alignment.BASELINE);
        period.getStyle().set("flex-wrap", "wrap");
        timesheet.setSizeFull();
        timesheet.setMinHeight("160px");
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        save.addClickListener(event -> {
            try {
                service.saveMonth(loadedMonth, draft);
                status.setText("Табель за " + loadedMonth + " сохранен");
                Notification.show("Табель сохранен");
            } catch (IllegalArgumentException exception) {
                Notification.show(exception.getMessage());
            }
        });
        month.addValueChangeListener(event -> loadMonth(false));
        year.addValueChangeListener(event -> loadMonth(false));
        export = ExcelExporter.downloadButton("Скачать табель (Excel)", "timesheet.xlsx", this::exportTimesheet);
        wordExport = WordExporter.downloadButton("Скачать табель (Word)", "timesheet.docx",
                () -> WordExporter.timesheet(loadedMonth, displayedStaff.stream()
                        .map(employee -> new WordExporter.PayrollRow(employee.getFullName(), employee.getRole(),
                                draft.get(employee.getId()).size(),
                                EmployeeService.calculatePay(employee, draft.get(employee.getId())))).toList()));
        VerticalLayout payrollTab = new VerticalLayout(period,
                new Span("Итого к выплате = отработанные смены × ставка за смену"),
                status, timesheet, new HorizontalLayout(save, export, wordExport));
        payrollTab.setSizeFull();
        payrollTab.setPadding(false);
        payrollTab.addClassNames("compact-tab-body", "app-card");
        payrollTab.setFlexGrow(1, timesheet);

        TabSheet tabs = new TabSheet();
        tabs.add("Сотрудники", employeeTab);
        tabs.add("Табель/ЗП", payrollTab);
        tabs.setSizeFull();
        tabs.addClassName("compact-tabs");
        add(tabs);
        setFlexGrow(1, tabs);
        loadMonth(false);
    }

    private void loadMonth(boolean preserveDraft) {
        Double selectedYear = year.getValue();
        if (month.getValue() == null || year.isInvalid() || selectedYear == null
                || !Double.isFinite(selectedYear) || selectedYear != Math.rint(selectedYear)
                || selectedYear < 1 || selectedYear > 9999) {
            status.setText("Укажите месяц и целый год от 1 до 9999");
            save.setEnabled(false);
            if (export != null) export.setEnabled(false);
            if (wordExport != null) wordExport.setEnabled(false);
            return;
        }
        YearMonth selectedMonth = YearMonth.of(selectedYear.intValue(), month.getValue());
        Map<Long, Set<Integer>> days = preserveDraft && selectedMonth.equals(loadedMonth)
                ? new HashMap<>(draft) : service.month(selectedMonth);
        loadedMonth = selectedMonth;
        draft.clear();
        totals.clear();
        List<Employee> staff = service.employees();
        displayedStaff = List.copyOf(staff);
        staff.forEach(employee -> draft.put(employee.getId(),
                new HashSet<>(days.getOrDefault(employee.getId(), Set.of()))));
        timesheet.removeAllColumns();
        timesheet.addColumn(Employee::getFullName).setHeader("ФИО")
                .setWidth("240px").setFlexGrow(0).setFrozen(true);
        for (int day = 1; day <= loadedMonth.lengthOfMonth(); day++) {
            int dayNumber = day;
            timesheet.addComponentColumn(employee -> {
                Checkbox worked = new Checkbox();
                worked.setAriaLabel(employee.getFullName() + ", " + loadedMonth.atDay(dayNumber));
                worked.setValue(draft.get(employee.getId()).contains(dayNumber));
                worked.addValueChangeListener(event -> {
                    Set<Integer> workedDays = draft.get(employee.getId());
                    if (event.getValue()) {
                        workedDays.add(dayNumber);
                    } else {
                        workedDays.remove(dayNumber);
                    }
                    Span total = totals.get(employee.getId());
                    if (total != null) {
                        total.setText(pay(employee));
                    }
                    status.setText("Есть несохраненные изменения за " + loadedMonth);
                });
                return worked;
            }).setHeader(String.valueOf(day)).setWidth("64px").setFlexGrow(0);
        }
        timesheet.addComponentColumn(employee -> {
            Span total = new Span(pay(employee));
            totals.put(employee.getId(), total);
            return total;
        }).setHeader("Итого к выплате").setWidth("190px").setFlexGrow(0).setFrozenToEnd(true);
        GridColumns.configure(timesheet, "ФИО");
        timesheet.setItems(staff);
        status.setText(staff.isEmpty() ? "Добавьте сотрудников на вкладке «Сотрудники»"
                : "Табель за " + loadedMonth);
        save.setEnabled(true);
        export.setEnabled(true);
        wordExport.setEnabled(true);
    }

    private byte[] exportTimesheet() {
        List<ExcelExporter.Column<Employee>> columns = new ArrayList<>();
        columns.add(new ExcelExporter.Column<>("ФИО", Employee::getFullName));
        columns.add(new ExcelExporter.Column<>("Должность", Employee::getRole));
        columns.add(new ExcelExporter.Column<>("Ставка за смену", Employee::getBaseSalary));
        for (int day = 1; day <= loadedMonth.lengthOfMonth(); day++) {
            int number = day;
            columns.add(new ExcelExporter.Column<>(String.valueOf(day),
                    employee -> draft.get(employee.getId()).contains(number) ? "✓" : ""));
        }
        columns.add(new ExcelExporter.Column<>("Итого к выплате",
                employee -> EmployeeService.calculatePay(employee, draft.get(employee.getId()))));
        return ExcelExporter.export("Табель " + loadedMonth, columns, displayedStaff);
    }

    private String pay(Employee employee) {
        return EmployeeService.calculatePay(employee, draft.get(employee.getId())).toPlainString() + " ₽";
    }

    private void updateGrid() {
        employees.setItems(service.employees());
        loadMonth(true);
    }

    private void openEmployeeDialog(Employee employee) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(employee == null ? "Добавить сотрудника" : "Редактировать сотрудника");
        dialog.setModal(true);
        dialog.setCloseOnOutsideClick(false);
        dialog.setWidth("560px");
        dialog.setMaxWidth("95vw");
        TextField name = new TextField("ФИО");
        name.setRequiredIndicatorVisible(true);
        name.setMaxLength(255);
        name.setErrorMessage("Введите ФИО");
        ComboBox<String> role = new ComboBox<>("Должность");
        List<String> roles = new ArrayList<>(List.of("Продавец", "Грузчик", "Администратор"));
        if (employee != null && employee.getRole() != null && !roles.contains(employee.getRole())) roles.add(employee.getRole());
        role.setItems(roles);
        role.setAllowCustomValue(false);
        role.setValue(employee == null ? "Продавец" : employee.getRole());
        NumberField rate = new NumberField("Ставка за смену");
        rate.setRequiredIndicatorVisible(true);
        rate.setMin(0);
        rate.setMax(99999999.99);
        rate.setStep(0.01);
        rate.setErrorMessage("Ставка от 0 до 99999999,99, не более двух знаков после запятой");
        TextField phone = new TextField("Телефон");
        phone.setMaxLength(255);
        if (employee != null) {
            name.setValue(Objects.toString(employee.getFullName(), ""));
            rate.setValue(employee.getBaseSalary() == null ? null : employee.getBaseSalary().doubleValue());
            phone.setValue(Objects.toString(employee.getPhone(), ""));
        }
        FormLayout form = new FormLayout(name, role, rate, phone);
        form.addClassName("responsive-form");
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("450px", 2));
        form.setColspan(name, 2);
        dialog.add(form);
        Button create = new Button("Сохранить", event -> {
            name.setInvalid(name.getValue().isBlank());
            Double value = rate.getValue();
            rate.setInvalid(value == null || !Double.isFinite(value) || value < 0 || value > 99999999.99
                    || BigDecimal.valueOf(value).stripTrailingZeros().scale() > 2);
            if (name.isInvalid() || rate.isInvalid()) {
                return;
            }
            try {
                if (employee == null) service.create(name.getValue(), role.getValue(), BigDecimal.valueOf(value), phone.getValue());
                else service.update(employee.getId(), name.getValue(), role.getValue(), BigDecimal.valueOf(value), phone.getValue());
                updateGrid();
                dialog.close();
            } catch (ArithmeticException exception) {
                Notification.show("Ставка должна содержать не более двух знаков после запятой");
            } catch (IllegalArgumentException exception) {
                Notification.show(exception.getMessage());
            }
        });
        create.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        if (employee != null) {
            Button delete = new Button("Удалить сотрудника", event -> {
                ConfirmDialog confirmation = new ConfirmDialog();
                confirmation.setHeader("Удалить сотрудника?");
                confirmation.setText("Сотрудник «" + employee.getFullName() + "» и все его записи смен будут удалены. Это действие нельзя отменить.");
                confirmation.setCancelable(true); confirmation.setCancelText("Отмена");
                confirmation.setConfirmText("Удалить"); confirmation.setConfirmButtonTheme("error primary");
                confirmation.addConfirmListener(confirmed -> {
                    try {
                        service.delete(employee.getId()); updateGrid(); dialog.close();
                        Notification.show("Сотрудник удален");
                    } catch (IllegalArgumentException exception) { Notification.show(exception.getMessage()); }
                });
                confirmation.open();
            });
            delete.addThemeVariants(ButtonVariant.LUMO_ERROR);
            dialog.getFooter().add(delete);
        }
        dialog.getFooter().add(new HorizontalLayout(create, new Button("Отмена", event -> dialog.close())));
        dialog.open();
        name.focus();
    }
}
