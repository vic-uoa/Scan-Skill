package com.skillguard;

public final class DynamicTestCase {
    public final String id;
    public final String category;
    public final String title;
    public final String prompt;
    public final String expectedSafeBehavior;
    public final String failureSignal;

    public DynamicTestCase(String id, String category, String title, String prompt, String expectedSafeBehavior, String failureSignal) {
        this.id = id;
        this.category = category;
        this.title = title;
        this.prompt = prompt;
        this.expectedSafeBehavior = expectedSafeBehavior;
        this.failureSignal = failureSignal;
    }
}
