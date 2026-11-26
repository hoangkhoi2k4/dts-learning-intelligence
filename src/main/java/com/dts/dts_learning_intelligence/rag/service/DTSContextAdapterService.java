package com.dts.dts_learning_intelligence.rag.service;

import com.dts.dts_learning_intelligence.entity.QuestionSkillMapping;
import com.dts.dts_learning_intelligence.entity.SkillTaxonomy;
import com.dts.dts_learning_intelligence.rag.util.VietnameseQueryCleaner;
import com.dts.dts_learning_intelligence.repository.QuestionSkillMappingRepository;
import com.dts.dts_learning_intelligence.repository.SkillTaxonomyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DTSContextAdapterService {

    private final QuestionSkillMappingRepository mappingRepository;
    private final SkillTaxonomyRepository taxonomyRepository;

    private static final String DEFAULT_TAXONOMY_VERSION = "tax-v1.0";
    private static final String DEFAULT_BANK_VERSION = "dts-2026-600-v1";

    public String extractContextSnippet(String contextType, String contextRefId, String licenseClass) {
        if (contextType == null || contextRefId == null || contextRefId.isBlank()) {
            return "";
        }

        try {
            switch (contextType.toUpperCase()) {
                case "THEORY_QUESTION" -> {
                    int questionId = Integer.parseInt(contextRefId);
                    var mappingOpt = mappingRepository.findByBankVersionAndAnyQuestionId(DEFAULT_BANK_VERSION, questionId);
                    if (mappingOpt.isPresent()) {
                        QuestionSkillMapping q = mappingOpt.get();
                        String skillName = resolveSkillName(q.getPrimarySkillCode());
                        return String.format("Học viên đang hỏi về câu hỏi lý thuyết số %d (Hạng %s, Kỹ năng: %s [%s], Điểm liệt: %s).",
                                questionId, licenseClass != null ? licenseClass : "B", skillName, q.getPrimarySkillCode(), q.getIsCritical());
                    }
                    return String.format("Học viên đang hỏi về câu hỏi lý thuyết số %d.", questionId);
                }
                case "THEORY_SKILL" -> {
                    SkillTaxonomy skill = resolveSkill(contextRefId);
                    if (skill != null) {
                        return String.format("Học viên đang tìm hiểu kỹ năng: %s (%s).", skill.getSkillName(), skill.getSkillCode());
                    }
                    return String.format("Học viên đang tìm hiểu kỹ năng có mã: %s.", contextRefId);
                }
                case "SIMULATION_SCENARIO" -> {
                    return String.format("Học viên đang thắc mắc về tình huống mô phỏng video số %s.", contextRefId);
                }
                default -> {
                    return "";
                }
            }
        } catch (Exception e) {
            log.warn("Could not extract context for type {} with refId {}: {}", contextType, contextRefId, e.getMessage());
            return "";
        }
    }

    /**
     * Trích xuất từ khóa pháp lý cốt lõi từ ngữ cảnh để bổ trợ cho bộ tìm kiếm RAG
     */
    public String extractSearchKeywords(String contextType, String contextRefId) {
        if (contextType == null || contextRefId == null || contextRefId.isBlank()) {
            return "";
        }

        try {
            if ("THEORY_QUESTION".equalsIgnoreCase(contextType)) {
                int questionId = Integer.parseInt(contextRefId);
                var mappingOpt = mappingRepository.findByBankVersionAndAnyQuestionId(DEFAULT_BANK_VERSION, questionId);
                if (mappingOpt.isPresent()) {
                    String skillName = resolveSkillName(mappingOpt.get().getPrimarySkillCode());
                    return VietnameseQueryCleaner.clean(skillName).rawKeywords();
                }
            } else if ("THEORY_SKILL".equalsIgnoreCase(contextType)) {
                SkillTaxonomy skill = resolveSkill(contextRefId);
                if (skill != null) {
                    return VietnameseQueryCleaner.clean(skill.getSkillName()).rawKeywords();
                }
            }
        } catch (Exception e) {
            log.warn("Could not extract search keywords for type {} with refId {}: {}", contextType, contextRefId, e.getMessage());
        }
        return "";
    }

    private String resolveSkillName(String skillCode) {
        if (skillCode == null) return "Chung";
        return taxonomyRepository.findByTaxonomyVersionAndSkillCode(DEFAULT_TAXONOMY_VERSION, skillCode)
                .map(SkillTaxonomy::getSkillName)
                .orElse(skillCode);
    }

    private SkillTaxonomy resolveSkill(String skillRefId) {
        if (skillRefId == null) return null;

        // 1. Tìm chính xác theo skill_code
        Optional<SkillTaxonomy> exact = taxonomyRepository.findByTaxonomyVersionAndSkillCode(DEFAULT_TAXONOMY_VERSION, skillRefId);
        if (exact.isPresent()) return exact.get();

        // 2. Tra cứu alias linh hoạt cho các mã hay dùng khi test
        String normalized = skillRefId.toUpperCase();
        String mappedCode = switch (normalized) {
            case "SKILL_INTERSECTION_GIVE_WAY", "RIGHT_OF_WAY", "INTERSECTION" -> "RULE_RIGHT_OF_WAY";
            case "ALCOHOL", "PROHIBITED" -> "RULE_PROHIBITED";
            case "SPEED", "DISTANCE" -> "RULE_SPEED_DIST";
            case "CONCEPT" -> "RULE_CONCEPT";
            default -> null;
        };

        if (mappedCode != null) {
            return taxonomyRepository.findByTaxonomyVersionAndSkillCode(DEFAULT_TAXONOMY_VERSION, mappedCode).orElse(null);
        }

        return null;
    }
}
