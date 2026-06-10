package com.skillguard;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class ScanSummary {
    public final Path root;
    public final List<SkillReport> reports = new ArrayList<>();

    public ScanSummary(Path root) {
        this.root = root;
    }

    public int totalFindings() {
        return reports.stream().mapToInt(r -> r.findings.size()).sum();
    }

    public int totalRawFindings() {
        return reports.stream().mapToInt(SkillReport::rawFindingsCount).sum();
    }

    public int totalFilteredFindings() {
        return reports.stream().mapToInt(SkillReport::filteredFindingsCount).sum();
    }

    public int totalFiles() {
        return reports.stream().mapToInt(r -> r.filesScanned).sum();
    }

    public int totalFilesSkipped() {
        return reports.stream().mapToInt(r -> r.filesSkipped).sum();
    }

    public int totalDirectoriesSkipped() {
        return reports.stream().mapToInt(r -> r.directoriesSkipped).sum();
    }

    public Severity riskLevel() {
        Severity highest = Severity.INFO;
        for (SkillReport report : reports) {
            if (report.riskLevel.rank() > highest.rank()) {
                highest = report.riskLevel;
            }
        }
        return highest;
    }

    public Map<Severity, Integer> counts() {
        Map<Severity, Integer> counts = new EnumMap<>(Severity.class);
        for (Severity severity : Severity.values()) {
            counts.put(severity, 0);
        }
        for (SkillReport report : reports) {
            for (Finding finding : report.findings) {
                counts.put(finding.severity, counts.get(finding.severity) + 1);
            }
        }
        return counts;
    }

    public Map<String, Integer> decisionCounts() {
        Map<String, Integer> counts = new java.util.TreeMap<>();
        for (SkillReport report : reports) {
            merge(counts, report.decisionCounts());
        }
        return counts;
    }

    public Map<String, Integer> falsePositiveCounts() {
        Map<String, Integer> counts = new java.util.TreeMap<>();
        for (SkillReport report : reports) {
            merge(counts, report.falsePositiveCounts());
        }
        return counts;
    }

    public String admissionDecision() {
        boolean needsReview = false;
        boolean warnings = false;
        for (SkillReport report : reports) {
            String decision = report.admissionDecision();
            if ("BLOCKED".equals(decision)) {
                return "BLOCKED";
            }
            if ("NEEDS_REVIEW".equals(decision)) {
                needsReview = true;
            }
            if ("PASS_WITH_WARNINGS".equals(decision)) {
                warnings = true;
            }
        }
        if (needsReview) {
            return "NEEDS_REVIEW";
        }
        return warnings ? "PASS_WITH_WARNINGS" : "PASS";
    }

    public String admissionReason() {
        String decision = admissionDecision();
        if ("BLOCKED".equals(decision)) {
            return "至少一个 Skill 存在阻断性高危或严重问题。";
        }
        if ("NEEDS_REVIEW".equals(decision)) {
            return "至少一个 Skill 存在需要人工复核的问题。";
        }
        if ("PASS_WITH_WARNINGS".equals(decision)) {
            return "未发现阻断问题，但存在低风险或治理提醒。";
        }
        return "未发现最终保留问题。";
    }

    public int blockingFindingsCount() {
        return reports.stream().mapToInt(SkillReport::blockingFindingsCount).sum();
    }

    public int manualReviewFindingsCount() {
        return reports.stream().mapToInt(SkillReport::manualReviewFindingsCount).sum();
    }

    private static void merge(Map<String, Integer> target, Map<String, Integer> source) {
        for (Map.Entry<String, Integer> entry : source.entrySet()) {
            target.put(entry.getKey(), target.containsKey(entry.getKey())
                    ? target.get(entry.getKey()) + entry.getValue()
                    : entry.getValue());
        }
    }
}
