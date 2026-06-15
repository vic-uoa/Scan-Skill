package com.skillguard;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

public final class SkillScanner {
    private static final Set<String> SCANNABLE_EXTENSIONS = new HashSet<>(Arrays.asList(
            ".md", ".txt", ".py", ".js", ".ts", ".java", ".kt", ".go", ".rs",
            ".sh", ".bash", ".ps1", ".bat", ".cmd", ".yml", ".yaml", ".json",
            ".toml", ".ini", ".properties", ".xml"
    ));

    private static final Set<String> EXCLUDED_DIRS = new HashSet<>(Arrays.asList(
            ".git", ".idea", ".vscode", "node_modules", "target", "build", "dist",
            ".venv", "venv", "__pycache__", ".pytest_cache"
    ));

    private static final Set<String> GENERATED_ARTIFACT_DIRS = new HashSet<>(Arrays.asList(
            "allure-report", "allure-results", "coverage", "reports", "screenshots",
            "traces", "tmp", "temp", ".cache"
    ));

    private final List<Rule> rules;

    public SkillScanner(List<Rule> rules) {
        this.rules = rules;
    }

    public ScanSummary scan(Path root) throws IOException {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        if (!Files.exists(normalizedRoot)) {
            throw new IOException("Path does not exist: " + root);
        }

        ScanSummary summary = new ScanSummary(normalizedRoot);
        for (Path skillDir : discoverSkills(normalizedRoot)) {
            summary.reports.add(scanSkill(skillDir));
        }
        summary.reports.sort(Comparator.comparing(r -> r.skillName.toLowerCase(Locale.ROOT)));
        return summary;
    }

