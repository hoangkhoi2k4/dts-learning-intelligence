package com.dts.dts_learning_intelligence.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "simulation_skill_mapping")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SimulationSkillMapping {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String bankVersion;
    private String taxonomyVersion;
    private UUID scenarioId;
    private Integer scenarioNumber;
    private Integer chapterId;
    private String primarySkillCode;
    private String title;
    private String status;
}