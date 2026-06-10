package com.skillguard;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class SkillReport {
    public final String skillName;
    public final Path skillPath;
    public final List<Finding> rawFindings = new ArrayList<>();
    public final List<Finding> filteredFindings = new ArrayList<>();
    public final List<Finding> findings = new ArrayList<>();
    public Path entryFile;
    public String scanScope = "UNKNOWN";
    public int filesScanned;
    public int filesSkipped;
    public int directoriesSkipped;
    public int unsupportedFilesSkipped;
    public int structureScore;
    public int safetyScore;
    public int testFitnessScore;
    public int totalScore;
    public Severity riskLevel = Severity.INFO;

    public SkillReport(String skillName, Path skillPath) {
        this.skillName = skillName;
        this.skillPath = skillPath;
    }

    public Map<Severity, Integer> counts() {
        Map<Severity, Integer> counts = new EnumMap<>(Severity.class);
        for (Severity severity : Severity.values()) {
            counts.put(severity, 0);
        }
        for (Finding finding : findings) {
            counts.put(finding.severity, counts.get(finding.severity) + 1);
        }
        return counts;
    }

    public int rawFindingsCount() {
        return rawFindings.size();
    }

    public int filteredFindingsCount() {
        return filteredFindings.size();
    }

    public Map<String, Integer> decisionCounts() {
        Map<String, Integer> counts = new java.util.TreeMap<>();
        for (Finding finding : findings) {
            increment(counts, finding.decision);
        }
        for (Finding finding : filteredFindings) {
            increment(counts, finding.decision);
        }
        return counts;
    }

    public Map<String, Integer> falsePositiveCounts() {
        Map<String, Integer> counts = new java.util.TreeMap<>();
        for (Finding finding : filteredFindings) {
            increment(counts, finding.decisionReason);
        }
        return counts;
    }

    public int blockingFindingsCount() {
        int count = 0;
        for (Finding finding : findings) {
            if (finding.blocking
                    && finding.severity.rank() >= Severity.HIGH.rank()
                    && ("confirmed".equals(finding.decision) || "probable".equals(finding.decision))) {
                count++;
            }
        }
        return count;
    }

    public int manualReviewFindingsCount() {
        int count = 0;
        for (Finding finding : findings) {
            if ("needs_review".equals(finding.decision)
                    || (finding.manualReview && !"low_risk_notice".equals(finding.decision))) {
                count++;
            }
        }
        return count;
    }

    public String admissionDecision() {
        if (blockingFindingsCount() > 0) {
            return "BLOCKED";
        }
        if (manualReviewFindingsCount() > 0) {
            return "NEEDS_REVIEW";
        }
        if (!findings.isEmpty()) {
            return "PASS_WITH_WARNINGS";
        }
        return "PASS";
    }

    public String admissionReason() {
        String decision = admissionDecision();
        if ("BLOCKED".equals(decision)) {
            return "存在 confirmed/probable 且 blocking=true 的高危或严重问题。";
        }
        if ("NEEDS_REVIEW".equals(decision)) {
            return "存在需要人工复核的问题。";
        }
        if ("PASS_WITH_WARNINGS".equals(decision)) {
            return "仅存在低风险治理提醒或非阻断问题。";
        }
        return "未发现最终保留问题。";
    }

    public void calculateScores(boolean hasSkillMd, String skillMdContent) {
        int riskPenalty = 0;
        Severity highest = Severity.INFO;
        for (Finding finding : findings) {
            riskPenalty += Math.round(finding.severity.weight() * 3 * (float) finding.confidence);
            if (finding.severity.rank() > highest.rank()) {
                highest = finding.severity;
            }
        }
        this.riskLevel = highest;
        this.safetyScore = clamp(100 - riskPenalty, 0, 100);

        int structure = 30;
        if (hasSkillMd) {
            structure += 30;
        }
        if (skillMdContent != null && skillMdContent.toLowerCase().contains("description:")) {
            structure += 15;
        }
        if (skillMdContent != null && skillMdContent.length() >= 400) {
            structure += 15;
        }
        if (skillMdContent != null && (skillMdContent.contains("##") || skillMdContent.contains("# "))) {
            structure += 10;
        }
        this.structureScore = clamp(structure, 0, 100);

        String text = skillMdContent == null ? "" : skillMdContent.toLowerCase();
        int testFitness = 35;
        if (containsAny(text, "test", "测试", "验证", "check", "verify", "pytest", "junit", "playwright")) {
            testFitness += 25;
        }
        if (containsAny(text, "expected", "预期", "assert", "断言", "report", "coverage")) {
            testFitness += 20;
        }
        if (containsAny(text, "rollback", "回滚", "cleanup", "清理", "sandbox", "隔离")) {
            testFitness += 20;
        }
        this.testFitnessScore = clamp(testFitness, 0, 100);

        this.totalScore = Math.round((structureScore * 0.25f) + (safetyScore * 0.55f) + (testFitnessScore * 0.20f));
    }

    private static boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static void increment(Map<String, Integer> counts, String key) {
        String normalized = key == null || key.trim().isEmpty() ? "unknown" : key;
        counts.put(normalized, counts.containsKey(normalized) ? counts.get(normalized) + 1 : 1);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
