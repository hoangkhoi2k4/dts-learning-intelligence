package com.dts.dts_learning_intelligence.repository;

import com.dts.dts_learning_intelligence.entity.SkillTaxonomy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SkillTaxonomyRepository extends JpaRepository<SkillTaxonomy, UUID> {

    Optional<SkillTaxonomy> findByTaxonomyVersionAndSkillCode(String taxonomyVersion, String skillCode);

    List<SkillTaxonomy> findByTaxonomyVersionAndStatus(String taxonomyVersion, String status);

    List<SkillTaxonomy> findByTaxonomyVersionAndChapterId(String taxonomyVersion, Integer chapterId);

    List<SkillTaxonomy> findByTaxonomyVersion(String taxonomyVersion);
}
