package com.skillguard;

public final class ReviewDecision {
    public final boolean keep;
    public final String decision;
    public final String reason;
    public final String whyKept;

    private ReviewDecision(boolean keep, String decision, String reason, String whyKept) {
        this.keep = keep;
        this.decision = decision;
        this.reason = reason == null ? "" : reason;
        this.whyKept = whyKept == null ? "" : whyKept;
    }

    public static ReviewDecision keep(String decision, String reason, String whyKept) {
        return new ReviewDecision(true, decision, reason, whyKept);
    }

    public static ReviewDecision filter(String reason) {
        return new ReviewDecision(false, "false_positive", reason, "命中内容符合已知误报模式，未进入最终问题清单。");
    }

    public static ReviewDecision notice(String reason, String whyKept) {
        return new ReviewDecision(true, "low_risk_notice", reason, whyKept);
    }
}
