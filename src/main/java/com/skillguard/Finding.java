package com.skillguard;

import java.nio.file.Path;

public final class Finding {
    public final String ruleId;
    public final String category;
    public final Severity severity;
    public final String skillName;
    public final Path file;
    public final int line;
    public final String message;
    public final String evidence;
    public final String recommendation;
    public final double confidence;
    public final String normSource;
    public final String evidenceType;
    public final String reviewStatus;
    public final String scanMode;
    public final boolean blocking;
    public final boolean manualReview;
    public final String fileRole;
    public final String decision;
    public final String decisionReason;
    public final String contextExcerpt;
    public final String whyMatched;
    public final String whyKept;
    public final String statementType;
    public final String evidenceSummary;

    public Finding(
            String ruleId,
            String category,
            Severity severity,
            String skillName,
            Path file,
            int line,
            String message,
            String evidence,
            String recommendation,
            double confidence) {
        this(ruleId, category, severity, skillName, file, line, message, evidence, recommendation,
                confidence, defaultNormSource(ruleId), "regex", defaultReviewStatus(confidence), "static",
                severity.rank() >= Severity.HIGH.rank(), confidence < 0.8,
                "UNKNOWN", defaultReviewStatus(confidence), "default_rule_match", "", "", "");
    }

    public Finding(
            String ruleId,
            String category,
            Severity severity,
            String skillName,
            Path file,
            int line,
            String message,
            String evidence,
            String recommendation,
            double confidence,
            String normSource,
            String evidenceType,
            String reviewStatus,
            String scanMode,
            boolean blocking,
            boolean manualReview) {
        this(ruleId, category, severity, skillName, file, line, message, evidence, recommendation,
                confidence, normSource, evidenceType, reviewStatus, scanMode, blocking, manualReview,
                "UNKNOWN", reviewStatus, "default_rule_match", "", "", "");
    }

    public Finding(
            String ruleId,
            String category,
            Severity severity,
            String skillName,
            Path file,
            int line,
            String message,
            String evidence,
            String recommendation,
            double confidence,
            String normSource,
            String evidenceType,
            String reviewStatus,
            String scanMode,
            boolean blocking,
            boolean manualReview,
            String fileRole,
            String decision,
            String decisionReason,
            String contextExcerpt,
            String whyMatched,
            String whyKept) {
        this(ruleId, category, severity, skillName, file, line, message, evidence, recommendation,
                confidence, normSource, evidenceType, reviewStatus, scanMode, blocking, manualReview,
                fileRole, decision, decisionReason, contextExcerpt, whyMatched, whyKept, "", "");
    }

    public Finding(
            String ruleId,
            String category,
            Severity severity,
            String skillName,
            Path file,
            int line,
            String message,
            String evidence,
            String recommendation,
            double confidence,
            String normSource,
            String evidenceType,
            String reviewStatus,
            String scanMode,
            boolean blocking,
            boolean manualReview,
            String fileRole,
            String decision,
            String decisionReason,
            String contextExcerpt,
            String whyMatched,
            String whyKept,
            String statementType,
            String evidenceSummary) {
        this.ruleId = ruleId;
        this.category = category;
        this.severity = severity;
        this.skillName = skillName;
        this.file = file;
        this.line = line;
        this.message = message;
        this.evidence = evidence == null ? "" : evidence.trim();
        this.recommendation = enhanceRecommendation(ruleId, recommendation, fileRole, decision, statementType);
        this.confidence = confidence;
        this.normSource = normSource == null ? "" : normSource;
        this.evidenceType = evidenceType == null ? "regex" : evidenceType;
        this.reviewStatus = reviewStatus == null ? defaultReviewStatus(confidence) : reviewStatus;
        this.scanMode = scanMode == null ? "static" : scanMode;
        this.blocking = blocking;
        this.manualReview = manualReview;
        this.fileRole = fileRole == null ? "UNKNOWN" : fileRole;
        this.decision = decision == null ? this.reviewStatus : decision;
        this.decisionReason = decisionReason == null ? "" : decisionReason;
        this.contextExcerpt = contextExcerpt == null ? "" : contextExcerpt.trim();
        this.whyMatched = whyMatched == null ? "" : whyMatched.trim();
        this.whyKept = whyKept == null ? "" : whyKept.trim();
        this.statementType = statementType == null ? "" : statementType.trim();
        this.evidenceSummary = evidenceSummary == null ? "" : evidenceSummary.trim();
    }

