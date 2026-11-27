package com.dts.dts_learning_intelligence.rag.service;

import com.dts.dts_learning_intelligence.rag.config.RAGProperties;
import com.dts.dts_learning_intelligence.rag.dto.GeminiGenerationResult;
import com.dts.dts_learning_intelligence.rag.dto.ScoredDocumentDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class GeminiLLMClient {

    private final RestClient restClient;
    private final RAGProperties ragProperties;
    private final ObjectMapper objectMapper;

    public GeminiLLMClient(RAGProperties ragProperties, ObjectMapper objectMapper) {
        this.ragProperties = ragProperties;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com/v1beta")
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * Sinh câu trả lời giải thích luật với khóa trần token và fallback an toàn.
     */
    public GeminiGenerationResult generateAnswer(String userQuestion, List<ScoredDocumentDto> contextDocuments, String learningContextSnippet) {
        String apiKey = ragProperties.getGemini().getApiKey();
        String modelName = ragProperties.getGemini().getModel();
        int maxOutputTokens = ragProperties.getTokenLimits().getMaxOutputTokens();

        // 1. Kiểm tra an toàn: Nếu chưa có API key hoặc danh sách tài liệu trống
        if (apiKey == null || apiKey.trim().isEmpty()) {
            log.warn("GEMINI_API_KEY is not configured. Activating deterministic rule fallback.");
            return buildFallbackResult(contextDocuments, "Trợ lý đang chạy ở chế độ ngoại tuyến (chưa cấu hình API Key).");
        }

        if (contextDocuments == null || contextDocuments.isEmpty()) {
            return GeminiGenerationResult.builder()
                    .answerText("Dựa trên kho tri thức pháp luật giao thông hiện có, tôi chưa tìm thấy căn cứ điều khoản liên quan để giải đáp câu hỏi này. Bạn vui lòng kiểm tra lại câu hỏi hoặc hỏi kỹ năng khác.")
                    .promptTokens(0)
                    .completionTokens(0)
                    .totalTokens(0)
                    .isFallback(false)
                    .build();
        }

        // 2. Xây dựng System Instruction & Prompt
        String systemInstruction = """
                Bạn là Trợ lý Tri thức Pháp luật & Giảng viên Đào tạo Lái xe DTS hàng đầu.
                Nhiệm vụ của bạn là giải thích thấu đáo quy định pháp luật giao thông cho học viên dựa HOÀN TOÀN vào các tài liệu được cung cấp dưới đây.
                
                PHONG CÁCH GIẢNG DẠY:
                - Thân thiện, sư phạm, gần gũi, chuẩn xác pháp lý, mang phong thái một chuyên gia đào tạo lái xe tận tâm.
                - Trình bày mạch lạc, sử dụng định dạng Markdown rõ ràng, mỗi phần BẮT BUỘC xuống dòng riêng biệt.
                
                CẤU TRÚC PHẢN HỒI BẮT BUỘC (Mỗi mục lớn và mỗi ý phân tích/mẹo BẮT BUỘC phải xuống dòng riêng biệt, không viết liền dòng):
                1. TRẢ LỜI TRỰC DIỆN:
                (Trả lời thẳng vào câu hỏi trong 1-2 câu đầu tiên, nêu rõ đúng/sai hoặc kết luận chính).
                
                2. PHÂN TÍCH CHI TIẾT & MẸO GHI NHỚ:
                * Phân tích chi tiết: (Giải thích cặn kẽ vì sao, điểm mấu chốt của quy tắc hoặc câu hỏi).
                * Mẹo ghi nhớ thực chiến: (Quy tắc vàng hoặc mẹo ngắn gọn để học viên nhớ ngay khi thi hoặc lái xe ngoài đời).
                
                3. CĂN CỨ PHÁP LÝ:
                (Trích dẫn rõ Điều, Khoản, Tên văn bản pháp luật dựa trên tài liệu được cung cấp).
                
                4. LỜI KHUYÊN AN TOÀN:
                (1 câu đúc kết kinh nghiệm lái xe an toàn thực tế ngoài đời thực).
                
                QUY TẮC BẮT BUỘC:
                1. BẮT BUỘC phải xuống dòng riêng biệt cho từng mục: tiêu đề mục, 'Phân tích chi tiết:', 'Mẹo ghi nhớ thực chiến:'. Tuyệt đối không viết liền nhiều ý trên cùng 1 dòng.
                2. Không sử dụng 3 dấu sao liên tiếp (***) mà chỉ dùng định dạng in đậm chuẩn (**in đậm**). Không để dấu ** rơi rụng ở cuối câu.
                3. Chỉ sử dụng thông tin trong phần TÀI LIỆU THAM KHẢO. Tuyệt đối không tự bịa đặt điều luật, số hiệu văn bản hoặc mức tiền phạt.
                4. Nếu tài liệu không đủ thông tin kết luận, phải trả lời rõ là chưa đủ căn cứ pháp lý trong kho tri thức hiện có.
                """;

        StringBuilder userContentBuilder = new StringBuilder();
        if (learningContextSnippet != null && !learningContextSnippet.isBlank()) {
            userContentBuilder.append("【NGỮ CẢNH HỌC TẬP】:\n").append(learningContextSnippet).append("\n\n");
        }

        userContentBuilder.append("【TÀI LIỆU THAM KHẢO】:\n");
        for (int i = 0; i < contextDocuments.size(); i++) {
            ScoredDocumentDto doc = contextDocuments.get(i);
            userContentBuilder.append(String.format("--- Tài liệu %d: %s ---\n", i + 1, doc.formatCitation()));
            userContentBuilder.append(doc.getContent()).append("\n\n");
        }

        userContentBuilder.append("【CÂU HỎI CỦA HỌC VIÊN】:\n").append(userQuestion);

        // 3. Chuẩn bị Request Body với cấu hình trần Token nghiêm ngặt
        Map<String, Object> generationConfig = Map.of(
                "maxOutputTokens", maxOutputTokens, // Khóa cứng trần token đầu ra (600 tokens)
                "temperature", 0.2,                 // Nhiệt độ thấp để câu trả lời chuẩn xác
                "topP", 0.8
        );

        Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                        Map.of("role", "user", "parts", List.of(Map.of("text", userContentBuilder.toString())))
                ),
                "systemInstruction", Map.of(
                        "parts", List.of(Map.of("text", systemInstruction))
                ),
                "generationConfig", generationConfig
        );

        // 4. Gọi API với cơ chế Circuit Breaker / Exception Fallback
        try {
            String responseString = restClient.post()
                    .uri("/models/{model}:generateContent?key={key}", modelName, apiKey)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            return parseGeminiResponse(responseString);
        } catch (Exception ex) {
            log.error("Gemini API call failed (quota exceeded, network timeout, or invalid key). Falling back to direct legal context: {}", ex.getMessage());
            return buildFallbackResult(contextDocuments, "Hệ thống AI đang quá tải hoặc gặp gián đoạn tạm thời.");
        }
    }

    private GeminiGenerationResult parseGeminiResponse(String responseJson) {
        try {
            JsonNode root = objectMapper.readTree(responseJson);
            JsonNode candidate = root.path("candidates").get(0);
            String text = candidate.path("content").path("parts").get(0).path("text").asText();

            JsonNode usageNode = root.path("usageMetadata");
            int promptTokens = usageNode.path("promptTokenCount").asInt(0);
            int completionTokens = usageNode.path("candidatesTokenCount").asInt(0);
            int totalTokens = usageNode.path("totalTokenCount").asInt(promptTokens + completionTokens);

            return GeminiGenerationResult.builder()
                    .answerText(text)
                    .promptTokens(promptTokens)
                    .completionTokens(completionTokens)
                    .totalTokens(totalTokens)
                    .isFallback(false)
                    .build();
        } catch (Exception e) {
            log.error("Failed to parse Gemini response JSON: {}", e.getMessage());
            throw new RuntimeException("Lỗi cấu trúc phản hồi từ Gemini LLM", e);
        }
    }

    private GeminiGenerationResult buildFallbackResult(List<ScoredDocumentDto> contextDocuments, String note) {
        StringBuilder sb = new StringBuilder();
        sb.append(note).append("\n\nDưới đây là các căn cứ quy định pháp luật được trích xuất trực tiếp liên quan đến câu hỏi của bạn:\n\n");
        for (int i = 0; i < Math.min(3, contextDocuments.size()); i++) {
            ScoredDocumentDto doc = contextDocuments.get(i);
            sb.append(String.format("📌 **%s**:\n%s\n\n", doc.formatCitation(), doc.getContent()));
        }

        return GeminiGenerationResult.builder()
                .answerText(sb.toString())
                .promptTokens(0)
                .completionTokens(0)
                .totalTokens(0)
                .isFallback(true)
                .build();
    }
}
