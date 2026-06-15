package com.skillguard;

import java.io.IOException;

public final class LlmRemediationService {
    private LlmRemediationService() {
    }

    public static void apply(ScanSummary summary, LlmConfig config) {
        for (SkillReport report : summary.reports) {
            for (int i = 0; i < report.findings.size(); i++) {
                Finding finding = report.findings.get(i);
                if (shouldSkip(finding)) {
                    continue;
                }
                try {
                    String suggestion = LlmClient.suggest(config, finding);
                    if (suggestion != null && !suggestion.trim().isEmpty()) {
                        report.findings.set(i, finding.withRecommendation("AI 个性化整改建议\n" + suggestion.trim()));
                    }
                } catch (IOException e) {
                    report.findings.set(i, finding.withRecommendation(finding.recommendation
                            + "\nAI 个性化整改建议生成失败，请检查模型服务或重试。"));
                }
            }
        }
    }

    private static boolean shouldSkip(Finding finding) {
        return finding == null
                || "false_positive".equals(finding.decision)
                || "low_risk_notice".equals(finding.decision);
    }
}
