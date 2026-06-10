package com.skillguard;

import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Pattern;

public final class Rule {
    public final String id;
    public final String category;
    public final Severity severity;
    public final Pattern pattern;
    public final String message;
    public final String recommendation;
    public final double confidence;
    public final String normSource;
    public final String evidenceType;
    public final boolean blocking;
    public final boolean manualReview;

    public Rule(String id, String category, Severity severity, String regex, String message, String recommendation, double confidence) {
        this(id, category, severity, regex, message, recommendation, confidence,
                defaultNormSource(id), "regex", severity.rank() >= Severity.HIGH.rank(), confidence < 0.8);
    }

    public Rule(String id, String category, Severity severity, String regex, String message, String recommendation,
                double confidence, String normSource, String evidenceType, boolean blocking, boolean manualReview) {
        this.id = id;
        this.category = category;
        this.severity = severity;
        this.pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        this.message = message;
        this.recommendation = recommendation;
        this.confidence = confidence;
        this.normSource = normSource == null ? "" : normSource;
        this.evidenceType = evidenceType == null ? "regex" : evidenceType;
        this.blocking = blocking;
        this.manualReview = manualReview;
    }

    public Optional<Finding> match(String skillName, Path file, int line, String text) {
        if (!pattern.matcher(text).find()) {
            return Optional.empty();
        }
        return Optional.of(new Finding(id, category, severity, skillName, file, line, message, text, recommendation,
                confidence, normSource, evidenceType, defaultReviewStatus(confidence), "static", blocking, manualReview));
    }

    private static String defaultNormSource(String id) {
        return id != null && id.startsWith("NORM-") ? "行内研发安全规范" : "SkillGuard 内置规则";
    }

    private static String defaultReviewStatus(double confidence) {
        if (confidence >= 0.86) {
            return "confirmed";
        }
        if (confidence >= 0.72) {
            return "probable";
        }
        return "needs_review";
    }
}
