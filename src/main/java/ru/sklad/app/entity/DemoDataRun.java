package ru.sklad.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "demo_data_runs")
@Getter
@Setter
public class DemoDataRun {
    @Id private String id;
    private LocalDateTime completedAt;
    private String sourceSha256;
    private int products;
    private int sales;
    private int supplies;
    private int employees;
    private int shifts;
}
