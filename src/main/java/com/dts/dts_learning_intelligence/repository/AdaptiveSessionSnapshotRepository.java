package com.dts.dts_learning_intelligence.repository;

import com.dts.dts_learning_intelligence.entity.AdaptiveSessionSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AdaptiveSessionSnapshotRepository extends JpaRepository<AdaptiveSessionSnapshot, UUID> {

    Optional<AdaptiveSessionSnapshot> findByStudyPlanId(UUID studyPlanId);

    Optional<AdaptiveSessionSnapshot> findByIdempotencyKey(String idempotencyKey);

    Optional<AdaptiveSessionSnapshot> findByPracticeSessionId(UUID practiceSessionId);

    boolean existsByIdempotencyKey(String idempotencyKey);
}
