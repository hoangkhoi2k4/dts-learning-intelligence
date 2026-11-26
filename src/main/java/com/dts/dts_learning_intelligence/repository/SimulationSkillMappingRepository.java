package com.dts.dts_learning_intelligence.repository;

import com.dts.dts_learning_intelligence.entity.SimulationSkillMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SimulationSkillMappingRepository extends JpaRepository<SimulationSkillMapping, UUID> {
    Optional<SimulationSkillMapping> findByBankVersionAndTaxonomyVersionAndScenarioNumber(
            String bankVersion, String taxonomyVersion, Integer scenarioNumber);

    Optional<SimulationSkillMapping> findByBankVersionAndTaxonomyVersionAndScenarioId(
            String bankVersion, String taxonomyVersion, UUID scenarioId);

    List<SimulationSkillMapping> findByBankVersionAndTaxonomyVersionAndStatus(
            String bankVersion, String taxonomyVersion, String status);
}