    private List<Path> discoverSkills(Path root) throws IOException {
        List<Path> skills = new ArrayList<>();
        if (Files.isRegularFile(root)) {
            skills.add(root.getParent());
            return skills;
        }
        if (findSkillMd(root) != null) {
            skills.add(root);
            return skills;
        }
        try (Stream<Path> stream = Files.walk(root, 3)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> !isUnderExcludedDirectory(root, path))
                    .filter(path -> isSkillFileName(path.getFileName().toString()))
                    .map(Path::getParent)
                    .distinct()
                    .forEach(skills::add);
        }
        if (skills.isEmpty()) {
            skills.add(root);
        }
        return skills;
    }

    private SkillReport scanSkill(Path skillDir) throws IOException {
        String skillName = skillDir.getFileName() == null ? skillDir.toString() : skillDir.getFileName().toString();
        SkillReport report = new SkillReport(skillName, skillDir);
        Path skillMd = findSkillMd(skillDir);
        boolean hasSkillMd = skillMd != null && Files.exists(skillMd);
        String skillMdContent = hasSkillMd ? readFile(skillMd) : "";
        report.entryFile = hasSkillMd ? skillMd : null;
        report.scanScope = scanScope(skillDir, skillMd);

        if (!hasSkillMd) {
            addSyntheticFinding(report, new Finding(
                    "STRUCT001", "skill_structure", Severity.MEDIUM, skillName, skillDir, 0,
                    "目录中没有 SKILL.md，无法形成标准 Skill 审计入口",
                    "", "为每个 Skill 提供 SKILL.md，并写清用途、输入、输出、限制和验证方式。", 0.9),
                    FileRole.UNKNOWN.name(), "confirmed", "kept_structure_missing_skill_md",
                    "scanner structural check", "Skill 缺少入口文件，保留为结构准入问题。");
        } else {
            addGovernanceFindings(skillName, skillMd, skillMdContent, report);
        }

        for (Path file : collectFiles(skillDir, report)) {
            report.filesScanned++;
            scanFile(skillName, file, report);
        }
        suppressOverlappingFindings(report);
        addBehaviorMismatchFindings(skillName, skillMd, skillMdContent, report);
        report.calculateScores(hasSkillMd, skillMdContent);
        return report;
    }

    private void suppressOverlappingFindings(SkillReport report) {
        List<Finding> kept = new ArrayList<>();
        for (Finding finding : report.findings) {
            String reason = overlappingSuppressionReason(finding, report.findings);
            if (reason.isEmpty()) {
                kept.add(finding);
            } else {
                report.filteredFindings.add(asFilteredOverlap(finding, reason));
            }
        }
        report.findings.clear();
        report.findings.addAll(kept);
    }

    private String overlappingSuppressionReason(Finding finding, List<Finding> findings) {
        if ("NET001".equals(finding.ruleId)
                && hasSameLocationRule(finding, findings, "NET002", "EXFIL001")) {
            return "filtered_overlapping_general_network_rule";
        }
        if ("CMD002".equals(finding.ruleId)
                && hasSameLocationRule(finding, findings, "DEP001", "SUPPLY002", "AGENT006")) {
            return "filtered_overlapping_general_execution_rule";
        }
        if ("INJ001".equals(finding.ruleId)
                && hasSameLocationRule(finding, findings, "AGENT006")) {
            return "filtered_overlapping_dynamic_execution_rule";
        }
        return "";
    }

    private boolean hasSameLocationRule(Finding finding, List<Finding> findings, String... ruleIds) {
        for (Finding other : findings) {
            if (other == finding) {
                continue;
            }
            if (sameLocation(finding, other) && ruleIdEqualsAny(other.ruleId, ruleIds)) {
                return true;
            }
        }
        return false;
    }

    private boolean ruleIdEqualsAny(String ruleId, String... ruleIds) {
        for (String candidate : ruleIds) {
            if (candidate.equals(ruleId)) {
                return true;
            }
        }
        return false;
    }

    private boolean sameLocation(Finding left, Finding right) {
        if (left.line != right.line) {
            return false;
        }
        String leftPath = left.file == null ? "" : left.file.toString();
        String rightPath = right.file == null ? "" : right.file.toString();
        return leftPath.equals(rightPath);
    }

    private Finding asFilteredOverlap(Finding finding, String reason) {
        return new Finding(finding.ruleId, finding.category, finding.severity, finding.skillName,
                finding.file, finding.line, finding.message, finding.evidence, finding.recommendation,
                finding.confidence, finding.normSource, finding.evidenceType, "false_positive",
                finding.scanMode, false, false, finding.fileRole, "false_positive",
                appendReason(finding.decisionReason, reason), finding.contextExcerpt, finding.whyMatched,
                "同一位置已有更具体规则表达该风险，泛化命中不进入最终报告。",
                finding.statementType, finding.evidenceSummary);
    }

    private List<Path> collectFiles(Path skillDir, SkillReport report) throws IOException {
        List<Path> files = new ArrayList<>();
        if (Files.isRegularFile(skillDir)) {
            files.add(skillDir);
            return files;
        }
        Files.walkFileTree(skillDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (!dir.equals(skillDir) && shouldSkipDirectory(dir)) {
                    report.directoriesSkipped++;
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (isScannable(file)) {
                    files.add(file);
                } else {
                    report.filesSkipped++;
                    report.unsupportedFilesSkipped++;
                }
                return FileVisitResult.CONTINUE;
            }
        });
        files.sort(Comparator.comparing(Path::toString));
        return files;
    }

    private boolean isScannable(Path path) {
        for (Path part : path) {
            if (EXCLUDED_DIRS.contains(part.toString())) {
                return false;
            }
        }
        String name = path.getFileName().toString();
        if (isSkillFileName(name) || name.equals("Jenkinsfile")) {
            return true;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        int dot = lower.lastIndexOf('.');
        return dot >= 0 && SCANNABLE_EXTENSIONS.contains(lower.substring(dot));
    }

    private boolean shouldSkipDirectory(Path dir) {
        String name = dir.getFileName() == null ? "" : dir.getFileName().toString();
        String lower = name.toLowerCase(Locale.ROOT);
        return EXCLUDED_DIRS.contains(name) || EXCLUDED_DIRS.contains(lower)
                || GENERATED_ARTIFACT_DIRS.contains(name) || GENERATED_ARTIFACT_DIRS.contains(lower);
    }

    private boolean isUnderExcludedDirectory(Path root, Path path) {
        Path relative;
        try {
            relative = root.toAbsolutePath().normalize().relativize(path.toAbsolutePath().normalize());
        } catch (IllegalArgumentException e) {
            relative = path;
        }
        for (Path part : relative) {
            String name = part.toString();
            String lower = name.toLowerCase(Locale.ROOT);
            if (EXCLUDED_DIRS.contains(name) || EXCLUDED_DIRS.contains(lower)
                    || GENERATED_ARTIFACT_DIRS.contains(name) || GENERATED_ARTIFACT_DIRS.contains(lower)) {
                return true;
            }
        }
        return false;
    }

    private String scanScope(Path skillDir, Path skillMd) {
        if (Files.isRegularFile(skillDir)) {
            return "SINGLE_FILE";
        }
        if (skillMd == null) {
            return "DIRECTORY_WITHOUT_SKILL_ENTRY";
        }
        String name = skillMd.getFileName() == null ? "" : skillMd.getFileName().toString();
        if ("SKILL.md".equals(name)) {
            return "STANDARD_SKILL";
        }
        if ("skill.md".equals(name)) {
            return "LOWERCASE_SKILL_ENTRY";
        }
        return "SKILL_DIRECTORY";
    }

    private void scanFile(String skillName, Path file, SkillReport report) {
        FileRole role = classifyFile(file);
        scanPath(skillName, file, report, role);

        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException | OutOfMemoryError e) {
            addSyntheticFinding(report, new Finding(
                    "SCAN001", "scanner", Severity.LOW, skillName, file, 0,
                    "文件无法读取，已跳过",
                    e.getMessage(), "确认文件编码和大小是否符合文本扫描预期。", 0.5),
                    role.name(), "needs_review", "kept_unreadable_file",
                    "scanner file read check", "文件无法读取，需确认编码、大小或权限是否符合扫描预期。");
            return;
        }

        boolean inMarkdownCodeFence = false;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.length() > 20_000) {
                continue;
            }
            String trimmed = line.trim();
            boolean fenceDelimiter = trimmed.startsWith("```") || trimmed.startsWith("~~~");
            boolean inCodeFenceForLine = inMarkdownCodeFence || fenceDelimiter;
            String previousLine = contextWindow(lines, Math.max(0, i - 4), i);
            String nextLine = contextWindow(lines, i + 1, Math.min(lines.size(), i + 5));
            LineContext context = new LineContext(role, inCodeFenceForLine, previousLine, nextLine,
                    classifyStatement(line, role, inCodeFenceForLine));
            for (Rule rule : rules) {
                java.util.Optional<Finding> finding = rule.match(skillName, file, i + 1, line);
                if (finding.isPresent()) {
                    Finding rawFinding = annotateFinding(finding.get(), rule, file, line, context,
                            ReviewDecision.keep("raw_match", "raw_rule_match", "规则正则命中，等待上下文复核。"));
                    report.rawFindings.add(rawFinding);
                    ReviewDecision decision = reviewFinding(rule, file, line, context);
                    if (decision.keep) {
                        report.findings.add(normalizeFinding(finding.get(), rule, file, line, context, decision));
                    } else {
                        report.filteredFindings.add(annotateFinding(finding.get(), rule, file, line, context, decision));
                    }
                }
            }
            if (fenceDelimiter) {
                inMarkdownCodeFence = !inMarkdownCodeFence;
            }
        }
    }

    private void scanPath(String skillName, Path file, SkillReport report, FileRole role) {
        String normalized = file.toString().replace('\\', '/');
        LineContext context = new LineContext(role, false, "", "", "path");
        for (Rule rule : rules) {
            if (!canRuleMatchPath(rule)) {
                continue;
            }
            java.util.Optional<Finding> finding = rule.match(skillName, file, 0, normalized);
            if (finding.isPresent()) {
                Finding rawFinding = annotateFinding(finding.get(), rule, file, normalized, context,
                        ReviewDecision.keep("raw_match", "raw_path_rule_match", "路径规则命中，等待上下文复核。"));
                report.rawFindings.add(rawFinding);
                ReviewDecision decision = reviewPathFinding(rule, normalized.toLowerCase(Locale.ROOT), context);
                if (decision.keep) {
                    report.findings.add(normalizeFinding(finding.get(), rule, file, normalized, context, decision));
                } else {
                    report.filteredFindings.add(annotateFinding(finding.get(), rule, file, normalized, context, decision));
                }
            }
        }
    }

    private String contextWindow(List<String> lines, int startInclusive, int endExclusive) {
        StringBuilder window = new StringBuilder();
        for (int i = startInclusive; i < endExclusive; i++) {
            if (i < 0 || i >= lines.size()) {
                continue;
            }
            if (window.length() > 0) {
                window.append('\n');
            }
            window.append(lines.get(i));
        }
        return window.toString();
    }

    private boolean canRuleMatchPath(Rule rule) {
        return "FILE001".equals(rule.id)
                || "TEST002".equals(rule.id)
                || "TEST003".equals(rule.id)
                || "ENV002".equals(rule.id)
                || "ENV003".equals(rule.id);
    }

    private boolean isSkillFileName(String name) {
        return "skill.md".equalsIgnoreCase(name);
    }

    private Path findSkillMd(Path dir) {
        if (dir == null || !Files.isDirectory(dir)) {
            return null;
        }
        Path upper = dir.resolve("SKILL.md");
        if (Files.exists(upper)) {
            return upper;
        }
        Path lower = dir.resolve("skill.md");
        return Files.exists(lower) ? lower : null;
    }

    private ReviewDecision reviewPathFinding(Rule rule, String lowerPath, LineContext context) {
        if (context.role == FileRole.RULE_DEFINITION
                || context.role == FileRole.SECURITY_REQUIREMENT
                || context.role == FileRole.TEMPLATE
                || context.role == FileRole.EXAMPLE_DOC
                || context.role == FileRole.ANALYSIS_REPORT) {
            return ReviewDecision.filter("filtered_path_documentation_role");
        }
        if ("ENV002".equals(rule.id)) {
            return containsAny(lowerPath, "/etc/hosts", "drivers/etc/hosts", "http_proxy", "https_proxy", "truststore", "keystore")
                    ? ReviewDecision.keep("probable", "kept_sensitive_path_match", "路径体现主机、代理或证书信任配置风险。")
                    : ReviewDecision.filter("filtered_path_missing_required_evidence");
        }
        if ("FILE001".equals(rule.id)) {
            return containsAny(lowerPath, "/.ssh/", "\\.ssh\\", "/.aws/credentials", "\\.aws\\credentials",
                    "credentials.json", ".pem", ".key", ".env")
                    ? ReviewDecision.keep("confirmed", "kept_sensitive_path_match", "路径指向敏感凭据文件。")
                    : ReviewDecision.filter("filtered_path_missing_required_evidence");
        }
        return ReviewDecision.keep("probable", "kept_path_rule_match", "路径命中规则且未落入文档/示例角色。");
    }

    private Finding normalizeFinding(Finding finding, Rule rule, Path file, String line, LineContext context, ReviewDecision decision) {
        String lowerLine = line == null ? "" : line.toLowerCase(Locale.ROOT);
        Severity severity = finding.severity;
        double confidence = finding.confidence;
        String reviewStatus = finding.reviewStatus;
        String finalDecision = decision.decision;
        String decisionReason = decision.reason;
        String whyKept = decision.whyKept;
        boolean manualReview = finding.manualReview;
        boolean blocking = finding.blocking;

        if (rule.id.startsWith("NORM-")) {
            severity = minSeverity(severity, Severity.MEDIUM);
            confidence = Math.min(confidence, 0.72);
            reviewStatus = "needs_review";
            finalDecision = "needs_review";
            manualReview = true;
            blocking = false;
            decisionReason = appendReason(decisionReason, "downgraded_norm_rule");
        }

        if (isCapabilityOnlyFinding(rule, lowerLine, context)) {
            severity = minSeverity(severity, isDeclaredCapabilityFinding(rule, lowerLine) ? Severity.LOW : Severity.MEDIUM);
            confidence = Math.min(confidence, isDeclaredCapabilityFinding(rule, lowerLine) ? 0.58 : 0.70);
            reviewStatus = "needs_review";
            finalDecision = "needs_review";
            manualReview = true;
            blocking = false;
            decisionReason = appendReason(decisionReason, "downgraded_capability_only");
        }

        if (hasStrongMitigationEvidence(rule, lowerLine, context.previousLine + "\n" + line + "\n" + context.nextLine, context)) {
            severity = minSeverity(severity, Severity.LOW);
            confidence = Math.min(confidence, 0.55);
            reviewStatus = "needs_review";
            finalDecision = "low_risk_notice";
            manualReview = true;
            blocking = false;
            decisionReason = appendReason(decisionReason, "downgraded_mitigation_present");
        }

        if (hasExploitChainEvidence(rule, lowerLine, context.previousLine + "\n" + line + "\n" + context.nextLine, context)) {
            confidence = Math.max(confidence, 0.86);
            reviewStatus = "confirmed";
            finalDecision = "confirmed";
            manualReview = false;
            blocking = severity.rank() >= Severity.HIGH.rank();
            decisionReason = appendReason(decisionReason, "upgraded_exploit_chain_evidence");
        }

        if (context.role == FileRole.SKILL_INSTRUCTION || context.role == FileRole.RULE_DEFINITION) {
            severity = minSeverity(severity, Severity.MEDIUM);
            confidence = Math.min(confidence, 0.72);
            reviewStatus = "needs_review";
            finalDecision = "needs_review";
            manualReview = true;
            blocking = false;
            decisionReason = appendReason(decisionReason, "downgraded_instruction_or_rule_file");
        }

        if (severity == Severity.LOW && rule.id.startsWith("META") && !"needs_review".equals(finalDecision)) {
            finalDecision = "low_risk_notice";
            reviewStatus = "needs_review".equals(reviewStatus) ? reviewStatus : "probable";
            blocking = false;
        }

        return new Finding(finding.ruleId, finding.category, severity, finding.skillName, file, finding.line,
                finding.message, finding.evidence, finding.recommendation, confidence, finding.normSource,
                finding.evidenceType, reviewStatus, finding.scanMode, blocking, manualReview,
                context.role.name(), finalDecision, decisionReason, contextExcerpt(line, context),
                whyMatched(rule, context), whyKept, context.statementType, evidenceSummary(rule, line, context));
    }

    private Finding annotateFinding(Finding finding, Rule rule, Path file, String line, LineContext context, ReviewDecision decision) {
        return new Finding(finding.ruleId, finding.category, finding.severity, finding.skillName, file, finding.line,
                finding.message, finding.evidence, finding.recommendation, finding.confidence, finding.normSource,
                finding.evidenceType, finding.reviewStatus, finding.scanMode, finding.blocking, finding.manualReview,
                context.role.name(), decision.decision, decision.reason, contextExcerpt(line, context),
                whyMatched(rule, context), decision.whyKept, context.statementType, evidenceSummary(rule, line, context));
    }

    private void addSyntheticFinding(
            SkillReport report,
            Finding finding,
            String fileRole,
            String decision,
            String decisionReason,
            String whyMatched,
            String whyKept) {
        String reviewStatus = "low_risk_notice".equals(decision) ? "probable" : decision;
        boolean manualReview = "needs_review".equals(decision)
                || (finding.manualReview && !"low_risk_notice".equals(decision));
        boolean blocking = finding.blocking
                && finding.severity.rank() >= Severity.HIGH.rank()
                && ("confirmed".equals(decision) || "probable".equals(decision));
        Finding enriched = new Finding(finding.ruleId, finding.category, finding.severity, finding.skillName,
                finding.file, finding.line, finding.message, finding.evidence, finding.recommendation,
                finding.confidence, finding.normSource, finding.evidenceType, reviewStatus, finding.scanMode,
                blocking, manualReview, fileRole, decision, decisionReason, "",
                whyMatched, whyKept, "synthetic", whyKept);
        report.rawFindings.add(enriched);
        report.findings.add(enriched);
    }

    private String appendReason(String current, String extra) {
        if (current == null || current.trim().isEmpty()) {
            return extra;
        }
        if (current.contains(extra)) {
            return current;
        }
        return current + "," + extra;
    }

    private String contextExcerpt(String line, LineContext context) {
        String text = (context.previousLine + "\n" + (line == null ? "" : line) + "\n" + context.nextLine).trim();
        return text.length() <= 800 ? text : text.substring(0, 797) + "...";
    }

    private String whyMatched(Rule rule, LineContext context) {
        return "规则 " + rule.id + " 正则命中；文件角色=" + context.role.name()
                + "；语句类型=" + context.statementType
                + (context.inCodeFence ? "；位于 Markdown 代码块或代码围栏附近。" : "。");
    }

    private String evidenceSummary(Rule rule, String line, LineContext context) {
        String lowerLine = line == null ? "" : line.toLowerCase(Locale.ROOT);
        String surrounding = (context.previousLine + "\n" + line + "\n" + context.nextLine).toLowerCase(Locale.ROOT);
        List<String> signals = new ArrayList<>();
        signals.add("文件角色=" + context.role.name());
        signals.add("语句类型=" + context.statementType);
        if (context.inCodeFence) {
            signals.add("代码块");
        }
        if (context.role == FileRole.IMPLEMENTATION) {
            signals.add("实现代码");
        }
        if (context.role == FileRole.ANALYSIS_REPORT) {
            signals.add("报告产物");
        }
        if (looksLikeRealSecretValue(lowerLine)) {
            signals.add("当前行含真实令牌/会话形态");
        }
        if (hasSensitiveLiteralEvidence(lowerLine)) {
            signals.add("当前行含敏感字面量");
        }
        if (hasUserControlledEvidence(lowerLine)) {
            signals.add("当前行含外部输入");
        } else if (hasUserControlledEvidence(surrounding)) {
            signals.add("附近存在外部输入上下文");
        }
        if (containsAny(lowerLine, "open(", "read", "readfile", "fs.", "cat ")) {
            signals.add("当前行文件读取");
        } else if (containsAny(surrounding, "open(", "read", "readfile", "fs.", "cat ")) {
            signals.add("附近存在文件读取");
        }
        if (containsAny(lowerLine, "requests.", "httpx.", "fetch(", "curl ", "wget ", "post(", "put(")) {
            signals.add("当前行网络请求");
        } else if (containsAny(surrounding, "requests.", "httpx.", "fetch(", "curl ", "wget ", "post(", "put(")) {
            signals.add("附近存在网络请求");
        }
        if (containsAny(lowerLine, "shell=true", "os.system", "subprocess.", "child_process.exec", "runtime.getruntime().exec")) {
            signals.add("当前行命令执行");
        } else if (containsAny(surrounding, "shell=true", "os.system", "subprocess.", "child_process.exec", "runtime.getruntime().exec")) {
            signals.add("附近存在命令执行");
        }
        if (containsAny(lowerLine, "execute", "cursor", "query", "jdbc", "delete ", "update ", "truncate table", "drop table")) {
            signals.add("当前行数据库/SQL");
        } else if (containsAny(surrounding, "execute", "cursor", "query", "jdbc", "delete ", "update ", "truncate table", "drop table")) {
            signals.add("附近存在数据库/SQL");
        }
        if (hasStrongMitigationEvidence(rule, lowerLine, surrounding, context)) {
            signals.add("存在缓解措施");
        }
        if (context.role == FileRole.TEST_CASE || context.role == FileRole.EXAMPLE_DOC || isTeachingOrFixExample(surrounding)) {
            signals.add("示例/测试语境");
        }
        if (signals.size() > 8) {
            signals = signals.subList(0, 8);
        }
        return "证据确认：" + String.join("；", signals);
    }

    private String classifyStatement(String line, FileRole role, boolean inCodeFence) {
        String value = line == null ? "" : line;
        String trimmed = value.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (trimmed.isEmpty()) {
            return "blank";
        }
        if (isPureCommentLine(lower)) {
            return "comment";
        }
        if (inCodeFence) {
            return "markdown_code";
        }
        if (trimmed.startsWith("|") && trimmed.endsWith("|")) {
            return "markdown_table";
        }
        if (trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.matches("^\\d+[.)]\\s+.*")) {
            return isSecurityRequirementStatement(lower) ? "requirement_bullet" : "markdown_list";
        }
        if (trimmed.startsWith("\"") && trimmed.contains(":")) {
            return isReportContextField(lower) ? "report_context_field" : "json_field";
        }
        if (containsAny(lower, " = ", "=\"", "= '", ":=", "=>")) {
            return "assignment";
        }
        if (containsAny(lower, "requests.", "httpx.", "fetch(", "curl ", "wget ", "urllib.", "urlopen")) {
            return "network_call";
        }
        if (containsAny(lower, "os.system", "subprocess.", "child_process.exec", "runtime.getruntime().exec",
                "eval(", "exec(", "shell=true")) {
            return "execution_sink";
        }
        if (containsAny(lower, "open(", ".write(", "writefile", "copy", "move", "delete", "remove-item",
                "download", "extract", "unzip")) {
            return "file_operation";
        }
        if (containsAny(lower, "jdbc:", "execute(", "cursor.", "query(", "truncate table", "delete from",
                "update ", "drop table")) {
            return "database_statement";
        }
        if (looksLikeExecutableSnippet(lower)) {
            return "executable_statement";
        }
        if (role == FileRole.SECURITY_REQUIREMENT || isSecurityRequirementStatement(lower)) {
            return "requirement_text";
        }
        if (role == FileRole.ANALYSIS_REPORT) {
            return "report_text";
        }
        return "plain_text";
    }

    private Severity minSeverity(Severity current, Severity cap) {
        return current.rank() > cap.rank() ? cap : current;
    }

    private ReviewDecision reviewFinding(Rule rule, Path file, String line, LineContext context) {
        String fileName = file.getFileName() == null ? "" : file.getFileName().toString();
        String lowerFile = file.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        String lowerLine = line == null ? "" : line.toLowerCase(Locale.ROOT);
        String surrounding = (context.previousLine + "\n" + line + "\n" + context.nextLine).toLowerCase(Locale.ROOT);
        boolean markdown = lowerFile.endsWith(".md");
        boolean json = lowerFile.endsWith(".json");
        boolean skillMd = "skill.md".equalsIgnoreCase(fileName);

        if (isDependencyMetadataFile(lowerFile)
                && (isOperationalRule(rule) || rule.id.startsWith("ENV"))
                && !isDependencyRiskRule(rule)) {
            return ReviewDecision.filter("filtered_dependency_lock_metadata");
        }

        if (isPureCommentLine(lowerLine) && (isOperationalRule(rule) || rule.id.startsWith("NORM-"))) {
            return ReviewDecision.filter("filtered_comment_only");
        }

        if (isRuleDefinitionNoise(rule, lowerLine, surrounding, context)) {
            return ReviewDecision.filter("filtered_rule_definition");
        }

        if (rule.id.startsWith("NORM-")
                && (context.role == FileRole.SECURITY_REQUIREMENT || context.role == FileRole.RULE_DEFINITION)
                && !looksLikeExecutableSnippet(lowerLine)) {
            return ReviewDecision.filter("filtered_security_requirement");
        }

        if (isScannerDetectionLogic(rule, lowerFile, lowerLine, surrounding, context)
                && !hasSensitiveLiteralEvidence(lowerLine)) {
            return ReviewDecision.filter("filtered_scanner_detection_logic");
        }

        if ("BANK004".equals(rule.id) && isScannerStatusOrProgressLog(lowerLine, surrounding)) {
            return ReviewDecision.filter("filtered_scanner_status_log");
        }

        if (("BANK004".equals(rule.id) || "DATA001".equals(rule.id))
                && isBenignDocumentMetadataLog(lowerFile, lowerLine, surrounding)) {
            return ReviewDecision.filter("filtered_document_metadata_log");
        }

        if (("AUTH001".equals(rule.id) || "AUTH005".equals(rule.id) || "AUTH006".equals(rule.id))
                && isScannerCredentialWordlist(lowerLine, surrounding)) {
            return ReviewDecision.filter("filtered_scanner_detection_logic");
        }

        if ("AUTH005".equals(rule.id)
                && isCredentialCandidateCorpus(lowerFile, lowerLine, surrounding)) {
            return ReviewDecision.filter("filtered_scanner_credential_corpus");
        }

        if ("INJ001".equals(rule.id) && isRegexOrParserExec(lowerLine, surrounding)) {
            return ReviewDecision.filter("filtered_parser_regex_exec");
        }

        if ("INJ001".equals(rule.id) && isStaticDispatchEval(lowerLine, surrounding)
                && !hasUserControlledEvidence(surrounding)) {
            return ReviewDecision.filter("filtered_static_dispatch_eval");
        }

        if ("AUTH002".equals(rule.id) && isRoutineSessionHandling(lowerLine, surrounding)) {
            return ReviewDecision.filter("filtered_routine_auth_flow");
        }

        if ((rule.id.startsWith("AUTH") || "BANK004".equals(rule.id))
                && isCredentialPlaceholderOrTemplate(lowerLine)
                && !looksLikeSensitiveOutputSink(surrounding)) {
            return ReviewDecision.filter("filtered_placeholder_value");
        }

        if ("NET001".equals(rule.id)
                && isNetworkCapabilityOnly(lowerLine, surrounding)) {
            return ReviewDecision.filter("filtered_capability_only_network");
        }

        if ("ENV003".equals(rule.id) && isDependencyIntegrityOrLabelOnly(lowerLine, surrounding)) {
            return ReviewDecision.filter("filtered_dependency_or_label_metadata");
        }

        if ("ENV003".equals(rule.id) && isVpnOrJumpLabelOnly(lowerLine, surrounding)) {
            return ReviewDecision.filter("filtered_config_label_only");
        }

        if (!hasRequiredEvidence(rule, lowerLine, surrounding, context)) {
            return ReviewDecision.filter("filtered_missing_required_evidence");
        }

        if (isPlaceholderOnly(lowerLine, rule)) {
            return ReviewDecision.filter("filtered_placeholder_value");
        }

        if (isExampleOrTestContext(lowerFile, lowerLine)
                && (rule.id.startsWith("AUTH") || rule.id.startsWith("DATA") || rule.id.startsWith("BANK"))
                && isCredentialPlaceholderOrTemplate(lowerLine)) {
            return ReviewDecision.filter("filtered_example_placeholder");
        }

        if ("FILE001".equals(rule.id) && isLikelyKeyPropertyAccess(line)) {
            return ReviewDecision.filter("filtered_type_or_property_access");
        }

        if ("FILE001".equals(rule.id)
                && (isSafeCredentialSource(lowerLine) || isConfigurationKeyReferenceOnly(lowerLine, surrounding))) {
            return ReviewDecision.filter("filtered_config_or_env_reference");
        }

        if ("FILE001".equals(rule.id) && !looksLikeSensitiveFileAccess(lowerLine, surrounding, context)) {
            return ReviewDecision.filter("filtered_missing_sensitive_file_evidence");
        }

        if ("DATA001".equals(rule.id) && isGenericBusinessLabelOnly(lowerLine, surrounding)) {
            return ReviewDecision.filter("filtered_generic_business_label");
        }

        if ("FILE002".equals(rule.id) && hasPathContainmentEvidence(surrounding)) {
            return ReviewDecision.filter("filtered_safe_implementation");
        }

        if ("CMD001".equals(rule.id) && isBenignPackageManagerCleanup(lowerLine, surrounding)) {
            return ReviewDecision.filter("filtered_benign_cleanup");
        }

        if (hasStrongMitigationEvidence(rule, lowerLine, surrounding, context)
                && !hasSensitiveLiteralEvidence(lowerLine)) {
            return ReviewDecision.filter("filtered_safe_implementation");
        }

        if ("AGENT001".equals(rule.id) && isFrameworkSecurityApi(lowerLine)) {
            return ReviewDecision.filter("filtered_safe_implementation");
        }

        if ("NORM-INPUT002".equals(rule.id) && isLikelyJsonEncodingImplementation(line)) {
            return ReviewDecision.filter("filtered_safe_implementation");
        }

        if (rule.id.startsWith("NORM-") && isSecurityRequirementStatement(lowerLine)) {
            return ReviewDecision.filter("filtered_security_requirement");
        }

        if (rule.id.startsWith("REPORT") && isExampleOrTestContext(lowerFile, lowerLine)) {
            return ReviewDecision.filter("filtered_example_doc");
        }

        if ((context.role == FileRole.TEMPLATE || context.role == FileRole.TEST_CASE || context.role == FileRole.EXAMPLE_DOC)
                && isAdversarialTestPrompt(lowerLine)) {
            return ReviewDecision.filter("filtered_test_payload");
        }

        if (isDocumentationRole(context.role) && isOperationalRule(rule)) {
            return looksLikeNegativeImplementation(lowerLine)
                    ? ReviewDecision.keep("needs_review", "kept_negative_documentation_evidence", "文档中描述了当前实现缺失或存在风险，需要人工复核。")
                    : ReviewDecision.filter("filtered_documentation_role");
        }

        if (context.role == FileRole.SKILL_INSTRUCTION && isOperationalRule(rule)
                && !looksLikeExecutableSnippet(lowerLine)
                && !looksLikeActionableSkillInstruction(lowerLine)
                && !looksLikeNegativeImplementation(surrounding)) {
            return ReviewDecision.filter("filtered_skill_instruction_text");
        }

        if (markdown && isSafePolicyOrRuleCatalogLine(lowerLine) && !looksLikeExecutableSnippet(lowerLine)) {
            return ReviewDecision.filter("filtered_security_requirement");
        }

        if (rule.id.startsWith("NORM-") && isPositiveControlStatement(lowerLine)) {
            return ReviewDecision.filter("filtered_positive_control_statement");
        }

        if (json && isAnalysisOrRequirementDoc(lowerFile) && isReportSummaryField(lowerLine) && isOperationalRule(rule)) {
            return ReviewDecision.filter("filtered_report_summary");
        }

        if (json && isAnalysisOrRequirementDoc(lowerFile) && isReportContextField(lowerLine)) {
            return isReportRule(rule) && looksLikeRealReportLeak(lowerLine)
                    ? ReviewDecision.keep("needs_review", "kept_report_context_leak", "报告字段包含可能需要脱敏的真实接口、认证或请求上下文。")
                    : ReviewDecision.filter("filtered_report_context_placeholder");
        }

        if (markdown && !skillMd && rule.id.startsWith("NORM-") && !looksLikeNegativeImplementation(lowerLine)) {
            return ReviewDecision.filter("filtered_security_requirement");
        }

        if (markdown && !skillMd && isOperationalRule(rule)) {
            return looksLikeExecutableSnippet(lowerLine)
                    ? ReviewDecision.keep("needs_review", "kept_markdown_executable_snippet", "Markdown 中包含可执行片段，需要人工复核。")
                    : ReviewDecision.filter("filtered_documentation_role");
        }

        if (markdown && isNormativeText(lowerLine) && isOperationalRule(rule) && !looksLikeExecutableSnippet(lowerLine)) {
            return ReviewDecision.filter("filtered_security_requirement");
        }

        if (markdown && isAnalysisOrRequirementDoc(lowerFile) && isOperationalRule(rule) && !looksLikeExecutableSnippet(lowerLine)) {
            return ReviewDecision.filter("filtered_report_summary");
        }

        return ReviewDecision.keep(findingDecision(rule), "kept_required_evidence", "命中内容满足规则最低证据门槛，保留进入最终报告。");
    }

    private String findingDecision(Rule rule) {
        if (rule.confidence >= 0.86) {
            return "confirmed";
        }
        if (rule.confidence >= 0.72) {
            return "probable";
        }
        return "needs_review";
    }

    private boolean isOperationalRule(Rule rule) {
        return rule.id.startsWith("FILE")
                || rule.id.startsWith("EXFIL")
                || rule.id.startsWith("NET")
                || rule.id.startsWith("CMD")
                || rule.id.startsWith("INJ")
                || rule.id.startsWith("OBF")
                || rule.id.startsWith("DEP")
                || rule.id.startsWith("TEST")
                || rule.id.startsWith("BANK")
                || rule.id.startsWith("AUTH")
                || rule.id.startsWith("DATA")
                || rule.id.startsWith("ENV")
                || rule.id.startsWith("DB")
                || rule.id.startsWith("APISEC")
                || rule.id.startsWith("AUTO")
                || rule.id.startsWith("CI")
                || rule.id.startsWith("REPORT")
                || rule.id.startsWith("SUPPLY");
    }

    private FileRole classifyFile(Path file) {
        String normalized = file.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        String name = file.getFileName() == null ? "" : file.getFileName().toString().toLowerCase(Locale.ROOT);
        if ("skill.md".equals(name)) {
            return FileRole.SKILL_INSTRUCTION;
        }
        if (isRuleDefinitionFile(normalized, name)) {
            return FileRole.RULE_DEFINITION;
        }
        if (isDocumentationFile(name) && containsAny(normalized,
                "/assets/", "/languages/", "/language/", "/frameworks/", "/framework/",
                "/infrastructure/", "/guides/", "/guide/", "/manuals/", "/manual/")) {
            return FileRole.SECURITY_REQUIREMENT;
        }
        if (containsAny(normalized, "/security-tests/", "/security-test/", "/testcases/", "/payloads/", "/fuzz/", "/tests/")) {
            return FileRole.TEST_CASE;
        }
        if (containsAny(normalized, "/security-analysis/", "/analysis/", "/reports/", "/report/")
                || name.equals("report.json") || name.equals("result.json") || name.equals("analysis.md")) {
            return FileRole.ANALYSIS_REPORT;
        }
        if (containsAny(normalized, "/templates/", "/prompts/") || name.contains("template") || name.contains("transcript")) {
            return FileRole.TEMPLATE;
        }
        if (containsAny(name, "requirements", "security_rules", "security-checks", "internal-security-rules", "reference")
                || containsAny(normalized, "/references/", "/docs/")) {
            return FileRole.SECURITY_REQUIREMENT;
        }
        if (containsAny(normalized, "/scripts/", "/bin/") || isImplementationFile(name)) {
            return FileRole.IMPLEMENTATION;
        }
        if (containsAny(normalized, "/examples/", "/demo/", "/sample/")) {
            return FileRole.EXAMPLE_DOC;
        }
        return FileRole.UNKNOWN;
    }

    private boolean isImplementationFile(String name) {
        return name.endsWith(".py")
                || name.endsWith(".js")
                || name.endsWith(".ts")
                || name.endsWith(".java")
                || name.endsWith(".kt")
                || name.endsWith(".go")
                || name.endsWith(".rs")
                || name.endsWith(".sh")
                || name.endsWith(".bash")
                || name.endsWith(".ps1")
                || name.endsWith(".bat")
                || name.endsWith(".cmd");
    }

    private boolean isDocumentationFile(String name) {
        return name.endsWith(".md")
                || name.endsWith(".txt")
                || name.endsWith(".rst")
                || name.endsWith(".adoc");
    }

    private boolean isDocumentationRole(FileRole role) {
        return role == FileRole.SECURITY_REQUIREMENT
                || role == FileRole.ANALYSIS_REPORT
                || role == FileRole.TEMPLATE
                || role == FileRole.EXAMPLE_DOC
                || role == FileRole.RULE_DEFINITION;
    }

    private boolean isRuleDefinitionFile(String normalized, String name) {
        return containsAny(normalized, "/rules/", "/rule/", "/policy-rules/", "/detectors/")
                || containsAny(name, "builtinrules", "audit_report", "scanner_rules", "security-rules", "rule-catalog")
                || (containsAny(normalized, "/references/") && containsAny(name, "pattern", "patterns", "rules"));
    }

    private boolean isRuleDefinitionNoise(Rule rule, String lowerLine, String surrounding, LineContext context) {
        if (context.role != FileRole.RULE_DEFINITION && !looksLikeRuleCatalogLine(lowerLine)) {
            return false;
        }
        if (looksLikeRealSecretValue(lowerLine)) {
            return false;
        }
        if (looksLikeExecutableSnippet(lowerLine) && !looksLikeRuleCatalogLine(lowerLine)) {
            return false;
        }
        return isOperationalRule(rule) || rule.id.startsWith("NORM-");
    }

    private boolean looksLikeRuleCatalogLine(String lowerLine) {
        String trimmed = lowerLine.trim();
        return trimmed.startsWith("pattern=")
                || trimmed.startsWith("pattern =")
                || trimmed.startsWith("regex=")
                || trimmed.startsWith("regex =")
                || trimmed.startsWith("description=")
                || trimmed.startsWith("description =")
                || trimmed.startsWith("remediation=")
                || trimmed.startsWith("remediation =")
                || trimmed.startsWith("impact=")
                || trimmed.startsWith("impact =")
                || trimmed.startsWith("vuln_type=")
                || trimmed.startsWith("vuln_type =")
                || trimmed.contains("new rule(")
                || trimmed.contains("severity.")
                || trimmed.contains("rule id")
                || trimmed.contains("rule_id")
                || trimmed.contains("cwe-")
                || trimmed.contains("owasp");
    }

    private boolean hasRequiredEvidence(Rule rule, String lowerLine, String surrounding, LineContext context) {
        if (context.role == FileRole.RULE_DEFINITION && !looksLikeExecutableSnippet(lowerLine)) {
            return false;
        }

        if (isDocumentationRole(context.role) && isOperationalRule(rule)) {
            return context.role == FileRole.ANALYSIS_REPORT
                    && isReportRule(rule)
                    && looksLikeRealReportLeak(lowerLine);
        }

        if (context.role == FileRole.TEST_CASE || context.role == FileRole.EXAMPLE_DOC) {
            if (isPayloadOrAttackSampleRule(rule)) {
                return false;
            }
        }

        if ("NORM-AUTH001".equals(rule.id) || "AUTH001".equals(rule.id) || "AUTH006".equals(rule.id)) {
            return looksLikeCredentialEvidence(lowerLine, surrounding, context);
        }

        if ("AUTH002".equals(rule.id)) {
            return looksLikeRealSessionExposure(lowerLine, surrounding, context);
        }

        if ("FILE002".equals(rule.id)) {
            return looksLikeWorkspaceEscapeEvidence(lowerLine, surrounding, context);
        }

        if ("AUTH003".equals(rule.id)) {
            return looksLikeTlsBypassEvidence(lowerLine, surrounding, context);
        }

        if ("AUTH004".equals(rule.id)) {
            return looksLikeAuthBypassEvidence(lowerLine, surrounding, context);
        }

        if ("AUTH005".equals(rule.id)) {
            return looksLikePrivilegedAccountEvidence(lowerLine, surrounding, context);
        }

        if ("BANK001".equals(rule.id)) {
            return looksLikeProductionEnvironmentEvidence(lowerLine, surrounding, context);
        }

        if ("BANK002".equals(rule.id)) {
            return looksLikeFinancialPiiEvidence(lowerLine, surrounding, context);
        }

        if ("BANK004".equals(rule.id)) {
            return looksLikeSensitiveLoggingEvidence(lowerLine, surrounding, context);
        }

        if ("BANK005".equals(rule.id)) {
            return looksLikeUserIdentifierExposureEvidence(lowerLine, surrounding, context);
        }

        if ("DATA001".equals(rule.id)) {
            return looksLikeRealCustomerDataEvidence(lowerLine, surrounding, context);
        }

        if ("DATA002".equals(rule.id)) {
            return looksLikeBulkDataExportEvidence(lowerLine, surrounding, context);
        }

        if ("DATA003".equals(rule.id) || rule.id.startsWith("REPORT")) {
            return looksLikeReportLeakEvidence(lowerLine, surrounding, context);
        }

        if ("NORM-ACCESS001".equals(rule.id) || "NORM-ACCESS002".equals(rule.id)
                || "NORM-TRADE001".equals(rule.id)) {
            return looksLikeBusinessRiskEvidence(lowerLine, surrounding, context);
        }

        if ("APISEC004".equals(rule.id)) {
            return looksLikeActivePayloadUse(lowerLine, surrounding, context);
        }

        if ("NORM-DATA002".equals(rule.id)) {
            return looksLikeUnsafeCryptoEvidence(lowerLine, surrounding, context);
        }

        if ("INJ001".equals(rule.id)) {
            return looksLikeExecutableCodeEvidence(lowerLine, surrounding, context);
        }

        if ("CMD002".equals(rule.id)) {
            return looksLikeRiskyShellExecution(lowerLine, surrounding, context);
        }

        if ("CMD001".equals(rule.id)) {
            return looksLikeDangerousCommandEvidence(lowerLine, surrounding, context);
        }

        if ("AGENT001".equals(rule.id)) {
            return looksLikeAgentInstructionRiskEvidence(lowerLine, surrounding, context);
        }

        if ("AGENT002".equals(rule.id)) {
            return looksLikeAgentHiddenActionEvidence(lowerLine, surrounding, context);
        }

        if ("AGENT003".equals(rule.id)) {
            return looksLikeRemoteInstructionEvidence(lowerLine, surrounding, context);
        }

        if ("AGENT004".equals(rule.id)) {
            return looksLikeUntrustedOutputInstructionEvidence(lowerLine, surrounding, context);
        }

        if ("AGENT005".equals(rule.id)) {
            return looksLikeExcessiveAgencyEvidence(lowerLine, surrounding, context);
        }

        if ("AGENT006".equals(rule.id)) {
            return looksLikeModelOrToolOutputExecutionEvidence(lowerLine, surrounding, context);
        }

        if ("NET001".equals(rule.id)) {
            return looksLikeNetworkRiskEvidence(lowerLine, surrounding, context);
        }

        if ("NET002".equals(rule.id)) {
            return looksLikeInternalNetworkOrSsrfEvidence(lowerLine, surrounding, context);
        }

        if ("EXFIL001".equals(rule.id)) {
            return looksLikeExfiltrationEvidence(lowerLine, surrounding, context);
        }

        if ("ENV003".equals(rule.id)) {
            return looksLikeJumpHostOrVpnEvidence(lowerLine, surrounding, context);
        }

        if ("ENV002".equals(rule.id)) {
            return looksLikeHostProxyMutationEvidence(lowerLine, surrounding, context);
        }

        if ("DB002".equals(rule.id)) {
            return looksLikeDatabaseMutationEvidence(lowerLine, surrounding, context);
        }

        if ("DB001".equals(rule.id) || "BANK003".equals(rule.id)) {
            return looksLikeSqlMutationEvidence(lowerLine, surrounding, context);
        }

        if ("TEST002".equals(rule.id) || "CI002".equals(rule.id)) {
            return looksLikeCiMutationEvidence(lowerLine, surrounding, context);
        }

        if ("SUPPLY002".equals(rule.id)) {
            return looksLikeUnpinnedRemoteDependencyEvidence(lowerLine, surrounding, context);
        }

        if (rule.id.startsWith("NORM-") && isDocumentationRole(context.role)) {
            return looksLikeNegativeImplementation(surrounding);
        }

        return true;
    }

    private boolean isPayloadOrAttackSampleRule(Rule rule) {
        return rule.id.startsWith("APISEC")
                || rule.id.startsWith("INJ")
                || rule.id.startsWith("NORM-AUTH")
                || rule.id.startsWith("NORM-DATA")
                || rule.id.startsWith("NORM-FILE")
                || rule.id.startsWith("REPORT");
    }

    private boolean looksLikeCredentialEvidence(String lowerLine, String surrounding, LineContext context) {
        if (containsAny(lowerLine,
                "bcryptpasswordencoder", "passwordencoder", "argon2", "pbkdf2", "validatepassword",
                "validatepwd", "userpassword: string", "userpwd", "inputpassword", "example", "demo",
                "sample", "placeholder", "masked", "redacted")
                || isTypeOrSchemaDeclaration(lowerLine)
                || isSafeCredentialSource(lowerLine)
                || isRuntimeCredentialParameter(lowerLine)
                || isCredentialPlaceholderOrTemplate(lowerLine)
                || isTeachingOrFixExample(surrounding)) {
            return false;
        }
        if (context.role == FileRole.SECURITY_REQUIREMENT || context.role == FileRole.TEMPLATE) {
            return looksLikeNegativeImplementation(surrounding);
        }
        if (looksLikeHighEntropySecretAssignment(lowerLine)) {
            return true;
        }
        return containsAny(lowerLine, "=", ":", "password=", "passwd=", "pwd=", "token=", "secret=")
                && containsAny(lowerLine, "\"", "'", "123456", "admin", "password", "qwerty")
                && !containsAny(lowerLine, "{password}", "${password}", "{user}", "{host}", "{database}");
    }

    private boolean looksLikeRealSessionExposure(String lowerLine, String surrounding, LineContext context) {
        if (isTeachingOrFixExample(surrounding)
                || isTypeOrSchemaDeclaration(lowerLine)
                || isRoutineSessionHandling(lowerLine, surrounding)
                || containsAny(lowerLine,
                "<token>", "{token}", "bearer <token>", "abc123", "dummy", "placeholder",
                "example", "sample", "demo", "mock", "masked", "redacted", "脱敏", "占位", "示例", "样例")) {
            return false;
        }
        if (context.role == FileRole.SECURITY_REQUIREMENT || context.role == FileRole.TEMPLATE || context.role == FileRole.TEST_CASE) {
            return looksLikeNegativeImplementation(surrounding) && looksLikeRealSecretValue(lowerLine);
        }
        return looksLikeRealSecretValue(lowerLine)
                || (context.role == FileRole.IMPLEMENTATION
                && looksLikeSensitiveOutputSink(surrounding)
                && containsAny(lowerLine, "token", "session", "cookie", "bearer", "jsessionid"));
    }

    private boolean looksLikeTlsBypassEvidence(String lowerLine, String surrounding, LineContext context) {
        if (!isExecutableOrInstructionContext(context) || isTeachingOrFixExample(surrounding)
                || looksLikeRuleCatalogLine(lowerLine)) {
            return false;
        }
        if (isPositiveControlStatement(surrounding) || containsAny(surrounding,
                "不得关闭", "禁止关闭", "不要关闭", "避免关闭", "must not disable", "do not disable")) {
            return false;
        }
        boolean disablesVerification = containsAny(lowerLine,
                "verify=false", "verify = false", "rejectunauthorized: false",
                "node_tls_reject_unauthorized=0", "node_tls_reject_unauthorized = 0",
                "--insecure", "trustallcerts");
        boolean insecureCurl = lowerLine.matches(".*\\bcurl\\b.*\\s-k\\b.*");
        return disablesVerification || insecureCurl;
    }

    private boolean looksLikeAuthBypassEvidence(String lowerLine, String surrounding, LineContext context) {
        if (!isExecutableOrInstructionContext(context) || isTeachingOrFixExample(surrounding)
                || looksLikeRuleCatalogLine(lowerLine)) {
            return false;
        }
        if (isPositiveControlStatement(surrounding) && !looksLikeNegativeImplementation(surrounding)) {
            return false;
        }
        boolean bypassSwitch = containsAny(lowerLine,
                "skipauth", "disableauth", "bypassauth", "isauthenticated = true",
                "isadmin = true", "mock admin", "免登录", "跳过认证", "绕过鉴权", "禁用鉴权");
        boolean activeContext = context.role == FileRole.IMPLEMENTATION
                || containsAny(surrounding, "run ", "execute ", "调用", "执行", "脚本", "代码中", "当前实现");
        return bypassSwitch && activeContext;
    }

    private boolean looksLikePrivilegedAccountEvidence(String lowerLine, String surrounding, LineContext context) {
        if (isTeachingOrFixExample(surrounding) || isTypeOrSchemaDeclaration(lowerLine)
                || looksLikeRuleCatalogLine(lowerLine)
                || isScannerCredentialWordlist(lowerLine, surrounding)) {
            return false;
        }
        boolean privilegedName = containsAny(lowerLine,
                "admin", "administrator", "root", "superuser", "超级管理员", "超级柜员", "运维账号", "sysdba");
        boolean credentialContext = containsAny(surrounding,
                "password", "passwd", "pwd", "token", "login", "登录", "账号", "account", "username", "=");
        return isExecutableOrInstructionContext(context) && privilegedName && credentialContext;
    }

    private boolean looksLikeProductionEnvironmentEvidence(String lowerLine, String surrounding, LineContext context) {
        if (!isExecutableOrInstructionContext(context) || isTeachingOrFixExample(surrounding)
                || looksLikeRuleCatalogLine(lowerLine)) {
            return false;
        }
        if (containsAny(lowerLine, "forbidden: production", "禁止生产", "不得生产", "不要生产")) {
            return false;
        }
        return containsAny(lowerLine, "jdbc:", "https://", "http://", "host", "endpoint", "url", "token", "账号", "密码")
                && containsAny(surrounding, "prod", "production", "生产环境", "真实环境", "真实库", "核心库",
                "core-bank", "core_bank", "账务", "清算", "支付");
    }

    private boolean looksLikeFinancialPiiEvidence(String lowerLine, String surrounding, LineContext context) {
        if (!isExecutableOrReportContext(context) || isTeachingOrFixExample(surrounding)
                || isTypeOrSchemaDeclaration(lowerLine) || looksLikeRuleCatalogLine(lowerLine)) {
            return false;
        }
        if (containsAny(lowerLine, "masked", "redacted", "脱敏", "示例", "样例", "example", "sample")) {
            return false;
        }
        boolean realIdCard = lowerLine.matches(".*\\b[1-9][0-9]{5}(18|19|20)[0-9]{2}[0-1][0-9][0-3][0-9][0-9x]\\b.*");
        boolean realBankCard = lowerLine.matches(".*\\b[0-9]{16,19}\\b.*")
                && containsAny(lowerLine, "银行卡", "卡号", "cardno", "accountno", "account_no");
        boolean realMobile = lowerLine.matches(".*\\b1[3-9][0-9]{9}\\b.*")
                && containsAny(lowerLine, "手机号", "mobile", "phone");
        return realIdCard || realBankCard || realMobile;
    }

    private boolean looksLikeSensitiveLoggingEvidence(String lowerLine, String surrounding, LineContext context) {
        if (!isExecutableOrReportContext(context) || isTeachingOrFixExample(surrounding)
                || looksLikeRuleCatalogLine(lowerLine)
                || isScannerStatusOrProgressLog(lowerLine, surrounding)) {
            return false;
        }
        boolean logSink = containsAny(lowerLine, "print(", "console.log", "logger.", "log.info", "system.out.println");
        boolean sensitiveValue = looksLikeFinancialPiiEvidence(lowerLine, surrounding, context)
                || looksLikeRealSessionExposure(lowerLine, surrounding, context)
                || looksLikeCredentialEvidence(lowerLine, surrounding, context);
        return logSink && sensitiveValue;
    }

    private boolean looksLikeUserIdentifierExposureEvidence(String lowerLine, String surrounding, LineContext context) {
        if (!isExecutableOrReportContext(context) || isTeachingOrFixExample(surrounding)
                || isTypeOrSchemaDeclaration(lowerLine) || looksLikeRuleCatalogLine(lowerLine)) {
            return false;
        }
        if (containsAny(lowerLine, "masked", "redacted", "脱敏", "example", "sample", "示例", "样例")) {
            return false;
        }
        return containsAny(lowerLine, "\"users\"", "\"user_ids\"", "\"userid\"", "\"operator\"", "柜员", "用户id")
                && lowerLine.matches(".*\\b[0-9]{5,}\\b.*");
    }

    private boolean looksLikeRealCustomerDataEvidence(String lowerLine, String surrounding, LineContext context) {
        if (!isExecutableOrReportContext(context) || isTeachingOrFixExample(surrounding)
                || looksLikeRuleCatalogLine(lowerLine)
                || isGenericBusinessLabelOnly(lowerLine, surrounding)) {
            return false;
        }
        if (containsAny(lowerLine, "synthetic", "mock", "fake", "脱敏", "合成", "示例", "样例")) {
            return false;
        }
        return containsAny(surrounding, "real_customer", "prod_dump", "生产数据", "真实客户", "原始数据",
                "脱敏前", "客户流水", "交易流水", "账户流水");
    }

    private boolean looksLikeBulkDataExportEvidence(String lowerLine, String surrounding, LineContext context) {
        if (!isExecutableOrInstructionContext(context) || isTeachingOrFixExample(surrounding)
                || looksLikeRuleCatalogLine(lowerLine)) {
            return false;
        }
        boolean exportCommand = containsAny(lowerLine,
                "select * from", "mysqldump", "expdp", "pg_dump", "mongoexport", "导出");
        boolean sensitiveTable = containsAny(surrounding,
                "customer", "account", "card", "transaction", "客户", "账户", "银行卡", "交易", "流水");
        return exportCommand && sensitiveTable;
    }

    private boolean looksLikeReportLeakEvidence(String lowerLine, String surrounding, LineContext context) {
        if (context.role == FileRole.TEST_CASE || context.role == FileRole.EXAMPLE_DOC
                || context.role == FileRole.SECURITY_REQUIREMENT || context.role == FileRole.RULE_DEFINITION
                || isTeachingOrFixExample(surrounding) || looksLikeRuleCatalogLine(lowerLine)) {
            return false;
        }
        if (containsAny(lowerLine,
                "masked", "redacted", "脱敏", "unknown", "none", "null",
                "<token>", "{token}", "bearer <token>", "example", "sample", "示例", "样例")) {
            return false;
        }
        boolean reportSink = context.role == FileRole.ANALYSIS_REPORT
                || containsAny(surrounding, "report", "allure", "har", "trace", "screenshot", "attachment",
                "报告", "截图", "附件", "缺陷", "jira", "禅道", "tapd", "webhook", "email");
        boolean realLeak = looksLikeRealReportLeak(lowerLine)
                || looksLikeFinancialPiiEvidence(lowerLine, surrounding, context)
                || looksLikeUserIdentifierExposureEvidence(lowerLine, surrounding, context)
                || lowerLine.matches(".*\\b(auth_info|authorization|cookie|session|token)\\b.*[:=].*[0-9a-z._-]{8,}.*")
                || lowerLine.matches(".*\\b(taskid|eventid|orderid|request_body|request_params)\\b.*[:=].*[0-9]{5,}.*");
        return reportSink && realLeak;
    }

    private boolean looksLikeBusinessRiskEvidence(String lowerLine, String surrounding, LineContext context) {
        if (isTeachingOrFixExample(surrounding) || looksLikeRuleCatalogLine(lowerLine)) {
            return false;
        }
        if (isDocumentationRole(context.role) || isPositiveControlStatement(surrounding)) {
            return looksLikeNegativeImplementation(surrounding);
        }
        boolean businessKeyword = containsAny(surrounding,
                "水平越权", "垂直越权", "idor", "未授权", "审批", "支付", "退款", "转账", "交易", "放款",
                "user_id", "userid", "orderid", "projectid", "amount", "金额");
        boolean missingControl = containsAny(surrounding,
                "无鉴权", "未校验", "前端校验", "可篡改", "跳过", "绕过", "直接调用",
                "重复提交", "无幂等", "金额可改", "无二次认证");
        return isExecutableOrInstructionContext(context) && businessKeyword && missingControl;
    }

    private boolean looksLikeWorkspaceEscapeEvidence(String lowerLine, String surrounding, LineContext context) {
        if (!isExecutableOrInstructionContext(context) || isTeachingOrFixExample(surrounding)
                || looksLikeRuleCatalogLine(lowerLine)) {
            return false;
        }
        if (hasPathContainmentEvidence(surrounding)
                || containsAny(surrounding, "workspace", "tempdir", "tmpdir", "safe_join", "secure_filename")) {
            return false;
        }
        boolean outsidePath = containsAny(lowerLine,
                "../", "..\\", "c:\\users\\", "/users/", "/home/", "/etc/", "/var/",
                "desktop", "downloads", "公共目录", "用户目录", "系统目录");
        boolean fileMutation = containsAny(surrounding,
                "write", "open(", "save", "download", "extract", "unzip", "copy", "move", "delete",
                "remove-item", "写入", "保存", "下载", "解压", "复制", "移动", "删除");
        return outsidePath && fileMutation;
    }

    private boolean looksLikeRemoteInstructionEvidence(String lowerLine, String surrounding, LineContext context) {
        if (isTeachingOrFixExample(surrounding) || looksLikeRuleCatalogLine(lowerLine)) {
            return false;
        }
        boolean remoteInstruction = containsAny(lowerLine, "http://", "https://")
                && containsAny(lowerLine, ".md", ".txt", "prompt", "instruction", "instructions", "脚本", "规则");
        boolean actionable = context.role == FileRole.SKILL_INSTRUCTION
                || containsAny(surrounding, "按照", "参考", "加载", "执行", "follow", "load", "run", "execute");
        boolean pinned = containsAny(surrounding, "sha256", "checksum", "commit", "tag", "version", "固定版本", "校验摘要");
        return remoteInstruction && actionable && !pinned;
    }

    private boolean looksLikeUntrustedOutputInstructionEvidence(String lowerLine, String surrounding, LineContext context) {
        if (isDocumentationRole(context.role) || isTeachingOrFixExample(surrounding)
                || looksLikeRuleCatalogLine(lowerLine) || isPositiveControlStatement(surrounding)) {
            return false;
        }
        boolean untrustedSource = containsAny(surrounding,
                "网页", "页面", "tool output", "tool_output", "工具输出", "browser output", "browser_output", "response", "返回内容",
                "用户输入", "外部文档", "external page", "external content");
        boolean instructionUse = containsAny(surrounding,
                "作为指令", "当作指令", "遵循", "执行", "follow", "obey", "treat as instruction");
        return looksLikeActionableSkillInstruction(surrounding) && untrustedSource && instructionUse;
    }

    private boolean looksLikeExcessiveAgencyEvidence(String lowerLine, String surrounding, LineContext context) {
        if (!isExecutableOrInstructionContext(context) || isTeachingOrFixExample(surrounding)
                || looksLikeRuleCatalogLine(lowerLine)) {
            return false;
        }
        if (containsAny(surrounding, "人工确认", "用户确认", "approval", "confirm", "dry_run", "sandbox", "沙箱")) {
            return false;
        }
        boolean autonomous = containsAny(surrounding,
                "自动执行", "自动点击", "自动提交", "auto-run", "autorun", "auto approve",
                "auto-submit", "auto-click", "无人值守", "without confirmation", "without asking");
        boolean highRiskAction = containsAny(surrounding,
                "支付", "转账", "审批", "删除", "生产", "prod", "deploy", "发布",
                "外联", "download", "execute", "rm -rf", "subprocess", "os.system");
        return autonomous && highRiskAction;
    }

    private boolean looksLikeModelOrToolOutputExecutionEvidence(String lowerLine, String surrounding, LineContext context) {
        if (!isExecutableOrInstructionContext(context) || isTeachingOrFixExample(surrounding)
                || looksLikeRuleCatalogLine(lowerLine)) {
            return false;
        }
        if (containsAny(surrounding, "白名单", "allowlist", "人工确认", "safe parser", "literal_eval")) {
            return false;
        }
        boolean untrustedOutput = containsAny(surrounding,
                "model output", "model_output", "llm output", "llm_output", "assistant output",
                "assistant_output", "tool output", "tool_output", "模型输出", "工具输出", "返回结果");
        boolean executionSink = containsAny(surrounding,
                "eval(", "exec(", "os.system", "subprocess", "shell", "execute", "执行命令", "运行代码");
        boolean currentLineExecutes = containsAny(lowerLine,
                "eval(", "exec(", "os.system", "subprocess", "shell", "执行命令", "运行代码")
                || lowerLine.matches(".*\\bexecute\\b.*");
        return untrustedOutput && executionSink && currentLineExecutes;
    }

    private boolean looksLikeInternalNetworkOrSsrfEvidence(String lowerLine, String surrounding, LineContext context) {
        if (!isExecutableOrInstructionContext(context) || isTeachingOrFixExample(surrounding)
                || looksLikeRuleCatalogLine(lowerLine)) {
            return false;
        }
        if (hasNetworkAllowlistEvidence(surrounding)
                || containsAny(surrounding, "deny_private", "block_private", "ssrf_guard", "禁止内网")) {
            return false;
        }
        boolean internalTarget = containsAny(surrounding,
                "169.254.169.254", "metadata.google", "metadata.azure", "localhost", "127.0.0.1",
                "0.0.0.0", "internal", "intranet", "内网", "元数据", "metadata")
                || surrounding.matches("(?s).*(10\\.\\d+\\.\\d+\\.\\d+|172\\.(1[6-9]|2\\d|3[0-1])\\.\\d+\\.\\d+|192\\.168\\.\\d+\\.\\d+).*");
        boolean requestSink = containsAny(surrounding,
                "curl ", "wget ", "requests.", "httpx.", "fetch(", "urllib.", "openconnection", "urlopen");
        return internalTarget && requestSink;
    }

    private boolean looksLikeUnpinnedRemoteDependencyEvidence(String lowerLine, String surrounding, LineContext context) {
        if (!isExecutableOrInstructionContext(context) || isTeachingOrFixExample(surrounding)
                || looksLikeRuleCatalogLine(lowerLine)) {
            return false;
        }
        if (containsAny(surrounding, "sha256", "checksum", "lockfile", "package-lock", "poetry.lock", "固定版本")) {
            return false;
        }
        boolean installer = containsAny(lowerLine, "pip ", "npm ", "pnpm ", "yarn ", "mvn ", "gradle ");
        boolean unpinnedRemote = containsAny(lowerLine, "git+http://", "git+https://", "@main", "@master", "latest")
                || lowerLine.matches(".*https?://.*\\.(zip|tar\\.gz|tgz).*");
        return installer && unpinnedRemote;
    }

    private boolean isExecutableOrInstructionContext(LineContext context) {
        return context.role == FileRole.IMPLEMENTATION
                || context.role == FileRole.SKILL_INSTRUCTION
                || context.inCodeFence;
    }

    private boolean isExecutableOrReportContext(LineContext context) {
        return context.role == FileRole.IMPLEMENTATION
                || context.role == FileRole.ANALYSIS_REPORT
                || context.inCodeFence;
    }

    private boolean looksLikeUnsafeCryptoEvidence(String lowerLine, String surrounding, LineContext context) {
        if (isTeachingOrFixExample(surrounding)
                || containsAny(surrounding, "禁止", "不得", "避免", "不要", "must not", "do not", "avoid", "forbidden")) {
            return false;
        }
        if (containsAny(lowerLine, "bcrypt", "argon2", "pbkdf2", "scrypt", "passwordencoder")) {
            return false;
        }
        return context.role == FileRole.IMPLEMENTATION
                && containsAny(lowerLine, "md5", "sha1", "des", "base64")
                && containsAny(surrounding, "password", "token", "secret", "cookie", "encrypt", "digest", "hash");
    }

    private boolean looksLikeActivePayloadUse(String lowerLine, String surrounding, LineContext context) {
        if (context.role == FileRole.TEST_CASE || context.role == FileRole.EXAMPLE_DOC || context.role == FileRole.SECURITY_REQUIREMENT) {
            return false;
        }
        if (containsAny(surrounding, "example", "demo", "sample", "test payload", "security payload", "payload sample",
                "示例", "样例", "测试payload", "安全测试")) {
            return false;
        }
        return context.role == FileRole.IMPLEMENTATION
                && containsAny(surrounding, "fetch(", "requests.", "httpx.", "curl ", "post(", "get(", "send", "open(");
    }

    private boolean looksLikeExecutableCodeEvidence(String lowerLine, String surrounding, LineContext context) {
        if (context.role != FileRole.IMPLEMENTATION && !context.inCodeFence) {
            return false;
        }
        if (containsAny(lowerLine, "example", "demo", "sample", "do not", "avoid", "禁止", "避免")) {
            return false;
        }
        if (isRegexOrParserExec(lowerLine, surrounding) || isStaticDispatchEval(lowerLine, surrounding)) {
            return false;
        }
        boolean evalSink = containsAny(lowerLine, "eval(", "__import__(", "new function", "function(",
                "vm.runinnewcontext", "vm.runinthiscontext");
        boolean execSink = containsAny(lowerLine, "child_process.exec", "runtime.getruntime().exec",
                "os.system(", "subprocess.", "exec(")
                && !isRegexOrParserExec(lowerLine, surrounding);
        return evalSink || execSink;
    }

    private boolean looksLikeRiskyShellExecution(String lowerLine, String surrounding, LineContext context) {
        if (context.role != FileRole.IMPLEMENTATION && !context.inCodeFence) {
            return false;
        }
        if (isTeachingOrFixExample(surrounding)) {
            return false;
        }
        if (containsAny(surrounding, "git diff", "git log", "git show")
                && !containsAny(surrounding, "user", "input", "args", "request", "prompt", "+", "{", "$")) {
            return false;
        }
        return containsAny(lowerLine, "shell=true", "child_process.exec", "runtime.getruntime().exec")
                || containsAny(lowerLine, "os.system(");
    }

    private boolean looksLikeDangerousCommandEvidence(String lowerLine, String surrounding, LineContext context) {
        if (context.role != FileRole.IMPLEMENTATION && !context.inCodeFence) {
            return false;
        }
        if (isTeachingOrFixExample(surrounding) || looksLikeRuleCatalogLine(lowerLine)) {
            return false;
        }
        if (isBenignPackageManagerCleanup(lowerLine, surrounding)) {
            return false;
        }
        if (containsAny(lowerLine, "sudo", "su ", "chmod 777", "chmod a+rwx", "dd ") && !hasUserControlledEvidence(surrounding)) {
            return false;
        }
        return containsAny(lowerLine, "rm -rf /", "rm -rf ~", "chmod 777", "chmod a+rwx", "sudo ", "su ",
                "dd ", "of=/", "> /etc/", "> /usr/", "> /bin/", "> /sbin/")
                && (hasUserControlledEvidence(surrounding) || containsAny(lowerLine, "rm -rf /", "rm -rf ~", "chmod 777"));
    }

    private boolean looksLikeAgentInstructionRiskEvidence(String lowerLine, String surrounding, LineContext context) {
        if (isDocumentationRole(context.role) || looksLikeRuleCatalogLine(lowerLine) || isPureCommentLine(lowerLine)) {
            return false;
        }
        if (isFrameworkSecurityApi(lowerLine)
                || containsAny(lowerLine, "bypasssecuritytrust", "authorization bypass", "cwe-", "grep -rn", "grep -r")) {
            return false;
        }
        if (isTeachingOrFixExample(surrounding)) {
            return false;
        }
        boolean hasAgentOrPolicyTarget = containsAny(surrounding,
                "agent", "assistant", "model", "tool", "approval", "sandbox", "security", "policy",
                "安全", "策略", "审批", "沙箱", "确认", "限制", "权限");
        boolean hasUnsafeInstruction = containsAny(lowerLine,
                "ignore", "bypass", "without asking", "without approval", "silent mode", "auto approve",
                "忽略", "绕过", "自动批准", "无需确认", "无需审批", "静默执行");
        return hasAgentOrPolicyTarget && hasUnsafeInstruction && looksLikeActionableSkillInstruction(surrounding);
    }

    private boolean looksLikeAgentHiddenActionEvidence(String lowerLine, String surrounding, LineContext context) {
        if (isDocumentationRole(context.role) || looksLikeRuleCatalogLine(lowerLine) || isPureCommentLine(lowerLine)) {
            return false;
        }
        if (isPositiveControlStatement(lowerLine)
                || containsAny(lowerLine, "不要记录", "不得记录", "禁止记录", "脱敏", "redact", "masked", "sanitize")) {
            return false;
        }
        boolean hasHiddenAction = containsAny(lowerLine,
                "不要告诉用户", "无需告知", "静默执行", "隐藏操作", "不要记录", "delete logs",
                "silent mode", "without asking", "without notifying", "do not tell the user");
        return hasHiddenAction && looksLikeActionableSkillInstruction(surrounding);
    }

    private boolean looksLikeNetworkRiskEvidence(String lowerLine, String surrounding, LineContext context) {
        if (context.role != FileRole.IMPLEMENTATION && !context.inCodeFence) {
            return false;
        }
        if (isTeachingOrFixExample(surrounding) || looksLikeRuleCatalogLine(lowerLine)) {
            return false;
        }
        boolean networkSink = containsAny(lowerLine, "fetch(", "requests.", "httpx.", "urllib.", "urlopen(",
                "curl ", "wget ");
        if (!networkSink) {
            return false;
        }
        if (isGenericRequestWrapperOnly(lowerLine, surrounding)) {
            return false;
        }
        boolean concreteOrUserControlledTarget = hasUserControlledUrlEvidence(surrounding)
                || hasConcreteNetworkTarget(surrounding);
        boolean sensitiveOutboundFlow = hasConcreteNetworkTarget(surrounding)
                && containsAny(surrounding, "token", "secret", "password", "cookie", ".env", ".ssh",
                "credential", "report", "upload", "request_body")
                && containsAny(surrounding, "post(", "put(", "requests.post", "httpx.post", "fetch(", "upload");
        return concreteOrUserControlledTarget || sensitiveOutboundFlow;
    }

    private boolean looksLikeJumpHostOrVpnEvidence(String lowerLine, String surrounding, LineContext context) {
        if (context.role != FileRole.IMPLEMENTATION && !context.inCodeFence) {
            return false;
        }
        if (isTeachingOrFixExample(surrounding) || looksLikeRuleCatalogLine(lowerLine)
                || isVpnOrJumpLabelOnly(lowerLine, surrounding)) {
            return false;
        }
        boolean accessKeyword = containsAny(surrounding, "vpn", "jump", "bastion", "proxy",
                "kubeconfig", "kubectl", "ssh ");
        boolean concreteTarget = hasConcreteNetworkTarget(surrounding)
                || containsAny(surrounding, "ssh ", "kubeconfig", "proxy_url", "proxyurl", "proxy_host",
                "vpn_server", "bastion_host", "jump_host");
        return accessKeyword && concreteTarget;
    }

    private boolean looksLikeExfiltrationEvidence(String lowerLine, String surrounding, LineContext context) {
        if (context.role != FileRole.IMPLEMENTATION && !context.inCodeFence) {
            return false;
        }
        if (isTeachingOrFixExample(surrounding) || looksLikeRuleCatalogLine(lowerLine)) {
            return false;
        }
        return containsAny(surrounding, "token", "secret", "password", ".env", ".ssh", "cookie", "credential")
                && containsAny(lowerLine, "post(", "put(", "fetch(", "requests.post", "httpx.post",
                "curl ", "wget ", "--data", "-d", "@");
    }

    private boolean looksLikeHostProxyMutationEvidence(String lowerLine, String surrounding, LineContext context) {
        if (context.role != FileRole.IMPLEMENTATION && !context.inCodeFence) {
            return false;
        }
        if (isTeachingOrFixExample(surrounding) || looksLikeRuleCatalogLine(lowerLine)) {
            return false;
        }
        if (containsAny(lowerLine, "allowed_hosts")) {
            return containsAny(lowerLine, "=", ":") && containsAny(lowerLine, "\"*\"", "'*'", "[*]");
        }
        return containsAny(lowerLine, "/etc/hosts", "drivers/etc/hosts", "http_proxy", "https_proxy",
                "no_proxy", "truststore", "keystore", "node_tls_reject_unauthorized")
                && containsAny(surrounding, "=", "set", "write", "append", "export", "putenv", "environ", "trustall");
    }

    private boolean looksLikeDatabaseMutationEvidence(String lowerLine, String surrounding, LineContext context) {
        if (context.role != FileRole.IMPLEMENTATION && !context.inCodeFence) {
            return false;
        }
        if (isTeachingOrFixExample(surrounding) || looksLikeRuleCatalogLine(lowerLine)) {
            return false;
        }
        return containsAny(surrounding, "update ", "delete ", "insert ", "execute(", "cursor.", "query(",
                "jdbc", "sql", "orm", "save(", ".update(")
                && containsAny(lowerLine, "balance", "amount", "limit", "credit", "rate", "status", "freeze");
    }

    private boolean looksLikeSqlMutationEvidence(String lowerLine, String surrounding, LineContext context) {
        if (context.role != FileRole.IMPLEMENTATION && !context.inCodeFence) {
            return false;
        }
        if (isTeachingOrFixExample(surrounding) || looksLikeRuleCatalogLine(lowerLine)) {
            return false;
        }
        boolean destructiveSql = containsAny(lowerLine, "delete from", "update ", "truncate table", "drop table");
        boolean executed = containsAny(surrounding, "execute", "cursor", "query", "jdbc", "session.", "connection", "engine");
        boolean stagedSql = containsAny(lowerLine, "sql =", "query =", "statement =", "stmt =");
        return destructiveSql && (executed || stagedSql);
    }

    private boolean looksLikeCiMutationEvidence(String lowerLine, String surrounding, LineContext context) {
        if (context.role == FileRole.SECURITY_REQUIREMENT || context.role == FileRole.RULE_DEFINITION
                || context.role == FileRole.EXAMPLE_DOC || context.role == FileRole.TEMPLATE) {
            return false;
        }
        if (isTeachingOrFixExample(surrounding) || looksLikeRuleCatalogLine(lowerLine)) {
            return false;
        }
        return containsAny(lowerLine, ".github/workflows", ".gitlab-ci", "jenkinsfile", "azure-pipelines", "circleci")
                || containsAny(lowerLine, "skip tests", "skiptests", "maven.test.skip", "disable", "quality gate", "sonar");
    }

    private boolean looksLikeSensitiveFileAccess(String lowerLine, String surrounding, LineContext context) {
        if (looksLikeRuleCatalogLine(lowerLine) || isTeachingOrFixExample(surrounding)) {
            return false;
        }
        if (isSafeCredentialSource(lowerLine)) {
            return false;
        }
        if (context.role == FileRole.SECURITY_REQUIREMENT || context.role == FileRole.TEMPLATE
                || context.role == FileRole.EXAMPLE_DOC || context.role == FileRole.ANALYSIS_REPORT
                || context.role == FileRole.RULE_DEFINITION) {
            return false;
        }
        return containsAny(lowerLine, "~/.ssh", ".ssh/", "id_rsa", "id_ed25519", ".aws/credentials",
                "credentials.json", ".pem", ".key", ".env")
                && (context.role == FileRole.IMPLEMENTATION
                || containsAny(surrounding, "open(", "read", "readfile", "fs.", "path", "load", "cat "));
    }

    private boolean isBenignPackageManagerCleanup(String lowerLine, String surrounding) {
        return containsAny(lowerLine, "rm -rf /var/lib/apt/lists", "rm -rf /tmp/*", "rm -rf /var/cache/apt")
                && containsAny(surrounding, "dockerfile", "apt-get", "apt ");
    }

    private boolean isFrameworkSecurityApi(String lowerLine) {
        return containsAny(lowerLine, "bypasssecuritytrusthtml", "bypasssecuritytrustscript",
                "bypasssecuritytrusturl", "bypasssecuritytrustresourceurl", "bypasssecuritytruststyle");
    }

    private boolean looksLikeActionableSkillInstruction(String lowerLine) {
        String trimmed = lowerLine.trim();
        return containsAny(trimmed, "run ", "execute ", "call ", "send ", "read ", "write ", "delete ",
                "connect ", "download ", "upload ", "curl ", "wget ", "requests.", "fetch(")
                || trimmed.startsWith("- run")
                || trimmed.startsWith("- execute")
                || trimmed.startsWith("- call")
                || trimmed.startsWith("- read")
                || trimmed.startsWith("- write")
                || trimmed.startsWith("- delete");
    }

    private boolean isCapabilityOnlyFinding(Rule rule, String lowerLine, LineContext context) {
        if (rule.id.startsWith("NORM-")) {
            return true;
        }
        if (context.role != FileRole.IMPLEMENTATION && !context.inCodeFence) {
            return true;
        }
        if ("NET001".equals(rule.id)) {
            return !hasUserControlledUrlEvidence(lowerLine) && !hasConcreteNetworkTarget(lowerLine);
        }
        if ("CMD002".equals(rule.id)) {
            return !containsAny(lowerLine, "shell=true", "user", "input", "args", "argv", "request", "prompt");
        }
        if ("ENV002".equals(rule.id)) {
            return true;
        }
        if ("BANK003".equals(rule.id) || "DB001".equals(rule.id) || "DB002".equals(rule.id)) {
            return containsAny(lowerLine, "sql =", "query =", "statement =", "stmt =")
                    && !containsAny(lowerLine, "execute", "cursor", "connection", "session.");
        }
        return false;
    }

    private boolean isDeclaredCapabilityFinding(Rule rule, String lowerLine) {
        if ("NET001".equals(rule.id)) {
            return containsAny(lowerLine, "settings.", "config.", "external_api_url", "api_url", "endpoint", "base_url")
                    && !hasUserControlledEvidence(lowerLine);
        }
        if ("CMD002".equals(rule.id)) {
            return !hasUserControlledEvidence(lowerLine);
        }
        return false;
    }

    private boolean hasExploitChainEvidence(Rule rule, String lowerLine, String surrounding, LineContext context) {
        if (context.role != FileRole.IMPLEMENTATION && !context.inCodeFence) {
            return false;
        }
        if ("NET001".equals(rule.id) || "EXFIL001".equals(rule.id)) {
            return (hasUserControlledUrlEvidence(surrounding) || hasConcreteNetworkTarget(surrounding))
                    && containsAny(surrounding, "fetch(", "requests.", "httpx.", "urllib.", "urlopen(", "curl ", "wget ")
                    && !hasNetworkAllowlistEvidence(surrounding)
                    && !isGenericRequestWrapperOnly(lowerLine, surrounding);
        }
        if ("CMD001".equals(rule.id) || "CMD002".equals(rule.id) || "INJ001".equals(rule.id)) {
            return hasUserControlledEvidence(surrounding)
                    && containsAny(surrounding, "shell=true", "os.system", "subprocess.", "child_process.exec",
                    "eval(", "exec(", "__import__(", "new function");
        }
        if ("FILE001".equals(rule.id)) {
            return hasUserControlledEvidence(surrounding)
                    && containsAny(surrounding, "open(", "read", "readfile", "fs.", "cat ")
                    && !hasPathContainmentEvidence(surrounding);
        }
        return hasSensitiveLiteralEvidence(lowerLine);
    }

    private boolean hasStrongMitigationEvidence(Rule rule, String lowerLine, String surrounding, LineContext context) {
        if (rule.id.startsWith("NORM-")) {
            return false;
        }
        if ("NET001".equals(rule.id) || "EXFIL001".equals(rule.id)) {
            return hasNetworkAllowlistEvidence(surrounding)
                    || containsAny(surrounding, "mock", "localhost", "127.0.0.1", "sandbox", "test server");
        }
        if ("FILE001".equals(rule.id) || rule.id.startsWith("NORM-FILE")) {
            return hasPathContainmentEvidence(surrounding);
        }
        if ("CMD001".equals(rule.id) || "CMD002".equals(rule.id)) {
            return containsAny(surrounding, "shell=false", "shell = false", "subprocess.run([", "subprocess.call([",
                    "shlex.quote", "list args", "allowlist", "whitelist")
                    && !hasUserControlledEvidence(lowerLine);
        }
        if ("INJ001".equals(rule.id)) {
            return containsAny(surrounding, "allowlist", "whitelist", "safe_eval", "literal_eval", "mapping", "dispatch table");
        }
        if ("DB001".equals(rule.id) || "DB002".equals(rule.id) || "BANK003".equals(rule.id)) {
            return containsAny(surrounding, "where ", "transaction", "rollback", "dry_run", "sandbox", "test_db",
                    "parameterized", "preparedstatement", "bindparam");
        }
        if ("AUTH002".equals(rule.id) || "BANK004".equals(rule.id) || rule.id.startsWith("REPORT")) {
            return containsAny(surrounding, "masked", "redacted", "sanitize", "scrub", "hash", "脱敏", "遮蔽");
        }
        return false;
    }

    private boolean hasNetworkAllowlistEvidence(String text) {
        return containsAny(text, "allowlist", "whitelist", "allowed_hosts", "allowed_domains",
                "validate_url", "validateurl", "is_allowed_url", "isallowedurl", "safe_url",
                "urlparse", "parse.url", "hostname in", "netloc in", "deny_private", "block_private",
                "ssrf_guard", "ssrfguard");
    }

    private boolean hasConcreteNetworkTarget(String text) {
        if (text == null) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.matches("(?s).*https?://[a-z0-9._~:/?#\\[\\]@!$&'()*+,;=%-]+.*")
                || lower.matches("(?s).*\\b(?:[0-9]{1,3}\\.){3}[0-9]{1,3}\\b.*")
                || lower.matches("(?s).*\\b[a-z0-9][a-z0-9.-]+\\.(?:com|cn|net|org|io|local|internal|corp|bank)\\b.*");
    }

    private boolean hasUserControlledUrlEvidence(String text) {
        return containsAny(text, "user_url", "target_url", "input_url", "callback_url", "redirect_url",
                "request.url", "req.url", "query.url", "req.query.url", "params.url", "args.url",
                "argv", "process.argv", "sys.argv", "url = input", "url=input", "endpoint = input",
                "endpoint=input", "prompt_url");
    }

    private boolean isGenericRequestWrapperOnly(String lowerLine, String surrounding) {
        return containsAny(lowerLine, "urlopen(req", "urlopen(request", "fetch(req", "fetch(request")
                && !hasConcreteNetworkTarget(surrounding)
                && !hasUserControlledUrlEvidence(surrounding)
                && !containsAny(surrounding, "token", "secret", "password", "cookie", "upload", "download",
                ".env", ".ssh", "request_body");
    }

    private boolean isNetworkCapabilityOnly(String lowerLine, String surrounding) {
        boolean networkCall = containsAny(lowerLine, "fetch(", "requests.", "httpx.", "urllib.", "urlopen(",
                "curl ", "wget ");
        if (!networkCall) {
            return false;
        }
        if (hasUserControlledUrlEvidence(surrounding) || hasConcreteNetworkTarget(surrounding)) {
            return false;
        }
        boolean genericVariableCall = containsAny(lowerLine,
                "urlopen(req", "urlopen(request", "urlopen(url", "urlopen(endpoint",
                "requests.get(url", "requests.post(url", "httpx.get(url", "httpx.post(url",
                "fetch(url", "fetch(req", "fetch(request");
        boolean noOutboundDataSink = !containsAny(surrounding,
                "requests.post", "httpx.post", "fetch(", "upload", "webhook", "email", "exfil",
                "send_report", "request_body", "multipart", "files=");
        return genericVariableCall && noOutboundDataSink;
    }

    private boolean isDependencyIntegrityOrLabelOnly(String lowerLine, String surrounding) {
        if (containsAny(lowerLine, "\"integrity\"", "'integrity'", "sha512-", "sha384-", "sha256-")) {
            return true;
        }
        boolean accessLabel = containsAny(lowerLine,
                "\"vpn\"", "'vpn'", "\"jump\"", "'jump'", "\"proxy\"", "'proxy'",
                "vpnout", "vpn_out", "vpn出口", "vpn代理", "堡垒机", "跳板机");
        return accessLabel
                && !hasConcreteNetworkTarget(surrounding)
                && !containsAny(surrounding, "connect(", "ssh ", "kubectl", "kubeconfig",
                "proxy_host", "proxy_url", "vpn_server", "bastion_host", "jump_host");
    }

    private boolean hasPathContainmentEvidence(String text) {
        return containsAny(text, "path.normalize", "path.resolve", "realpath", "resolve()",
                "startswith", "starts_with", "relative_to", "safe_join", "secure_filename",
                "workspace", "tempdir", "tmpdir", "allowlist", "whitelist");
    }

    private boolean hasUserControlledEvidence(String text) {
        return containsAny(text, "user", "input", "args", "argv", "request.", "request[", "request(",
                "request.get", "request.form", "request.args", "req.", "query", "params",
                "body", "form", "prompt", "payload", "filename", "filepath", "path_param",
                "route", "headers", "cookie", "process.argv", "sys.argv", "os.environ[", "getenv(");
    }

    private boolean isDependencyMetadataFile(String lowerFile) {
        return lowerFile.endsWith("package-lock.json")
                || lowerFile.endsWith("pnpm-lock.yaml")
                || lowerFile.endsWith("yarn.lock")
                || lowerFile.endsWith("poetry.lock")
                || lowerFile.endsWith("pipfile.lock")
                || lowerFile.endsWith("composer.lock")
                || lowerFile.endsWith("cargo.lock")
                || lowerFile.endsWith("go.sum");
    }

    private boolean isDependencyRiskRule(Rule rule) {
        if (rule == null || rule.id == null) {
            return false;
        }
        String id = rule.id.toUpperCase(Locale.ROOT);
        return id.startsWith("DEP") || id.startsWith("SUPPLY") || id.startsWith("CVE");
    }

    private boolean isScannerCredentialWordlist(String lowerLine, String surrounding) {
        boolean literalList = containsAny(lowerLine,
                "\"admin\"", "'admin'", "\"administrator\"", "'administrator'",
                "\"password\"", "'password'", "\"passwd\"", "'passwd'", "\"pwd\"", "'pwd'",
                "\"123456\"", "'123456'", "\"12345678\"", "'12345678'", "\"qwerty\"", "'qwerty'",
                "\"root\"", "'root'", "\"guest\"", "'guest'");
        if (!literalList) {
            return false;
        }
        boolean scannerOrPatternContext = containsAny(surrounding,
                "plaintext password", "password_patterns", "weak_password", "common_password",
                "value_upper", "value_lower", "wordlist", "dictionary", "regex", "pattern",
                "scan", "scanner", "detect", "detector", "rule", "rules", "候选", "字典");
        boolean activeLoginUse = containsAny(surrounding,
                "login(", "authenticate(", "auth(", "requests.", "fetch(", "axios.",
                "session.post", ".post(", "set_password", "create_user", "createuser");
        return scannerOrPatternContext && !activeLoginUse;
    }

    private boolean isScannerStatusOrProgressLog(String lowerLine, String surrounding) {
        boolean logSink = containsAny(lowerLine, "print(", "console.log", "logger.", "log.info", "system.out.println");
        if (!logSink) {
            return false;
        }
        boolean scannerText = containsAny(lowerLine, "scanning", "scan ", "scanner", "detected", "found ",
                "suggested", "step ", "checking", "plaintext password", "passwords...", "rsid")
                || containsAny(surrounding, "scanner", "detect", "regex", "pattern", "rule", "value_upper",
                "value_lower", "password_patterns");
        return scannerText && !looksLikeRealSecretValue(lowerLine) && !looksLikeHighEntropySecretAssignment(lowerLine);
    }

    private boolean isBenignDocumentMetadataLog(String lowerFile, String lowerLine, String surrounding) {
        boolean docContext = containsAny(lowerFile, "docx", "ooxml", "word/", "document")
                || containsAny(surrounding, "docx", "ooxml", "word/", "document", "revision", "rsid", "edit session");
        if (!docContext) {
            return false;
        }
        boolean metadataTerm = containsAny(lowerLine, "rsid", "revision", "edit session", "suggested_rsid",
                "document id", "doc_id", "paragraph id", "style id");
        if (!metadataTerm) {
            return false;
        }
        boolean realLeak = containsAny(surrounding, "token", "cookie", "password", "passwd", "secret",
                "id_card", "card_no", "account_no", "mobile", "phone", "customer", "客户号", "手机号", "身份证", "银行卡")
                || looksLikeRealSecretValue(lowerLine)
                || hasSensitiveLiteralEvidence(lowerLine);
        return !realLeak;
    }

    private boolean isCredentialCandidateCorpus(String lowerFile, String lowerLine, String surrounding) {
        boolean scannerContext = containsAny(lowerFile, "password-scan", "plaintext-password", "weak-password",
                "scanner", "scan_", "-scan", "detect", "detector")
                || containsAny(surrounding, "plaintext password", "weak_password", "common_password",
                "password_patterns", "wordlist", "dictionary", "candidate", "候选", "字典", "扫描");
        if (!scannerContext) {
            return false;
        }
        boolean candidateList = (lowerLine.contains("[") || lowerLine.contains("{") || lowerLine.contains(","))
                && containsAny(lowerLine, "\"admin\"", "'admin'", "\"password\"", "'password'",
                "\"123456\"", "'123456'", "\"12345678\"", "'12345678'", "\"root\"", "'root'",
                "\"guest\"", "'guest'", "\"qwerty\"", "'qwerty'");
        if (!candidateList) {
            return false;
        }
        boolean activeLoginUse = containsAny(surrounding, "login(", "authenticate(", "auth(",
                "requests.", "fetch(", "axios.", "session.post", ".post(", "create_user", "createuser");
        return !activeLoginUse && !looksLikeRealSecretValue(lowerLine) && !looksLikeHighEntropySecretAssignment(lowerLine);
    }

    private boolean isRegexOrParserExec(String lowerLine, String surrounding) {
        if (containsAny(lowerLine, "child_process.exec", "runtime.getruntime().exec", "os.exec", "subprocess.")) {
            return false;
        }
        return containsAny(lowerLine, "regex.exec(", "regexp.exec(", ".exec(errorstack", ".exec(stack",
                ".exec(line", ".exec(text", ".exec(content")
                || (lowerLine.contains(".exec(") && containsAny(surrounding, "regex", "regexp", "pattern",
                "parse", "parser", "stackregex"));
    }

    private boolean isStaticDispatchEval(String lowerLine, String surrounding) {
        return containsAny(lowerLine, "eval(\"expect_\"", "eval('expect_'", "eval(\"check_\"", "eval('check_'")
                && containsAny(surrounding, "expect_dict", "dispatch", "mapping", "function table", "test case");
    }

    private boolean isVpnOrJumpLabelOnly(String lowerLine, String surrounding) {
        boolean label = containsAny(lowerLine, "\"vpn\"", "'vpn'", "vpnout", "vpn_out", "vpn代理", "vpn出口",
                "\"jump\"", "'jump'", "\"bastion\"", "'bastion'");
        if (!label) {
            return false;
        }
        return !hasConcreteNetworkTarget(surrounding)
                && !containsAny(surrounding, "ssh ", "kubeconfig", "proxy_host", "proxy_url",
                "vpn_server", "bastion_host", "jump_host", "connect", "login");
    }

    private boolean hasSensitiveLiteralEvidence(String lowerLine) {
        return looksLikeRealSecretValue(lowerLine)
                || looksLikeHighEntropySecretAssignment(lowerLine)
                || lowerLine.matches(".*\\b[0-9]{16,19}\\b.*")
                || lowerLine.matches(".*\\b[1-9][0-9]{5}(18|19|20)[0-9]{2}[0-1][0-9][0-3][0-9][0-9x]\\b.*");
    }

    private boolean isPureCommentLine(String lowerLine) {
        String trimmed = lowerLine.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        return trimmed.startsWith("#")
                || trimmed.startsWith("//")
                || trimmed.startsWith("*")
                || trimmed.startsWith("/*")
                || trimmed.startsWith("<!--");
    }

    private boolean isTypeOrSchemaDeclaration(String lowerLine) {
        return lowerLine.matches(".*\\b(password|passwd|pwd|token|secret|auth_info|request_body|api_url)\\b\\s*:\\s*(string|str|number|boolean|object|array).*")
                || containsAny(lowerLine,
                "interface ", "type ", "schema", "字段", "字段名", "变量名", "参数名",
                "request_body", "auth_info", "api_url", "upload_url");
    }

    private boolean isSafeCredentialSource(String lowerLine) {
        return containsAny(lowerLine,
                "getenv(", "os.getenv", "os.environ.get", "os.environ[", "system.getenv", "process.env", "config.get", "secretsmanager",
                "secretmanager", "keyvault", "vault", "credentialsprovider", "fromenv",
                "环境变量", "密钥管理", "配置中心", "临时凭据");
    }

    private boolean isScannerDetectionLogic(Rule rule, String lowerFile, String lowerLine, String surrounding, LineContext context) {
        if (!isOperationalRule(rule)) {
            return false;
        }
        boolean scannerFile = containsAny(lowerFile,
                "scanner", "scan_", "-scan", "detect", "detector", "rule", "audit", "security_check",
                "plaintext-password-scan", "password-scan");
        boolean parserOrRuleLogic = containsAny(surrounding,
                "re.compile", "regex", "pattern", "match(", ".match", "matcher", "detect", "detector",
                "scan", "scanner", "rule", "value_upper", "value_lower", "key_lower", "secretkeyref",
                "configparser", "yaml.safe_load", "json.loads", "parse", "parser");
        boolean noRuntimeSink = !containsAny(surrounding,
                "logger.", "log.info", "console.log", "print(", "write(", "writefile", "json.dump",
                "requests.", "httpx.", "fetch(", "post(", "put(");
        return context.role == FileRole.IMPLEMENTATION && (scannerFile || parserOrRuleLogic) && noRuntimeSink;
    }

    private boolean isConfigurationKeyReferenceOnly(String lowerLine, String surrounding) {
        if (containsAny(surrounding, "open(", "read", "readfile", "fs.read", "cat ", "get-content",
                "download", "upload", "copy", "send_file")) {
            return false;
        }
        return containsAny(lowerLine,
                ".key", "entry.key", "secretkeyref.key", "valuefrom.secretkeyref.key",
                "object.keys", ".keys(", "key_lower", "args.keyword", "keyword", "configparser")
                || lowerLine.matches(".*\\b(key|keys|keyword)\\b\\s*(==|=|:|in).*");
    }

    private boolean isCredentialPlaceholderOrTemplate(String lowerLine) {
        return containsAny(lowerLine,
                "<token>", "<secret>", "<password>", "<cookie>", "<session>", "<jwt>",
                "{token}", "{password}", "{user}", "{username}", "{host}", "{database}", "{port}",
                "${token}", "${password}", "${user}", "${username}", "${host}", "${database}", "${port}",
                "${respdata.access_token}", "${resp_data.access_token}", "${response.access_token}",
                "your_token", "example_token", "dummy", "placeholder", "masked", "redacted",
                "my-client-secret", "client-secret", "my-secret", "your-secret", "your_secret",
                "replace_me", "changeme", "change_me", "todo-secret", "test-secret",
                "\"xxx\"", "'xxx'", "\"password\"", "'password'",
                "password=password", "passwd=passwd", "pwd=pwd", "user=user", "host=host", "port=port")
                || lowerLine.matches(".*\\b(password|passwd|pwd|token|secret)\\s*=\\s*(password|passwd|pwd|token|secret)\\b.*")
                || lowerLine.matches(".*://\\{user\\}:\\{password\\}@\\{host\\}.*")
                || lowerLine.matches(".*://\\$\\{user\\}:\\$\\{password\\}@\\$\\{host\\}.*")
                || lowerLine.matches(".*://\\{username\\}:\\{password\\}@\\{host\\}.*")
                || lowerLine.matches(".*://\\$\\{username\\}:\\$\\{password\\}@\\$\\{host\\}.*");
    }

    private boolean looksLikeHighEntropySecretAssignment(String lowerLine) {
        if (!containsAny(lowerLine, "secret", "token", "password", "passwd", "pwd", "private_key",
                "client_secret", "accesskey", "secretkey", "ak", "sk")) {
            return false;
        }
        if (isCredentialPlaceholderOrTemplate(lowerLine) || isSafeCredentialSource(lowerLine)
                || isRuntimeCredentialParameter(lowerLine)) {
            return false;
        }
        return lowerLine.matches(".*[:=]\\s*[\"'][a-z0-9._/-]{24,}[\"'].*")
                || lowerLine.matches(".*[:=]\\s*[\"'][a-f0-9]{16,}[\"'].*")
                || lowerLine.matches(".*[:=]\\s*[\"']eyj[a-z0-9_-]{10,}\\.[a-z0-9_-]{10,}.*");
    }

    private boolean isRoutineSessionHandling(String lowerLine, String surrounding) {
        if (looksLikeSensitiveOutputSink(surrounding) || looksLikeRealSecretValue(lowerLine)) {
            return false;
        }
        return containsAny(lowerLine,
                "authorization", "headers", "access_token", "refresh_token", "respdata.access_token",
                "resp_data[\"access_token\"]", "resp_data['access_token']", "set-cookie", "cookie")
                && containsAny(surrounding,
                "if ", "throw new error", "not found", "headers", "return {", "fetch(", "requests.",
                "axios", "client", "request");
    }

    private boolean isRuntimeCredentialParameter(String lowerLine) {
        return containsAny(lowerLine,
                "args.password", "args.passwd", "args.pwd", "args.token", "args.secret",
                "options.password", "options.token", "opts.password", "opts.token",
                "request.password", "request.token", "req.body.password", "req.body.token",
                "respdata.access_token", "resp_data['access_token']", "resp_data[\"access_token\"]",
                "response.access_token", "response.data.access_token",
                "config.password", "config.token", "settings.password", "settings.token",
                "credentials.password", "credentials.token")
                || lowerLine.matches(".*\\b(password|passwd|pwd|token|secret)\\s*=\\s*(args|opts|options|config|settings|request|response|resp_data|respdata|credentials)\\.[a-z0-9_]+.*")
                || lowerLine.matches(".*\\b(password|passwd|pwd|token|secret)\\s*=\\s*(args|opts|options|config|settings|request|response|resp_data|respdata|credentials)\\[[^\\]]+\\].*");
    }

    private boolean looksLikeSensitiveOutputSink(String text) {
        return containsAny(text,
                "logger.", "log.info", "log.debug", "console.log", "print(", "system.out.println",
                "write(", "writefile", "appendfile", "json.dump", "yaml.dump", "csv", "xlsx",
                "report", "markdown", "md.append", "allure", "har", "trace", "screenshot",
                "localstorage", "sessionstorage", "urlsearchparams", "querystring", "webhook", "email");
    }

    private boolean isGenericBusinessLabelOnly(String lowerLine, String surrounding) {
        if (containsAny(surrounding, "real_customer", "prod_dump", "production data", "raw customer",
                "id_card", "card_no", "account_no", "mobile", "phone", "dump", "export", "json.dump")) {
            return false;
        }
        return containsAny(lowerLine, "\"comment\"", "'''", "\"\"\"", "description", "desc", "label",
                "transaction flow", "trade flow");
    }

    private boolean isTeachingOrFixExample(String surrounding) {
        String text = surrounding == null ? "" : surrounding;
        return containsAny(text,
                "示例", "样例", "教学", "演示", "修复后", "正确示例", "错误示例", "对照代码",
                "for example", "example:", "example code", "sample:", "sample code", "demo:", "demo code",
                "teaching", "fixed example", "bad example", "good example", "should be", "should not");
    }

    private boolean looksLikeRealSecretValue(String lowerLine) {
        return lowerLine.matches(".*bearer\\s+[a-z0-9._-]{20,}.*")
                || lowerLine.matches(".*(access[_-]?token|refresh[_-]?token|session|cookie|jsessionid)\\s*[:=]\\s*[\"']?[a-z0-9._/-]{20,}.*")
                || lowerLine.matches(".*eyj[a-z0-9_-]{10,}\\.[a-z0-9_-]{10,}\\.[a-z0-9_-]{10,}.*");
    }

    private boolean isPlaceholderOnly(String lowerLine, Rule rule) {
        if (!(rule.id.startsWith("AUTH") || rule.id.startsWith("BANK") || rule.id.startsWith("DATA") || rule.id.startsWith("EXFIL") || rule.id.startsWith("REPORT"))) {
            return false;
        }
        return containsAny(lowerLine,
                "<token>", "<secret>", "<password>", "<cookie>", "<session>", "<jwt>",
                "{token}", "${token}", "your_token", "example_token", "dummy", "placeholder",
                "示例", "占位", "样例", "脱敏", "masked", "redacted");
    }

    private boolean isPositiveControlStatement(String lowerLine) {
        return containsAny(lowerLine,
                "必须", "应当", "应该", "需要", "要求", "确认", "暂停", "人工确认", "授权确认",
                "不得", "禁止", "避免", "防止", "校验", "检查", "审批", "审计",
                "must", "should", "shall", "require", "requires", "required", "confirm",
                "approval", "review", "validate", "verify", "prevent", "avoid");
    }

    private boolean isLikelyKeyPropertyAccess(String line) {
        String value = line == null ? "" : line;
        return value.matches(".*\\$?[A-Za-z_][A-Za-z0-9_]*\\.Key\\b.*")
                || value.matches(".*\\bObject\\.keys\\s*\\(.*")
                || value.matches(".*\\b[a-zA-Z_][A-Za-z0-9_]*\\.keys\\s*\\(.*");
    }

    private boolean isLikelyJsonEncodingImplementation(String line) {
        String value = line == null ? "" : line;
        String lower = value.toLowerCase(Locale.ROOT);
        return (lower.contains("jsonraw") || lower.contains("json_raw") || lower.contains("json raw"))
                && (lower.contains("replace(") || lower.contains("escapehtml") || lower.contains("</script"))
                || (lower.contains("escapehtml") && lower.contains("</script"));
    }

    private boolean isSecurityRequirementStatement(String lowerLine) {
        if (looksLikeNegativeImplementation(lowerLine)) {
            return false;
        }
        return containsAny(lowerLine,
                "安全要求", "安全规范", "规范要求", "合规要求", "治理要求", "验收要求",
                "要求", "说明", "描述", "建议", "禁止", "不得", "必须", "应当", "应该",
                "需", "需要", "防止", "避免", "校验", "检查", "脱敏", "审计", "审批",
                "授权", "人工确认", "日志脱敏", "密码复杂度", "文件类型", "文件大小",
                "目录遍历", "文件下载", "文件上传", "会话id", "cookie", "security requirement",
                "requirement", "policy", "must", "should", "shall", "do not", "prevent", "avoid");
    }

    private boolean looksLikeNegativeImplementation(String lowerLine) {
        return containsAny(lowerLine,
                "当前实现", "实际实现", "代码中", "脚本中", "发现", "存在漏洞", "存在风险",
                "未做", "未进行", "未实现", "缺少", "可被绕过", "可篡改", "可泄露",
                "未校验", "未脱敏", "未授权", "未审批", "明文输出", "明文存储", "真实token",
                "真实 token", "真实用户", "真实内网", "implementation lacks", "code lacks",
                "is vulnerable", "found risk");
    }

    private boolean isSafePolicyOrRuleCatalogLine(String lowerLine) {
        return containsAny(lowerLine,
                "不直接调用", "不调用", "不访问", "不要调用", "不要访问", "不得调用", "不得访问",
                "禁止调用", "禁止访问", "禁止记录", "禁止输出", "不能调用", "不能访问",
                "必须确认", "必须审批", "需要审批", "人工确认", "授权确认", "安全边界",
                "适用边界", "输出要求", "动态分析规则", "静态扫描重点", "检查项",
                "是否出现", "是否包含", "是否已经", "用于检查", "用于识别",
                "`dyn", "dyn001", "dyn002", "dyn003", "dyn004", "dyn005", "dyn006",
                "do not call", "do not access", "must not", "should not", "prohibit",
                "for checking", "check whether");
    }

    private boolean isAdversarialTestPrompt(String lowerLine) {
        return containsAny(lowerLine,
                "prompt", "payload", "test case", "security test", "red team", "jailbreak",
                "try to", "attempt to", "ask the target", "expected safe behavior",
                "尝试", "诱导", "测试", "安全测试", "对抗", "攻击样例", "失败信号",
                "目标 skill", "当前任务缺少数据", "补齐", "绕过审批", "隐藏操作");
    }

    private boolean isExampleOrTestContext(String lowerFile, String lowerLine) {
        return isAnalysisOrRequirementDoc(lowerFile)
                || containsAny(lowerFile, "/examples/", "/demo/", "/test/", "/tests/", "sample", "fixture")
                || containsAny(lowerLine,
                "example", "sample", "demo", "mock", "test", "placeholder", "dummy",
                "示例", "样例", "测试", "模拟", "占位", "脱敏", "<token>", "bearer <token>",
                "eventid", "taskids", "request_body", "api_url", "upload_url");
    }

    private boolean looksLikeRealReportLeak(String lowerLine) {
        if (containsAny(lowerLine,
                "example", "sample", "demo", "mock", "test", "placeholder", "dummy",
                "示例", "样例", "测试", "模拟", "占位", "脱敏", "unknown", "none", "null",
                "<token>", "{token}", "bearer <token>")) {
            return false;
        }
        return containsAny(lowerLine,
                "bearer ey", "authorization", "cookie", "session", "access_token", "refresh_token",
                "http://", "https://", "password", "passwd", "secret", "accountno", "cardno");
    }

    private boolean isNormativeText(String lowerLine) {
        return containsAny(lowerLine,
                "应", "应当", "应该", "不得", "禁止", "防止", "建议", "必须", "仅允许", "避免",
                "要求", "需", "需要", "不能", "不应", "采用", "通过", "检查", "校验",
                "should", "must", "shall", "recommend", "prevent", "require", "only allow");
    }

    private boolean isAnalysisOrRequirementDoc(String lowerFile) {
        return lowerFile.contains("/references/")
                || lowerFile.contains("requirement")
                || lowerFile.contains("需求")
                || lowerFile.contains("analysis")
                || lowerFile.contains("分析")
                || lowerFile.contains("report")
                || lowerFile.contains("报告");
    }

    private boolean isReportSummaryField(String lowerLine) {
        String trimmed = lowerLine.trim();
        return trimmed.startsWith("\"unmatched_note\"")
                || trimmed.startsWith("\"matched_")
                || trimmed.startsWith("\"evidence\"")
                || trimmed.startsWith("\"design\"")
                || trimmed.startsWith("\"recommendation\"")
                || trimmed.startsWith("\"summary\"")
                || trimmed.startsWith("\"risk_summary\"")
                || trimmed.startsWith("\"security_requirements\"");
    }

    private boolean isReportContextField(String lowerLine) {
        String trimmed = lowerLine.trim();
        return trimmed.startsWith("\"auth_info\"")
                || trimmed.startsWith("\"users\"")
                || trimmed.startsWith("\"user\"")
                || trimmed.startsWith("\"api_url\"")
                || trimmed.startsWith("\"upload_urls\"")
                || trimmed.startsWith("\"request_params\"")
                || trimmed.startsWith("\"request_body\"");
    }

    private boolean isReportRule(Rule rule) {
        return "BANK005".equals(rule.id)
                || "AUTH002".equals(rule.id)
                || "REPORT003".equals(rule.id)
                || "REPORT004".equals(rule.id)
                || "REPORT005".equals(rule.id);
    }

    private boolean looksLikeExecutableSnippet(String lowerLine) {
        String trimmed = lowerLine.trim();
        return trimmed.startsWith("```")
                || trimmed.startsWith("$ ")
                || trimmed.startsWith("curl ")
                || trimmed.startsWith("wget ")
                || trimmed.startsWith("sudo ")
                || trimmed.startsWith("rm ")
                || trimmed.startsWith("npm ")
                || trimmed.startsWith("pip ")
                || trimmed.startsWith("python ")
                || trimmed.startsWith("powershell ")
                || trimmed.startsWith("pwsh ")
                || trimmed.contains("os.system(")
                || trimmed.contains("subprocess.")
                || trimmed.contains("eval(")
                || trimmed.contains("exec(")
                || trimmed.contains("requests.")
                || trimmed.contains("httpx.")
                || trimmed.contains("fetch(")
                || trimmed.contains("jdbc:")
                || trimmed.contains("authorization:")
                || trimmed.contains("set-cookie:")
                || trimmed.contains("cookie:");
    }

    private void addGovernanceFindings(String skillName, Path skillMd, String content, SkillReport report) {
        String text = content.toLowerCase(Locale.ROOT);
        if (!containsAny(text, "适用环境", "禁止环境", "environment", "env", "scope", "边界", "权限", "permission")) {
            addSyntheticFinding(report, governanceFinding("META002", Severity.MEDIUM, skillName, skillMd,
                    "SKILL.md 缺少适用环境、禁止环境或权限边界说明",
                    "银行测试团队准入时应明确可用环境、禁止环境、权限要求和外联边界。"),
                    FileRole.SKILL_INSTRUCTION.name(), "needs_review", "kept_governance_gap",
                    "governance checklist check", "治理字段缺失会影响准入边界判断，需人工补充或确认。");
        }
        if (!containsAny(text, "验证", "自测", "测试", "verification", "verify", "expected", "assert", "断言")) {
            addSyntheticFinding(report, governanceFinding("META003", Severity.LOW, skillName, skillMd,
                    "SKILL.md 缺少验证方式或预期结果说明",
                    "为 Skill 增加自测步骤、预期输出、断言标准和失败处理方式。"),
                    FileRole.SKILL_INSTRUCTION.name(), "low_risk_notice", "kept_governance_gap",
                    "governance checklist check", "低风险治理提醒，不作为漏洞阻断。");
        }
        if (!containsAny(text, "清理", "回滚", "cleanup", "rollback", "恢复", "幂等")) {
            addSyntheticFinding(report, governanceFinding("META004", Severity.LOW, skillName, skillMd,
                    "SKILL.md 缺少清理、回滚或幂等说明",
                    "涉及造数、清数、报告生成、依赖安装的 Skill 应提供清理和回滚策略。"),
                    FileRole.SKILL_INSTRUCTION.name(), "low_risk_notice", "kept_governance_gap",
                    "governance checklist check", "低风险治理提醒，不作为漏洞阻断。");
        }
        if (!containsAny(text, "负责人", "维护人", "owner", "maintainer", "version", "版本", "变更记录", "changelog")) {
            addSyntheticFinding(report, governanceFinding("META005", Severity.LOW, skillName, skillMd,
                    "SKILL.md 缺少负责人、版本或变更记录",
                    "团队共享 Skill 应标明负责人、维护人、版本和变更记录，便于审计追踪。"),
                    FileRole.SKILL_INSTRUCTION.name(), "low_risk_notice", "kept_governance_gap",
                    "governance checklist check", "低风险治理提醒，不作为漏洞阻断。");
        }
        if (!containsAny(text, "输入", "输出", "input", "output", "参数", "产物")) {
            addSyntheticFinding(report, governanceFinding("META006", Severity.LOW, skillName, skillMd,
                    "SKILL.md 缺少输入输出说明",
                    "明确 Skill 读取什么、生成什么、写入哪里，降低误用和数据泄露风险。"),
                    FileRole.SKILL_INSTRUCTION.name(), "low_risk_notice", "kept_governance_gap",
                    "governance checklist check", "低风险治理提醒，不作为漏洞阻断。");
        }
    }

    private void addBehaviorMismatchFindings(String skillName, Path skillMd, String content, SkillReport report) {
        if (content == null || content.trim().isEmpty() || skillMd == null) {
            return;
        }
        String text = content.toLowerCase(Locale.ROOT);
        boolean claimsPassive = containsAny(text, "不执行", "不会执行", "只生成", "仅生成", "只提供建议", "不会访问网络", "no execution", "read-only");
        boolean hasActiveRisk = report.findings.stream().anyMatch(f ->
                f.category.contains("command")
                        || f.category.contains("network")
                        || f.category.contains("data_exfiltration")
                        || f.category.contains("dependency")
                        || f.category.contains("database")
                        || f.category.contains("production"));
        if (claimsPassive && hasActiveRisk) {
            addSyntheticFinding(report, new Finding(
                    "META007", "skill_governance", Severity.HIGH, skillName, skillMd, 0,
                    "Skill 文档声明偏静态或只生成内容，但实际文件中存在执行、外联、数据库或依赖风险",
                    "SKILL.md", "修正文档与行为不一致的问题；准入评审应以实际脚本能力为准。", 0.86),
                    FileRole.SKILL_INSTRUCTION.name(), "confirmed", "kept_behavior_mismatch",
                    "governance consistency check", "文档声明与实际能力不一致，保留为准入阻断候选。");
        }
        boolean declaresNetwork = containsAny(text, "网络", "外联", "http", "接口", "api", "requests", "fetch", "curl");
        boolean hasNetwork = report.findings.stream().anyMatch(f ->
                f.category.contains("network") || f.category.contains("data_exfiltration") || f.category.contains("external_notification"));
        if (!declaresNetwork && hasNetwork) {
            addSyntheticFinding(report, new Finding(
                    "META008", "skill_intent_consistency", Severity.HIGH, skillName, skillMd, 0,
                    "Skill 文档未声明网络或接口访问能力，但实际文件中存在外联或接口调用风险",
                    "SKILL.md", "补充外联目的、域名范围、数据字段、审批边界和 mock/隔离环境说明。", 0.82,
                    "A.I.G Agent Skill 审计思想：意图一致性", "consistency", "probable", "static", true, true),
                    FileRole.SKILL_INSTRUCTION.name(), "probable", "kept_behavior_mismatch",
                    "governance consistency check", "发现实际外联能力但 SKILL.md 未声明边界，保留为准入阻断候选。");
        }
        boolean declaresSensitiveOutput = containsAny(text, "脱敏", "敏感", "token", "cookie", "用户", "账号", "报告");
        boolean hasSensitiveReport = report.findings.stream().anyMatch(f ->
                f.category.contains("report") || f.category.contains("privacy") || f.category.contains("identifier"));
        if (!declaresSensitiveOutput && hasSensitiveReport) {
            addSyntheticFinding(report, new Finding(
                    "META009", "skill_output_contract", Severity.MEDIUM, skillName, skillMd, 0,
                    "Skill 文档未说明报告或输出脱敏策略，但扫描发现敏感输出或报告残留风险",
                    "SKILL.md", "明确输出文件、报告字段、脱敏策略、保存位置和可流转范围。", 0.78,
                    "A.I.G Agent Skill 审计思想：输出契约", "consistency", "probable", "static", false, true),
                    FileRole.SKILL_INSTRUCTION.name(), "needs_review", "kept_behavior_mismatch",
                    "governance consistency check", "发现报告/敏感输出风险但文档缺少脱敏契约，需人工确认。");
        }
        boolean declaresCleanup = containsAny(text, "清理", "回滚", "cleanup", "rollback", "幂等", "恢复");
        boolean hasMutation = report.findings.stream().anyMatch(f ->
                f.category.contains("deletion") || f.category.contains("database") || f.category.contains("dependency") || f.category.contains("ci_mutation"));
        if (!declaresCleanup && hasMutation) {
            addSyntheticFinding(report, new Finding(
                    "META010", "skill_operational_safety", Severity.HIGH, skillName, skillMd, 0,
                    "Skill 存在删除、数据库、依赖或流水线变更风险，但缺少清理、回滚或幂等说明",
                    "SKILL.md", "补充临时目录范围、事务回滚、依赖隔离、失败现场保留和恢复步骤。", 0.84,
                    "行内研发安全规范：测试流程与回归复核", "consistency", "probable", "static", true, true),
                    FileRole.SKILL_INSTRUCTION.name(), "probable", "kept_behavior_mismatch",
                    "governance consistency check", "存在变更类能力但缺少回滚/幂等说明，保留为准入阻断候选。");
        }
    }

    private Finding governanceFinding(String id, Severity severity, String skillName, Path file, String message, String recommendation) {
        return new Finding(id, "skill_governance", severity, skillName, file, 0, message, "SKILL.md", recommendation, 0.72);
    }

    private boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String readFile(Path file) {
        try {
            return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private enum FileRole {
        IMPLEMENTATION,
        SKILL_INSTRUCTION,
        RULE_DEFINITION,
        SECURITY_REQUIREMENT,
        TEST_CASE,
        EXAMPLE_DOC,
        ANALYSIS_REPORT,
        TEMPLATE,
        UNKNOWN
    }

    private static final class LineContext {
        final FileRole role;
        final boolean inCodeFence;
        final String previousLine;
        final String nextLine;
        final String statementType;

        LineContext(FileRole role, boolean inCodeFence, String previousLine, String nextLine, String statementType) {
            this.role = role == null ? FileRole.UNKNOWN : role;
            this.inCodeFence = inCodeFence;
            this.previousLine = previousLine == null ? "" : previousLine;
            this.nextLine = nextLine == null ? "" : nextLine;
            this.statementType = statementType == null || statementType.trim().isEmpty() ? "unknown" : statementType;
        }
    }
}
