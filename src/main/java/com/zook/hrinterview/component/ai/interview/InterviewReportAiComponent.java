package com.zook.hrinterview.component.ai.interview;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zook.hrinterview.component.ai.openai.OpenAiChatComponent;
import com.zook.hrinterview.interfaces.setting.service.AiInterviewSettingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
public class InterviewReportAiComponent {

    @Resource
    private InterviewReportAiProperties properties;

    @Resource
    private OpenAiChatComponent openAiChatComponent;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private AiInterviewSettingService aiInterviewSettingService;

    public Optional<InterviewReportAiResult> generate(InterviewReportAiRequest request) {
        if (!available()) {
            return Optional.empty();
        }
        try {
            AiInterviewBoundaryConfig boundaryConfig = aiInterviewSettingService.currentBoundaryConfig();
            String content = openAiChatComponent.chat(systemPrompt(), buildUserPrompt(request, boundaryConfig), 0.2);
            JsonNode report = objectMapper.readTree(extractJson(content));
            return Optional.of(toResult(report, request, boundaryConfig));
        } catch (Exception ex) {
            log.warn("AI interview report generation failed, fallback to rule report", ex);
            return Optional.empty();
        }
    }

    private boolean available() {
        return Boolean.TRUE.equals(properties.getEnabled()) && openAiChatComponent.available();
    }

    private String systemPrompt() {
        return "你是一名严谨的 HR AI 面试评估官。只输出严格 JSON，不要 Markdown，不要解释。"
                + "评分必须主要依据候选人在本次面试中的实际回答，回答质量、具体证据、表达完整度合计权重不得低于70%。"
                + "岗位 JD、能力要求和简历只能作为辅助上下文，不能因为简历或岗位表面匹配就给高分。"
                + "如果岗位提供了评分维度和权重，必须严格按这些维度逐项评分，不能替换成自己的维度。"
                + "也不能因为候选人存在某个短板就否定全部表现，应区分核心能力、可补短板和致命风险。"
                + "实时语音转写可能存在错字、断句和口语重复，请按语义理解，不要因 ASR 错字或口语化表达过度扣分。"
                + "如果候选人回答很短、泛泛而谈、没有案例、没有技术细节或答非所问，必须明显降分；"
                + "如果候选人能给出真实工作案例、服务流程、业务工具、解决方案、客户处理或交付结果，即使细节不完美，也应进入中等或中上评分区间。";
    }

