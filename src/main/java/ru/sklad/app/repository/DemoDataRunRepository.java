package ru.sklad.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.sklad.app.entity.DemoDataRun;

public interface DemoDataRunRepository extends JpaRepository<DemoDataRun, String> {}
