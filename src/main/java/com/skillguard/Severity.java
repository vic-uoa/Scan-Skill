package com.skillguard;

public enum Severity {
    CRITICAL(4, 10),
    HIGH(3, 7),
    MEDIUM(2, 4),
    LOW(1, 1),
    INFO(0, 0);

    private final int rank;
    private final int weight;

    Severity(int rank, int weight) {
        this.rank = rank;
        this.weight = weight;
    }

    public int rank() {
        return rank;
    }

    public int weight() {
        return weight;
    }

    public static Severity parse(String value) {
        if (value == null || value.trim().isEmpty()) {
            return HIGH;
        }
        String normalized = value.trim().toLowerCase();
        if ("critical".equals(normalized)) {
            return CRITICAL;
        }
        if ("high".equals(normalized)) {
            return HIGH;
        }
        if ("medium".equals(normalized) || "warning".equals(normalized)) {
            return MEDIUM;
        }
        if ("low".equals(normalized)) {
            return LOW;
        }
        if ("info".equals(normalized)) {
            return INFO;
        }
        throw new IllegalArgumentException("Unknown severity: " + value);
    }
}
