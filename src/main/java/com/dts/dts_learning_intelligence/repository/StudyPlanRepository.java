package com.dts.dts_learning_intelligence.repository;

import com.dts.dts_learning_intelligence.entity.StudyPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StudyPlanRepository extends JpaRepository<StudyPlan, UUID> {

    List<StudyPlan> findByUserIdAndLicenseClassAndStatusOrderByCreatedAtDesc(
            UUID userId, String licenseClass, String status);

    Optional<StudyPlan> findFirstByUserIdAndLicenseClassAndStatusOrderByCreatedAtDesc(
            UUID userId, String licenseClass, String status);
}