    private String buildUserPrompt(InterviewReportAiRequest request, AiInterviewBoundaryConfig boundaryConfig) {
        StringBuilder builder = new StringBuilder();
        builder.append("请根据下面的岗位、候选人简历和完整面试对话，生成面试评估报告。\n");
        builder.append("必须输出 JSON 结构：\n");
        builder.append("{\n");
        builder.append("  \"totalScore\": 0,\n");
        builder.append("  \"dimensionScores\": [{\"name\":\"回答有效性\",\"score\":0,\"comment\":\"\"}],\n");
        builder.append("  \"strengths\": \"\",\n");
        builder.append("  \"risks\": \"\",\n");
        builder.append("  \"recommendation\": \"RECOMMEND|HOLD|REJECT\",\n");
        builder.append("  \"followUpQuestions\": [\"\"],\n");
        builder.append("  \"summary\": \"\"\n");
        builder.append("}\n\n");
        List<InterviewReportAiRequest.EvaluationDimension> dimensions = expectedDimensions(request);
        if (dimensions.isEmpty()) {
            builder.append("岗位未配置评分维度，请根据岗位 JD、能力要求、候选人简历和本次面试对话，自动生成 4-6 个评分维度。")
                    .append("维度必须贴合岗位，不要套固定模板；dimensionScores 的 name 使用你生成的维度名，comment 必须引用候选人本次面试回答中的证据。")
                    .append("totalScore 必须根据这些维度的表现综合计算，不能凭整体感觉单独给分。\n");
        } else {
            builder.append("评分维度和权重必须严格使用下面列表，dimensionScores 只能输出这些维度，name 必须完全一致，comment 必须引用候选人本次面试回答中的证据：\n");
            for (InterviewReportAiRequest.EvaluationDimension dimension : dimensions) {
                builder.append("- ").append(nullToEmpty(dimension.getName()))
                        .append("，权重 ").append(normalizeWeight(dimension.getWeight()))
                        .append("%，说明：").append(nullToEmpty(dimension.getDescription()))
                        .append('\n');
            }
            builder.append("totalScore 必须按以上维度权重加权计算，不能凭整体感觉单独给分。\n");
        }
        builder.append("推荐规则：75分及以上 RECOMMEND，60-74.99 HOLD，60分以下 REJECT。\n");
        builder.append("硬性封顶规则：有效回答少于").append(positiveOrDefault(boundaryConfig.getMinEffectiveAnswerCount(), 2))
                .append("轮最高").append(positiveOrDefault(boundaryConfig.getInsufficientAnswerMaxScore(), 60))
                .append("分；没有真实案例或细节最高").append(positiveOrDefault(boundaryConfig.getNoEvidenceMaxScore(), 70))
                .append("分；岗位核心能力证据不足最高").append(positiveOrDefault(boundaryConfig.getWeakJobMatchMaxScore(), 74))
                .append("分；严重答非所问或核心能力明显不匹配最高").append(positiveOrDefault(boundaryConfig.getWeakAnswerMaxScore(), 59))
                .append("分。\n");
        builder.append("评分锚点：\n");
        builder.append("- 85-95：回答深入，能给出清晰方法论、关键流程、业务细节、量化结果和复杂问题处理过程。\n");
        builder.append("- 75-84：回答较完整，有多个真实工作案例，能说明方案、流程、工具、协作方式和部分落地细节，岗位核心能力较匹配。\n");
        builder.append("- 65-74：回答有实际工作经历和业务实践，能覆盖主要工作流程，但细节、数据或表达严谨性不足，适合标记待定继续复核。\n");
        builder.append("- 55-64：有部分关键词和零散经验，但证据较弱、回答较泛或岗位关键短板明显。\n");
        builder.append("- 55以下：有效回答少、明显答非所问、缺少真实项目证据，或岗位核心能力明显不匹配。\n");
        builder.append("校准要求：如果候选人完成5轮以上有效回答，并围绕目标岗位给出多个真实案例、服务流程、客户沟通、方案设计、问题处理、工具使用或交付结果，除非岗位核心能力完全不匹配，总分通常不应低于65。");
        builder.append("如果候选人存在某项短板，但岗位核心能力或可迁移经验有证据，应在岗位匹配度和风险点中体现，不要把所有维度都压到不合格。");
        builder.append("对高端旅游定制师等服务销售类岗位，应重点评估目的地/产品理解、客户需求挖掘、行程方案设计、预算和资源协调、成交转化、服务意识、应急处理和高净值客户沟通能力。\n\n");
        builder.append("岗位信息：\n");
        builder.append("岗位名称：").append(nullToEmpty(request.getJobTitle())).append('\n');
        builder.append("岗位JD：").append(limit(stripHtml(request.getJobDescription()), 2500)).append('\n');
        builder.append("能力要求：").append(limit(stripHtml(request.getJobRequirements()), 2000)).append("\n\n");
        builder.append("候选人信息：\n");
        builder.append("姓名：").append(nullToEmpty(request.getCandidateName())).append('\n');
        builder.append("简历：").append(limit(stripHtml(request.getResumeText()), 5000)).append("\n\n");
        builder.append("面试对话：\n");
        for (InterviewReportAiRequest.InterviewReportMessage message : request.getMessages() == null ? List.<InterviewReportAiRequest.InterviewReportMessage>of() : request.getMessages()) {
            if (message == null || !StringUtils.hasText(message.getContent())) {
                continue;
            }
            builder.append(roleLabel(message.getRole())).append("：")
                    .append(limit(stripHtml(message.getContent()), 1000))
                    .append('\n');
        }
        return limit(builder.toString(), properties.getMaxContentChars() == null ? 18000 : properties.getMaxContentChars());
    }

