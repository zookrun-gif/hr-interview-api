package com.zook.hrinterview.component.ai.interview;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class InterviewReportAiRequest {

    private String jobTitle;

    private String jobDescription;

    private String jobRequirements;

    private String candidateName;

    private String resumeText;

    private List<EvaluationDimension> evaluationDimensions;

    private List<InterviewReportMessage> messages;

    @Data
    public static class EvaluationDimension {

        private String name;

        private String description;

        private BigDecimal weight;
    }

    @Data
    public static class InterviewReportMessage {

        private String role;

        private String content;
    }
}
