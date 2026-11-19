package com.dts.dts_learning_intelligence.repository;

import com.dts.dts_learning_intelligence.entity.QuestionSkillMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface QuestionSkillMappingRepository extends JpaRepository<QuestionSkillMapping, UUID> {

    Optional<QuestionSkillMapping> findByBankVersionAndTaxonomyVersionAndContentBuilderQuestionId(
            String bankVersion, String taxonomyVersion, UUID contentBuilderQuestionId);

    Optional<QuestionSkillMapping> findByBankVersionAndTaxonomyVersionAndPracticeQuestionId(
            String bankVersion, String taxonomyVersion, Integer practiceQuestionId);

    List<QuestionSkillMapping> findByBankVersionAndTaxonomyVersionAndStatus(
            String bankVersion, String taxonomyVersion, String status);

    List<QuestionSkillMapping> findByBankVersionAndTaxonomyVersionAndPrimarySkillCode(
            String bankVersion, String taxonomyVersion, String primarySkillCode);

    @Query(value = "SELECT * FROM question_skill_mapping m " +
            "WHERE m.bank_version = :bankVersion " +
            "AND m.taxonomy_version = :taxonomyVersion " +
            "AND m.status = 'PUBLISHED' " +
            "AND jsonb_exists(m.applicable_licenses, :licenseClass)", nativeQuery = true)
    List<QuestionSkillMapping> findPublishedByBankAndLicense(
            @Param("bankVersion") String bankVersion,
            @Param("taxonomyVersion") String taxonomyVersion,
            @Param("licenseClass") String licenseClass);

    Optional<QuestionSkillMapping> findByBankVersionAndPracticeQuestionIdAndStatus(
            String bankVersion, Integer practiceQuestionId, String status);
}