    public Finding withRecommendation(String updatedRecommendation) {
        return new Finding(ruleId, category, severity, skillName, file, line, message, evidence,
                updatedRecommendation, confidence, normSource, evidenceType, reviewStatus, scanMode,
                blocking, manualReview, fileRole, decision, decisionReason, contextExcerpt,
                whyMatched, whyKept, statementType, evidenceSummary);
    }

    private static String defaultNormSource(String ruleId) {
        if (ruleId == null) {
            return "";
        }
        if (ruleId.startsWith("NORM-")) {
            return "行内研发安全规范";
        }
        if (ruleId.startsWith("DYN")) {
            return "动态 Skill 安全测试";
        }
        return "SkillGuard 内置规则";
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

    private static String enhanceRecommendation(String ruleId, String recommendation, String fileRole, String decision, String statementType) {
        String base = recommendation == null ? "" : recommendation;
        String contextual = contextualRecommendation(ruleId, fileRole, decision, statementType);
        String example = exampleFor(ruleId, fileRole, statementType);
        StringBuilder result = new StringBuilder(base);
        if (!contextual.isEmpty() && !base.contains(contextual)) {
            if (result.length() > 0) {
                result.append('\n');
            }
            result.append("场景化建议: ").append(contextual);
        }
        if (example.isEmpty() || result.toString().contains("示例:")) {
            return result.toString();
        }
        return result.append("\n示例: ").append(example).toString();
    }

    private static String contextualRecommendation(String ruleId, String fileRole, String decision, String statementType) {
        String id = ruleId == null ? "" : ruleId;
        String role = fileRole == null ? "" : fileRole;
        String type = statementType == null ? "" : statementType;
        String status = decision == null ? "" : decision;
        if ("UNKNOWN".equals(role) && type.trim().isEmpty()) {
            return "";
        }
        if ("low_risk_notice".equals(status)) {
            return "作为治理提醒处理，优先补充边界、验证、回滚和负责人信息，不按漏洞阻断。";
        }
        if ("needs_review".equals(status)) {
            return "先人工确认该证据是否来自真实实现、真实报告或真实环境；若只是说明、样例或占位符，应沉淀为误报样本。";
        }
        if ("FILE002".equals(id) && "file_operation".equals(type)) {
            return "将读写、下载、解压和删除路径统一收敛到 workspace 或受控临时目录，并在执行前做 resolve/realpath 校验。";
        }
        if ("FILE001".equals(id) && "IMPLEMENTATION".equals(role)) {
            return "删除默认读取宿主机凭据的逻辑，改为显式参数传入临时测试凭据，并拒绝 HOME、.ssh、云凭据目录。";
        }
        if ("NET002".equals(id)) {
            return "增加 URL 白名单和私网/云元数据地址拦截，默认禁止访问 localhost、内网段和 169.254.169.254。";
        }
        if ("EXFIL001".equals(id)) {
            return "先列出外发字段清单，删除 secret/token/.env 等敏感数据，必要时只发送脱敏摘要到受控 mock 服务。";
        }
        if ("SUPPLY002".equals(id)) {
            return "把远程依赖固定到版本、tag 或 commit，并增加 lockfile 或 SHA256 校验，避免 main/master/latest 漂移。";
        }
        if ("AGENT006".equals(id)) {
            return "禁止直接执行模型或工具输出；改为解析为受限动作枚举，再通过白名单映射和人工确认执行。";
        }
        if ("BANK001".equals(id)) {
            return "将生产地址、核心库连接和账号信息移出 Skill，由外部审批后的配置通道注入，并默认指向隔离环境。";
        }
        if ("BANK002".equals(id)) {
            return "替换为合成测试数据或一致性脱敏值，避免身份证、卡号、手机号、客户号在代码、样例或报告中沉淀。";
        }
        if ("BANK004".equals(id) || id.startsWith("REPORT")) {
            return "在报告/日志写入前统一调用脱敏函数，隐藏 token、cookie、用户号、事件号、taskId、request_body 和真实域名。";
        }
        if (id.startsWith("META")) {
            return "补齐 SKILL.md 的 owner、version、输入输出、适用环境、禁止环境、验证方式和清理回滚说明。";
        }
        return "";
    }

    private static String exampleFor(String ruleId, String fileRole, String statementType) {
        if (ruleId == null) {
            return "";
        }
        if ("FILE001".equals(ruleId)) {
            return "不要读取 ~/.ssh/id_rsa、*.pem、*.key；改为让用户显式传入测试凭据路径，并校验路径位于临时测试目录。";
        }
        if ("FILE002".equals(ruleId)) {
            return "Path target = workspace.resolve(userName).normalize(); if (!target.startsWith(workspace)) reject。";
        }
        if ("NET002".equals(ruleId)) {
            return "只允许访问 allowlist 中的域名，并拒绝 localhost、10.0.0.0/8、172.16.0.0/12、192.168.0.0/16、169.254.169.254。";
        }
        if ("SUPPLY002".equals(ruleId)) {
            return "把 git+https://repo/tool.git@main 改成 git+https://repo/tool.git@<commit-sha>，并记录 SHA256 或 lockfile。";
        }
        if ("AGENT006".equals(ruleId)) {
            return "不要 exec(tool_output)；改为 allowedActions.get(parsedAction).run(fixedArgs)，执行前展示给用户确认。";
        }
        if ("NORM-ACCESS002".equals(ruleId)) {
            return "将“直接发布生产”改成“若需发布，先暂停并要求审批单号/授权确认；缺失时只输出待办建议”。";
        }
        if ("NORM-INPUT002".equals(ruleId)) {
            return "HTML 报告写入前先做 HTML 转义，并在嵌入 JSON 时额外转义 </script> 等脚本闭合片段。";
        }
        if ("NORM-AUTH001".equals(ruleId)) {
            return "若只是教学或修复示例，请在标题或注释中标明 example/demo/fixed；真实实现应使用 BCrypt/Argon2/PBKDF2 等口令哈希，并避免沉淀 123456/admin 等样例口令。";
        }
        if ("AUTH002".equals(ruleId)) {
            return "真实 token/cookie 应从报告和日志中移除；样例请写成 Bearer <token>、SESSION=<masked>，并标明 example/test。";
        }
        if ("APISEC004".equals(ruleId)) {
            return "攻击 payload 应放入 security-tests/payloads 或 examples 并标明仅用于授权测试；生产实现中不要把 payload 拼接到真实 URL 或请求体。";
        }
        if ("INJ001".equals(ruleId)) {
            return "示例代码请标明 demo/example；真实实现避免 eval/exec/new Function，改用白名单映射或固定函数表。";
        }
        if ("NORM-DATA001".equals(ruleId)) {
            return "若文档是在描述脱敏要求，请写成“必须脱敏/禁止明文”；真实实现应在输出、报告、日志和导出前统一调用脱敏函数。";
        }
        if ("NORM-DATA002".equals(ruleId)) {
            return "规范文档请使用“禁止 MD5/SHA1/DES”；真实代码中密码或 token 场景改用 BCrypt/Argon2/PBKDF2 或合规加密组件。";
        }
        if (ruleId.startsWith("NORM-FILE")) {
            return "安全规范请标明 requirement；真实上传/下载实现应校验扩展名、MIME、大小、路径白名单，并隔离存储目录。";
        }
        if (ruleId.startsWith("AUTH")) {
            return "用环境变量或临时密钥引用替代明文 token/password，并在报告、日志、样例中只展示 masked 值。";
        }
        if (ruleId.startsWith("NET") || ruleId.startsWith("EXFIL")) {
            return "把允许访问的域名、字段和脱敏策略写入 SKILL.md，默认使用 mock/沙箱地址。";
        }
        if (ruleId.startsWith("CMD")) {
            return "使用参数化 API 和白名单路径；需要清理时只允许删除当前 workspace 下明确的临时目录。";
        }
        if (ruleId.startsWith("DB")) {
            return "所有写操作绑定测试库、where 条件、事务回滚和影响行数确认，禁止默认指向生产连接。";
        }
        if (ruleId.startsWith("REPORT")) {
            return "输出报告前统一脱敏 request/response/header/cookie/token，只保留字段类别和风险结论。";
        }
        return "";
    }
}
