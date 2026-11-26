package com.dts.dts_learning_intelligence.rag.service;

import com.dts.dts_learning_intelligence.rag.dto.*;
import com.dts.dts_learning_intelligence.rag.util.VietnameseQueryCleaner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RAGOrchestrationService {

    private final TokenQuotaGuardService quotaGuardService;
    private final DTSContextAdapterService contextAdapterService;
    private final PracticeQuestionLookupService questionLookupService;
    private final HybridSearchService hybridSearchService;
    private final GeminiLLMClient geminiLLMClient;
    private final CitationValidatorService citationValidatorService;

    public RAGChatResponseDto processChat(UUID userId, RAGChatRequestDto request) {
        // 1. Chốt chặn Token: Tần suất (5 câu/phút) và Hạn ngạch ngày (30 câu/ngày)
        quotaGuardService.checkRateLimit(userId);
        quotaGuardService.validateDailyQuota(userId);

        // 2. Trích xuất thông tin chi tiết câu hỏi lý thuyết (nếu đang trong ngữ cảnh câu hỏi)
        QuestionDetailDto questionDetail = null;
        if ("THEORY_QUESTION".equalsIgnoreCase(request.getContextType()) && request.getContextRefId() != null) {
            try {
                int questionId = Integer.parseInt(request.getContextRefId().trim());
                questionDetail = questionLookupService.findQuestionDetail(questionId).orElse(null);
            } catch (Exception e) {
                log.warn("Invalid question ID format for contextRefId: {}", request.getContextRefId());
            }
        }

        // 3. Trích xuất ngữ cảnh học tập & từ khóa tìm kiếm
        String contextSnippet = buildLearningContextSnippet(request, questionDetail);
        String contextSearchKeywords = contextAdapterService.extractSearchKeywords(
                request.getContextType(),
                request.getContextRefId()
        );

        // 4. Xây dựng câu truy vấn tìm kiếm tối ưu (kết hợp câu hỏi học viên + từ khóa ngữ cảnh)
        String effectiveSearchQuery = buildEffectiveSearchQuery(request.getMessage(), contextSearchKeywords);
        log.info("RAG search query formulated: '{}' (from raw message: '{}', context: '{}')",
                effectiveSearchQuery, request.getMessage(), contextSearchKeywords);

        // 5. Tìm kiếm kết hợp Hybrid Search (Vector + Multi-Tier Full-Text Search + RRF)
        List<ScoredDocumentDto> retrievedDocs = hybridSearchService.search(effectiveSearchQuery, null, 3);
        List<LegalSourceDto> sources = hybridSearchService.toSources(retrievedDocs);

        // 6. Sinh câu trả lời qua Gemini LLM (khóa trần token, có fallback)
        GeminiGenerationResult genResult = geminiLLMClient.generateAnswer(
                request.getMessage(),
                retrievedDocs,
                contextSnippet
        );

        // 7. Hậu kiểm tra tính hợp lệ của trích dẫn nguồn
        citationValidatorService.hasValidCitations(genResult.getAnswerText(), retrievedDocs);

        // 8. Ghi nhận Token tiêu thụ thực tế vào DB
        quotaGuardService.recordUsage(userId, genResult.getTotalTokens());
        int remainingQueries = quotaGuardService.getRemainingQueries(userId);

        // 9. Tạo danh sách hành động gợi ý (Suggested Actions)
        List<String> suggestedActions = buildSuggestedActions(request.getContextType());

        return RAGChatResponseDto.builder()
                .answer(genResult.getAnswerText())
                .sources(sources)
                .contextUsed(contextSnippet.isBlank() ? "GENERAL" : request.getContextType())
                .remainingDailyQueries(remainingQueries)
                .isFallback(genResult.isFallback())
                .suggestedActions(suggestedActions)
                .questionDetail(questionDetail)
                .build();
    }

    private String buildLearningContextSnippet(RAGChatRequestDto request, QuestionDetailDto questionDetail) {
        if (questionDetail != null) {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Học viên đang làm Câu hỏi lý thuyết số %d (Hạng %s)\n",
                    questionDetail.getQuestionId(), request.getLicenseClass() != null ? request.getLicenseClass() : "B"));
            sb.append(String.format("- Nội dung câu hỏi: \"%s\"\n", questionDetail.getQuestionText()));
            if (questionDetail.getOptions() != null && !questionDetail.getOptions().isEmpty()) {
                sb.append("- Các phương án lựa chọn:\n");
                for (Map<String, Object> opt : questionDetail.getOptions()) {
                    sb.append(String.format("  [%s] %s\n", opt.get("label"), opt.get("text")));
                }
            }
            sb.append(String.format("- Đáp án đúng của đề thi: %s\n", questionDetail.getCorrectAnswer()));
            if (questionDetail.getExplanation() != null && !questionDetail.getExplanation().isBlank()) {
                sb.append(String.format("- Giải thích của đề thi: %s\n", questionDetail.getExplanation()));
            }
            return sb.toString();
        }

        return contextAdapterService.extractContextSnippet(
                request.getContextType(),
                request.getContextRefId(),
                request.getLicenseClass()
        );
    }

    private String buildEffectiveSearchQuery(String userMessage, String contextKeywords) {
        String cleanedUser = VietnameseQueryCleaner.clean(userMessage).rawKeywords();
        if (cleanedUser.isBlank()) {
            return contextKeywords != null ? contextKeywords : "";
        }
        if (contextKeywords != null && !contextKeywords.isBlank() && !cleanedUser.contains(contextKeywords)) {
            return cleanedUser + " " + contextKeywords;
        }
        return cleanedUser;
    }

    private List<String> buildSuggestedActions(String contextType) {
        List<String> actions = new ArrayList<>();
        if ("THEORY_QUESTION".equalsIgnoreCase(contextType)) {
            actions.add("Quay lại câu hỏi đang luyện");
            actions.add("Xem bài ôn liên quan");
        } else if ("THEORY_SKILL".equalsIgnoreCase(contextType)) {
            actions.add("Luyện tập củng cố kỹ năng này");
            actions.add("Xem hồ sơ năng lực");
        } else {
            actions.add("Hỏi tiếp về luật giao thông");
            actions.add("Xem gợi ý học tập AI");
        }
        return actions;
    }
}
