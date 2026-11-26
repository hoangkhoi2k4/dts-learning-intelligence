package com.dts.dts_learning_intelligence.rag.repository;

import com.dts.dts_learning_intelligence.rag.entity.UserRagUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRagUsageRepository extends JpaRepository<UserRagUsage, UUID> {

    Optional<UserRagUsage> findByUserIdAndUsageDate(UUID userId, LocalDate usageDate);
}
