package com.zook.hrinterview.component.ai.interview;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class InterviewReportAiResult {

    private BigDecimal totalScore;

    private String dimensionScoresJson;

    private String strengths;

    private String risks;

    private String recommendation;

    private String followUpQuestions;

    private String rawReportJson;
}
