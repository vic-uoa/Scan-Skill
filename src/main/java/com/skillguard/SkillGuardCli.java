package com.skillguard;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class SkillGuardCli {
    private SkillGuardCli() {
    }

    public static void main(String[] args) {
        int code = run(args);
        if (code != 0) {
            System.exit(code);
        }
    }

    static int run(String[] args) {
        if (args.length == 0 || args[0].equals("--help") || args[0].equals("-h")) {
            printHelp();
            return 0;
        }
        if (!args[0].equals("scan") && !args[0].equals("dynamic-plan") && !args[0].equals("dynamic-report")) {
            System.err.println("未知命令: " + args[0]);
            printHelp();
            return 2;
        }

        Path target = null;
        String format = "console";
        String mode = args[0].equals("dynamic-plan") ? "dynamic-plan" : args[0].equals("dynamic-report") ? "dynamic-report" : "static";
        Path output = null;
        Severity failOn = null;
        String skillName = null;
        boolean reviewMode = false;
        boolean llmMode = false;

        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            if ("--mode".equals(arg)) {
                    if (!hasValue(args, i)) {
                        System.err.println(arg + " 需要一个参数值");
                        return 2;
                    }
                    i++;
                    mode = args[i].toLowerCase();
                    if (!mode.equals("static") && !mode.equals("dynamic-plan") && !mode.equals("dynamic-report")) {
                        System.err.println("--mode 只能是 static、dynamic-plan 或 dynamic-report");
                        return 2;
                    }
            } else if ("--format".equals(arg)) {
                    if (!hasValue(args, i)) {
                        System.err.println(arg + " 需要一个参数值");
                        return 2;
                    }
                    i++;
                    format = args[i].toLowerCase();
                    if (!format.equals("console") && !format.equals("json") && !format.equals("html") && !format.equals("pdf")) {
                        System.err.println("--format 只能是 console、json、html 或 pdf");
                        return 2;
                    }
            } else if ("--output".equals(arg) || "-o".equals(arg)) {
                    if (!hasValue(args, i)) {
                        System.err.println(arg + " 需要一个参数值");
                        return 2;
                    }
                    i++;
                    output = Paths.get(args[i]);
            } else if ("--fail-on".equals(arg)) {
                    if (!hasValue(args, i)) {
                        System.err.println(arg + " 需要一个参数值");
                        return 2;
                    }
                    i++;
                    try {
                        failOn = Severity.parse(args[i]);
                    } catch (IllegalArgumentException e) {
                        System.err.println(e.getMessage());
                        return 2;
                    }
            } else if ("--skill".equals(arg)) {
                    if (!hasValue(args, i)) {
                        System.err.println(arg + " 需要一个参数值");
                        return 2;
                    }
                    i++;
                    skillName = args[i];
            } else if ("--review".equals(arg)) {
                    reviewMode = true;
            } else if ("--LLM".equals(arg) || "--llm".equals(arg)) {
                    llmMode = true;
            } else if ("--help".equals(arg) || "-h".equals(arg)) {
                    printHelp();
                    return 0;
            } else {
                    if (arg.startsWith("-")) {
                        System.err.println("未知选项: " + arg);
                        return 2;
                    }
                    if (target != null) {
                        System.err.println("一次只能指定一个扫描路径。");
                        return 2;
                    }
                    target = Paths.get(arg);
            }
        }

        if (target == null) {
            target = Paths.get("skills");
        }

        try {
            if (!"static".equals(mode)) {
                DynamicScanner dynamicScanner = new DynamicScanner();
                DynamicScanResult result = "dynamic-plan".equals(mode)
                        ? dynamicScanner.createPlan(target)
                        : dynamicScanner.analyzeTranscript(target, skillName);
                writeDynamic(result, format, output);
                if (failOn != null && result.riskLevel().rank() >= failOn.rank()) {
                    return 1;
                }
                return 0;
            }

            LlmConfig llmConfig = null;
            if (llmMode) {
                try {
                    llmConfig = LlmConfigDialog.configure(LlmConfig.defaultPath());
                } catch (IOException e) {
                    System.err.println("请先正确连接模型");
                    return 2;
                }
                if (llmConfig == null) {
                    System.err.println("请先正确连接模型");
                    return 2;
                }
            }

            SkillScanner scanner = new SkillScanner(BuiltinRules.all());
            ScanSummary summary = scanner.scan(target);
            if (llmMode) {
                LlmRemediationService.apply(summary, llmConfig);
            }
            String report;
            if ("json".equals(format)) {
                report = ReportWriter.json(summary);
            } else if ("html".equals(format)) {
                report = ReportWriter.html(summary, reviewMode, llmMode);
            } else if ("pdf".equals(format)) {
                report = "";
            } else {
                report = ReportWriter.console(summary);
            }
            if (output != null) {
                ensureParentDirectory(output);
                if (format.equals("pdf")) {
                    Files.write(output, ReportWriter.pdf(summary));
                } else {
                    Files.write(output, report.getBytes(StandardCharsets.UTF_8));
                }
                System.out.println("报告已写入 " + output);
                if (!format.equals("console")) {
                    System.out.println("风险: " + summary.riskLevel() + "，问题数量: " + summary.totalFindings());
                }
            } else {
                if (format.equals("pdf")) {
                    System.err.println("PDF 格式必须通过 --output 指定输出文件。");
                    return 2;
                }
                System.out.print(report);
            }

            if (failOn != null && summary.riskLevel().rank() >= failOn.rank()) {
                return 1;
            }
            return 0;
        } catch (IOException e) {
            System.err.println("扫描失败: " + e.getMessage());
            return 2;
        }
    }

    private static void writeDynamic(DynamicScanResult result, String format, Path output) throws IOException {
        String report;
        if ("json".equals(format)) {
            report = ReportWriter.dynamicJson(result);
        } else if ("html".equals(format)) {
            report = ReportWriter.dynamicHtml(result);
        } else if ("pdf".equals(format)) {
            report = "";
        } else {
            report = ReportWriter.dynamicJson(result);
        }
        if (output != null) {
            ensureParentDirectory(output);
            if (format.equals("pdf")) {
                Files.write(output, ReportWriter.dynamicPdf(result));
            } else {
                Files.write(output, report.getBytes(StandardCharsets.UTF_8));
            }
            System.out.println("报告已写入 " + output);
            System.out.println("风险: " + result.riskLevel() + "，问题数量: " + result.findings.size());
        } else {
            if (format.equals("pdf")) {
                System.err.println("PDF 格式必须通过 --output 指定输出文件。");
                throw new IOException("PDF output path required");
            }
            System.out.print(report);
        }
    }

    private static void ensureParentDirectory(Path output) throws IOException {
        Path parent = output.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    private static boolean hasValue(String[] args, int index) {
        return index + 1 < args.length && !args[index + 1].startsWith("-");
    }

    private static void printHelp() {
        System.out.println(
                "SkillGuard - Agent Skill 安全扫描工具\n\n"
                        + "用法:\n"
                        + "  java -jar dist/skillguard.jar scan [path] [options]\n\n"
                        + "动态扫描:\n"
                        + "  java -jar dist/skillguard.jar scan [skill-path] --mode dynamic-plan --format html -o dynamic-plan.html\n"
                        + "  java -jar dist/skillguard.jar scan [transcript.txt] --mode dynamic-report --skill skillName --format html -o dynamic-report.html\n"
                        + "  java -jar dist/skillguard.jar dynamic-plan [skill-path] --format json -o prompts.json\n"
                        + "  java -jar dist/skillguard.jar dynamic-report [transcript.txt] --skill skillName --format pdf -o dynamic.pdf\n\n"
                        + "选项:\n"
                        + "  --mode static|dynamic-plan|dynamic-report\n"
                        + "                           扫描模式，默认为 static\n"
                        + "  --format console|json|html|pdf\n"
                        + "                           输出格式，默认为 console\n"
                        + "  -o, --output FILE        将报告写入文件\n"
                        + "  --skill NAME             dynamic-report 模式下指定目标 Skill 名称\n"
                        + "  --fail-on LEVEL          当风险等级达到 LEVEL 时返回退出码 1\n"
                        + "                           LEVEL: critical, high, medium, low\n"
                        + "  --review                 在 HTML 报告中启用维护者复核工具\n"
                        + "  --LLM                    打开模型配置窗口，连接成功后生成 AI 整改建议\n"
                        + "  -h, --help               显示帮助\n\n"
                        + "如果省略 path，默认扫描 ./skills。"
        );
    }
}


