package com.dts.dts_learning_intelligence.repository;

import com.dts.dts_learning_intelligence.entity.LearningEvidence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LearningEvidenceRepository extends JpaRepository<LearningEvidence, UUID> {

    boolean existsByEventId(UUID eventId);

    boolean existsBySourceOutcomeKey(String sourceOutcomeKey);

    Optional<LearningEvidence> findByEventId(UUID eventId);

    Optional<LearningEvidence> findBySourceOutcomeKey(String sourceOutcomeKey);

    long countBySessionId(UUID sessionId);

    long countByUserIdAndLicenseClassAndBankVersionAndPracticeQuestionId(
            UUID userId, String licenseClass, String bankVersion, Integer practiceQuestionId);

    List<LearningEvidence> findByUserIdAndLicenseClassAndBankVersionOrderByOccurredAtDesc(
            UUID userId, String licenseClass, String bankVersion);

    List<LearningEvidence> findByUserIdAndLicenseClassAndBankVersionAndPracticeQuestionIdOrderByOccurredAtAsc(
            UUID userId, String licenseClass, String bankVersion, Integer practiceQuestionId);

    @Query(value = "SELECT e.* FROM learning_evidence e " +
            "JOIN question_skill_mapping m ON e.practice_question_id = m.practice_question_id " +
            "AND e.bank_version = m.bank_version " +
            "WHERE e.user_id = :userId " +
            "AND e.license_class = :licenseClass " +
            "AND e.bank_version = :bankVersion " +
            "AND m.taxonomy_version = :taxonomyVersion " +
            "AND m.primary_skill_code = :skillCode " +
            "AND e.outcome IN ('CORRECT', 'INCORRECT')", nativeQuery = true)
    List<LearningEvidence> findValidEvidencesBySkill(
            @Param("userId") UUID userId,
            @Param("licenseClass") String licenseClass,
            @Param("bankVersion") String bankVersion,
            @Param("taxonomyVersion") String taxonomyVersion,
            @Param("skillCode") String skillCode);

    @Query(value = "SELECT practice_question_id, MAX(occurred_at) FROM learning_evidence " +
            "WHERE user_id = :userId AND license_class = :licenseClass AND bank_version = :bankVersion " +
            "GROUP BY practice_question_id", nativeQuery = true)
    List<Object[]> findRawLastAttemptedTimePerQuestion(
            @Param("userId") UUID userId,
            @Param("licenseClass") String licenseClass,
            @Param("bankVersion") String bankVersion);
}