    private InterviewReportAiResult toResult(JsonNode report, InterviewReportAiRequest request, AiInterviewBoundaryConfig boundaryConfig) throws Exception {
        List<InterviewReportAiRequest.EvaluationDimension> expectedDimensions = expectedDimensions(request);
        List<Map<String, Object>> dimensions = normalizeDimensions(report.path("dimensionScores"), expectedDimensions);
        List<String> capReasons = new ArrayList<>();
        BigDecimal baseScore = dimensions.isEmpty()
                ? clampScore(decimalValue(report.path("totalScore")))
                : weightedTotalScore(dimensions, expectedDimensions);
        BigDecimal totalScore = applyScoreCaps(baseScore, request, dimensions, boundaryConfig, capReasons);
        String recommendation = normalizeRecommendation(report.path("recommendation").asText(""), totalScore);
        List<String> followUpQuestions = normalizeStringList(report.path("followUpQuestions"));

        InterviewReportAiResult result = new InterviewReportAiResult();
        result.setTotalScore(totalScore);
        result.setDimensionScoresJson(objectMapper.writeValueAsString(dimensions));
        result.setStrengths(text(report, "strengths"));
        result.setRisks(text(report, "risks"));
        result.setRecommendation(recommendation);
        result.setFollowUpQuestions(objectMapper.writeValueAsString(followUpQuestions));
        result.setRawReportJson(buildRawReportJson(report, totalScore, dimensions, capReasons));
        return result;
    }

    private List<Map<String, Object>> normalizeDimensions(JsonNode dimensionsNode, List<InterviewReportAiRequest.EvaluationDimension> expectedDimensions) {
        Map<String, JsonNode> aiDimensionMap = new LinkedHashMap<>();
        if (dimensionsNode != null && dimensionsNode.isArray()) {
            for (JsonNode item : dimensionsNode) {
                String name = text(item, "name");
                if (StringUtils.hasText(name)) {
                    aiDimensionMap.put(name, item);
                }
            }
        }
        if (expectedDimensions == null || expectedDimensions.isEmpty()) {
            return normalizeDynamicDimensions(dimensionsNode);
        }
        List<Map<String, Object>> dimensions = new ArrayList<>();
        for (InterviewReportAiRequest.EvaluationDimension expectedDimension : expectedDimensions) {
            JsonNode item = aiDimensionMap.get(nullToEmpty(expectedDimension.getName()));
            Map<String, Object> dimension = new LinkedHashMap<>();
            dimension.put("name", nullToEmpty(expectedDimension.getName()));
            dimension.put("score", item == null ? BigDecimal.ZERO : clampScore(decimalValue(item.path("score"))));
            dimension.put("comment", item == null ? "AI 未返回该维度评分，系统按 0 分处理。" : text(item, "comment"));
            dimensions.add(dimension);
        }
        return dimensions;
    }

    private List<Map<String, Object>> normalizeDynamicDimensions(JsonNode dimensionsNode) {
        List<Map<String, Object>> dimensions = new ArrayList<>();
        if (dimensionsNode == null || !dimensionsNode.isArray()) {
            return dimensions;
        }
        for (JsonNode item : dimensionsNode) {
            String name = text(item, "name");
            if (!StringUtils.hasText(name)) {
                continue;
            }
            Map<String, Object> dimension = new LinkedHashMap<>();
            dimension.put("name", name.trim());
            dimension.put("score", clampScore(decimalValue(item.path("score"))));
            dimension.put("comment", text(item, "comment"));
            dimensions.add(dimension);
            if (dimensions.size() >= 6) {
                break;
            }
        }
        return dimensions;
    }

