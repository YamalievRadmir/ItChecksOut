package ru.sklad.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.sklad.app.entity.RoleSettings;

public interface RoleSettingsRepository extends JpaRepository<RoleSettings, String> {}
