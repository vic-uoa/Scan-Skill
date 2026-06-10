package com.skillguard;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class DynamicScanResult {
    public final String scanMode;
    public final String skillName;
    public final Path target;
    public final List<DynamicTestCase> tests = new ArrayList<>();
    public final List<Finding> findings = new ArrayList<>();

    public DynamicScanResult(String scanMode, String skillName, Path target) {
        this.scanMode = scanMode;
        this.skillName = skillName;
        this.target = target;
    }

    public Severity riskLevel() {
        Severity highest = Severity.INFO;
        for (Finding finding : findings) {
            if (finding.severity.rank() > highest.rank()) {
                highest = finding.severity;
            }
        }
        return highest;
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
}