    private List<String> normalizeStringList(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                String value = item.asText("");
                if (StringUtils.hasText(value)) {
                    values.add(value.trim());
                }
            }
        }
        return values;
    }

    private String normalizeRecommendation(String recommendation, BigDecimal score) {
        if ("RECOMMEND".equalsIgnoreCase(recommendation)) {
            return "RECOMMEND";
        }
        if ("HOLD".equalsIgnoreCase(recommendation)) {
            return "HOLD";
        }
        if ("REJECT".equalsIgnoreCase(recommendation)) {
            return "REJECT";
        }
        if (score.compareTo(BigDecimal.valueOf(75)) >= 0) {
            return "RECOMMEND";
        }
        if (score.compareTo(BigDecimal.valueOf(60)) >= 0) {
            return "HOLD";
        }
        return "REJECT";
    }

    private String buildRawReportJson(JsonNode report, BigDecimal finalScore, List<Map<String, Object>> dimensions, List<String> capReasons) throws Exception {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("generatedBy", "ai-interview-report-v1");
        raw.put("provider", "openai-compatible");
        raw.put("model", openAiChatComponent.model());
        raw.put("finalScore", finalScore);
        raw.put("normalizedDimensions", dimensions);
        raw.put("scoreCapReasons", capReasons);
        raw.put("report", report);
        return objectMapper.writeValueAsString(raw);
    }

    private List<InterviewReportAiRequest.EvaluationDimension> expectedDimensions(InterviewReportAiRequest request) {
        List<InterviewReportAiRequest.EvaluationDimension> dimensions = request == null ? null : request.getEvaluationDimensions();
        if (dimensions != null && !dimensions.isEmpty()) {
            List<InterviewReportAiRequest.EvaluationDimension> validDimensions = new ArrayList<>();
            for (InterviewReportAiRequest.EvaluationDimension dimension : dimensions) {
                if (dimension != null && StringUtils.hasText(dimension.getName())) {
                    validDimensions.add(dimension);
                }
            }
            if (!validDimensions.isEmpty()) {
                return validDimensions;
            }
        }
        return List.of();
    }

    private BigDecimal weightedTotalScore(List<Map<String, Object>> dimensions, List<InterviewReportAiRequest.EvaluationDimension> expectedDimensions) {
        BigDecimal weightedScore = BigDecimal.ZERO;
        BigDecimal weightSum = BigDecimal.ZERO;
        for (int i = 0; i < dimensions.size(); i++) {
            BigDecimal score = mapScore(dimensions.get(i));
            BigDecimal weight = expectedDimensions == null || expectedDimensions.isEmpty()
                    ? BigDecimal.ONE
                    : normalizeWeight(i < expectedDimensions.size() ? expectedDimensions.get(i).getWeight() : null);
            weightedScore = weightedScore.add(score.multiply(weight));
            weightSum = weightSum.add(weight);
        }
        if (weightSum.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return clampScore(weightedScore.divide(weightSum, 2, RoundingMode.HALF_UP));
    }

    private BigDecimal applyScoreCaps(
            BigDecimal score,
            InterviewReportAiRequest request,
            List<Map<String, Object>> dimensions,
            AiInterviewBoundaryConfig boundaryConfig,
            List<String> capReasons
    ) {
        int effectiveAnswerCount = effectiveCandidateAnswerCount(request);
        int concreteEvidenceCount = concreteEvidenceCount(request);
        int minEffectiveAnswerCount = positiveOrDefault(boundaryConfig.getMinEffectiveAnswerCount(), 2);
        if (effectiveAnswerCount < minEffectiveAnswerCount) {
            int maxScore = positiveOrDefault(boundaryConfig.getInsufficientAnswerMaxScore(), 60);
            score = capScore(score, BigDecimal.valueOf(maxScore), "有效回答少于" + minEffectiveAnswerCount + "轮，最高" + maxScore + "分。", capReasons);
        }
        if (concreteEvidenceCount <= 0) {
            int maxScore = positiveOrDefault(boundaryConfig.getNoEvidenceMaxScore(), 70);
            score = capScore(score, BigDecimal.valueOf(maxScore), "没有真实案例、流程、工具、数据或处理细节，最高" + maxScore + "分。", capReasons);
        }
        BigDecimal jobMatchScore = dimensionScoreByKeywords(dimensions, List.of("岗位匹配度", "行程", "方案", "目的地", "需求"));
        if (jobMatchScore != null && jobMatchScore.compareTo(BigDecimal.valueOf(60)) < 0) {
            int maxScore = positiveOrDefault(boundaryConfig.getWeakJobMatchMaxScore(), 74);
            score = capScore(score, BigDecimal.valueOf(maxScore), "岗位匹配度低于60分，最高" + maxScore + "分。", capReasons);
        }
        BigDecimal answerScore = dimensionScoreByKeywords(dimensions, List.of("回答有效性", "客户需求", "服务意识", "表达完整度"));
        if (answerScore != null && answerScore.compareTo(BigDecimal.valueOf(45)) < 0) {
            int maxScore = positiveOrDefault(boundaryConfig.getWeakAnswerMaxScore(), 59);
            score = capScore(score, BigDecimal.valueOf(maxScore), "回答有效性低于45分，最高" + maxScore + "分。", capReasons);
        }
        return clampScore(score);
    }

    private BigDecimal capScore(BigDecimal score, BigDecimal cap, String reason, List<String> capReasons) {
        if (score.compareTo(cap) > 0) {
            capReasons.add(reason);
            return cap;
        }
        return score;
    }

    private int effectiveCandidateAnswerCount(InterviewReportAiRequest request) {
        int count = 0;
        for (InterviewReportAiRequest.InterviewReportMessage message : request == null || request.getMessages() == null
                ? List.<InterviewReportAiRequest.InterviewReportMessage>of()
                : request.getMessages()) {
            if (message == null || !"CANDIDATE".equals(message.getRole())) {
                continue;
            }
            String content = stripHtml(message.getContent()).trim();
            if (content.length() >= 12) {
                count++;
            }
        }
        return count;
    }

    private int concreteEvidenceCount(InterviewReportAiRequest request) {
        StringBuilder builder = new StringBuilder();
        for (InterviewReportAiRequest.InterviewReportMessage message : request == null || request.getMessages() == null
                ? List.<InterviewReportAiRequest.InterviewReportMessage>of()
                : request.getMessages()) {
            if (message != null && "CANDIDATE".equals(message.getRole())) {
                builder.append(stripHtml(message.getContent())).append('\n');
            }
        }
        String text = builder.toString().toLowerCase();
        int count = 0;
        String[] keywords = {
                "项目", "客户", "需求", "方案", "行程", "预算", "资源", "供应商", "成交", "投诉",
                "负责", "设计", "优化", "处理", "解决", "结果", "提升", "数据", "流程", "工具",
                "mysql", "redis", "mq", "spring", "vue", "直播", "订单", "支付"
        };
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                count++;
            }
        }
        return count;
    }

    private BigDecimal dimensionScore(List<Map<String, Object>> dimensions, String name) {
        for (Map<String, Object> dimension : dimensions) {
            if (name.equals(dimension.get("name"))) {
                return mapScore(dimension);
            }
        }
        return null;
    }

    private BigDecimal dimensionScoreByKeywords(List<Map<String, Object>> dimensions, List<String> keywords) {
        for (Map<String, Object> dimension : dimensions) {
            String name = String.valueOf(dimension.get("name"));
            for (String keyword : keywords) {
                if (name.contains(keyword)) {
                    return mapScore(dimension);
                }
            }
        }
        return null;
    }

    private BigDecimal mapScore(Map<String, Object> dimension) {
        Object value = dimension == null ? null : dimension.get("score");
        if (value instanceof BigDecimal score) {
            return clampScore(score);
        }
        try {
            return clampScore(new BigDecimal(String.valueOf(value)));
        } catch (Exception ex) {
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal normalizeWeight(BigDecimal weight) {
        if (weight == null || weight.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ONE;
        }
        return weight.setScale(2, RoundingMode.HALF_UP);
    }

    private int positiveOrDefault(Integer value, int defaultValue) {
        return value == null || value <= 0 ? defaultValue : value;
    }

    private BigDecimal decimalValue(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return BigDecimal.ZERO;
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        try {
            return new BigDecimal(node.asText("0").trim());
        } catch (Exception ex) {
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal clampScore(BigDecimal score) {
        if (score == null) {
            return BigDecimal.ZERO;
        }
        if (score.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        if (score.compareTo(BigDecimal.valueOf(100)) > 0) {
            return BigDecimal.valueOf(100);
        }
        return score.setScale(2, RoundingMode.HALF_UP);
    }

    private String extractJson(String content) {
        String value = content == null ? "" : content.trim();
        int start = value.indexOf('{');
        int end = value.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return value.substring(start, end + 1);
        }
        return value;
    }

    private String roleLabel(String role) {
        if ("CANDIDATE".equals(role)) {
            return "候选人";
        }
        if ("AI".equals(role)) {
            return "AI面试官";
        }
        return nullToEmpty(role);
    }

    private String text(JsonNode node, String field) {
        String value = node == null ? "" : node.path(field).asText("");
        return value == null ? "" : value.trim();
    }

    private String stripHtml(String value) {
        return nullToEmpty(value).replaceAll("<[^>]+>", " ");
    }

    private String limit(String value, int maxChars) {
        String text = nullToEmpty(value);
        return text.length() <= maxChars ? text : text.substring(0, maxChars);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
