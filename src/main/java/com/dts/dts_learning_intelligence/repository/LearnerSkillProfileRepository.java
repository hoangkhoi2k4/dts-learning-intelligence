package com.dts.dts_learning_intelligence.repository;

import com.dts.dts_learning_intelligence.entity.LearnerSkillProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LearnerSkillProfileRepository extends JpaRepository<LearnerSkillProfile, UUID> {

    Optional<LearnerSkillProfile> findByUserIdAndLicenseClassAndBankVersionAndTaxonomyVersionAndSkillCode(
            UUID userId, String licenseClass, String bankVersion, String taxonomyVersion, String skillCode);

    List<LearnerSkillProfile> findByUserIdAndLicenseClassAndBankVersionAndTaxonomyVersion(
            UUID userId, String licenseClass, String bankVersion, String taxonomyVersion);

    List<LearnerSkillProfile> findByUserIdAndLicenseClassAndNextReviewAtLessThanEqual(
            UUID userId, String licenseClass, OffsetDateTime dueTime);
}
