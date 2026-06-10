package com.skillguard;

public final class SecurityKnowledgeBase {
    private SecurityKnowledgeBase() {
    }

    public static String ruleDomain(String ruleId) {
        String id = safe(ruleId);
        if (id.startsWith("FILE")) return "敏感文件、工作区边界与本地凭据";
        if (id.startsWith("EXFIL") || id.startsWith("NET")) return "外联、SSRF 与数据外传";
        if (id.startsWith("CMD") || id.startsWith("INJ") || id.startsWith("OBF")) return "命令执行与动态代码";
        if (id.startsWith("DEP") || id.startsWith("SUPPLY") || id.startsWith("CI")) return "依赖、供应链与流水线";
        if (id.startsWith("AUTH") || id.startsWith("NORM-AUTH") || id.startsWith("NORM-SESSION")) return "认证、会话与密钥";
        if (id.startsWith("BANK") || id.startsWith("DATA") || id.startsWith("NORM-DATA")) return "数据安全与金融个人信息";
        if (id.startsWith("ENV")) return "环境隔离与生产误用";
        if (id.startsWith("DB")) return "数据库与账务变更";
        if (id.startsWith("APISEC") || id.startsWith("NORM-API")) return "接口安全测试";
        if (id.startsWith("NORM-ACCESS") || id.startsWith("NORM-TRADE")) return "访问控制与业务流程";
        if (id.startsWith("NORM-INPUT") || id.startsWith("NORM-FILE") || id.startsWith("NORM-CLIENT")) return "输入输出、文件与客户端安全";
        if (id.startsWith("REPORT") || id.startsWith("NORM-LOG")) return "报告、日志与缺陷流转";
        if (id.startsWith("TEST") || id.startsWith("AUTO")) return "自动化测试执行边界";
        if (id.startsWith("AGENT") || id.startsWith("META")) return "Agent Skill 治理";
        return "通用安全";
    }

    public static String evidenceGate(String ruleId) {
        String id = safe(ruleId);
        if (id.startsWith("FILE")) {
            return "需要看到真实敏感路径、工作区外路径或文件读写/下载/解压语境；仅 key/keys 字段名不算证据。";
        }
        if (id.startsWith("AUTH")) {
            return "需要真实 token、cookie、账号、密钥或认证上下文，并存在日志、报告、外发、落盘或硬编码语境；仅 Authorization 头构造、access_token 判空、从响应对象取 token、<token>/dummy/masked 默认降噪。";
        }
        if (id.startsWith("EXFIL") || id.startsWith("NET")) {
            return "需要外联调用与敏感数据、凭据、报告、上传、下载、内网地址或用户可控 URL 语境同现；仅 urlopen(req)、fetch(url)、requests.get(url) 等网络能力封装不算证据。";
        }
        if (id.startsWith("CMD") || id.startsWith("INJ")) {
            return "需要真实执行 API、危险命令或用户输入进入执行点；正则 exec、解析器 exec、静态分发表 eval 和固定安全清理默认降级。";
        }
        if (id.startsWith("BANK") || id.startsWith("DATA")) {
            return "需要真实客户标识、金融个人信息、生产数据、批量导出或报告泄露字段；扫描器进度日志、字段标签、comment/description 不算证据。";
        }
        if (id.startsWith("ENV")) {
            return "需要真实生产、跳板、VPN、代理配置、连接动作或绕过准入语境；依赖锁 integrity、VPN 文案标签和配置枚举不算证据。";
        }
        if (id.startsWith("DB")) {
            return "需要 SQL 写操作、账务字段、生产/核心连接、无 where 或缺少事务保护证据。";
        }
        if (id.startsWith("NORM-")) {
            return "需要当前实现缺失、可绕过、未校验、明文等负向证据；安全需求说明默认不算漏洞。";
        }
        if (id.startsWith("REPORT")) {
            return "需要报告产物保留真实域名、用户号、事件号、taskId、请求体或认证材料。";
        }
        if (id.startsWith("META")) {
            return "需要 SKILL.md 缺少治理字段或文档行为不一致；默认作为治理提醒。";
        }
        if (id.startsWith("AGENT")) {
            return "需要 Skill 指令、工具输出、外部内容或模型输出被用于高风险执行、绕过确认或覆盖安全策略的语境。";
        }
        if (id.startsWith("SUPPLY")) {
            return "需要远程脚本执行、未固定远程依赖、latest/main/master 或缺少校验摘要的供应链语境。";
        }
        return "需要命中文本满足规则最低证据门槛，并通过文件角色与上下文复核。";
    }

    public static String falsePositiveType(String decisionReason) {
        String reason = safe(decisionReason);
        if (reason.contains("filtered_dependency_lock_metadata")) {
            return "dependency/generated metadata false positive";
        }
        if (reason.contains("filtered_dependency_or_label_metadata")) {
            return "依赖元数据或配置标签误报";
        }
        if (reason.contains("filtered_scanner_status_log")) {
            return "scanner status/log text false positive";
        }
        if (reason.contains("filtered_scanner_credential_corpus")) {
            return "扫描器候选凭据字典误报";
        }
        if (reason.contains("filtered_document_metadata_log")) {
            return "文档元数据日志误报";
        }
        if (reason.contains("filtered_parser_regex_exec")) {
            return "parser or regex execution false positive";
        }
        if (reason.contains("filtered_static_dispatch_eval")) {
            return "静态分发或解析器执行误报";
        }
        if (reason.contains("filtered_config_label_only")) {
            return "configuration label false positive";
        }
        if (reason.contains("filtered_capability_only_network")) {
            return "网络能力封装误报";
        }
        if (reason.contains("filtered_routine_auth_flow")) {
            return "常规认证流程误报";
        }
        if (reason.contains("filtered_missing_required_evidence")
                || reason.contains("filtered_missing_sensitive_file_evidence")) {
            return "missing minimum evidence gate";
        }
        if (reason.contains("filtered_security_requirement") || reason.contains("filtered_positive_control_statement")) {
            return "文档型/正向规范误报";
        }
        if (reason.contains("filtered_rule_definition")) {
            return "规则库自扫描误报";
        }
        if (reason.contains("filtered_scanner_detection_logic")) {
            return "扫描器/规则检测逻辑误报";
        }
        if (reason.contains("filtered_config_or_env_reference")) {
            return "环境变量或配置引用误报";
        }
        if (reason.contains("filtered_test_payload")) {
            return "测试 Payload 误报";
        }
        if (reason.contains("filtered_example_doc")) {
            return "示例/教学误报";
        }
        if (reason.contains("filtered_example_placeholder")) {
            return "示例占位符凭据误报";
        }
        if (reason.contains("filtered_type_or_property_access")) {
            return "类型声明或字段名误报";
        }
        if (reason.contains("filtered_generic_business_label")) {
            return "通用业务标签误报";
        }
        if (reason.contains("filtered_report_summary")) {
            return "报告摘要误报";
        }
        if (reason.contains("kept_report_context_leak")) {
            return "报告上下文泄露专项";
        }
        if (reason.contains("filtered_safe_implementation") || reason.contains("downgraded_mitigation_present")) {
            return "安全实现或缓解措施误报";
        }
        if (reason.contains("filtered_placeholder_value") || reason.contains("filtered_report_context_placeholder")) {
            return "占位符/脱敏样例误报";
        }
        if (reason.contains("downgraded_capability_only")) {
            return "能力声明降级";
        }
        return "";
    }

    public static String recommendationFamily(String ruleId) {
        String id = safe(ruleId);
        if (id.startsWith("AUTH") || id.startsWith("REPORT")) return "脱敏、密钥托管与报告流转控制";
        if (id.startsWith("FILE") || id.startsWith("ENV")) return "工作区边界、环境隔离、最小权限与受控配置";
        if (id.startsWith("CMD") || id.startsWith("INJ") || id.startsWith("DB")) return "参数化、白名单、事务与回滚";
        if (id.startsWith("SUPPLY")) return "依赖锁定、来源固定、校验摘要与供应链隔离";
        if (id.startsWith("AGENT")) return "指令隔离、工具输出降权、人工确认与最小代理权限";
        if (id.startsWith("APISEC") || id.startsWith("NORM-ACCESS") || id.startsWith("NORM-TRADE")) return "测试授权、范围限制、幂等与审批";
        if (id.startsWith("NORM-") || id.startsWith("META")) return "Skill 治理、规范补齐与可审计说明";
        return "按规则证据进行最小化整改";
    }

    public static String admissionPolicy(Finding finding) {
        if (finding == null) {
            return "";
        }
        if (finding.blocking && ("confirmed".equals(finding.decision) || "probable".equals(finding.decision))
                && finding.severity.rank() >= Severity.HIGH.rank()) {
            return "阻断项：确认/高度疑似且达到高危以上，默认阻断准入。";
        }
        if ("needs_review".equals(finding.decision)) {
            return "复核项：证据不足或上下文有歧义，需人工确认后进入最终报告。";
        }
        if ("low_risk_notice".equals(finding.decision)) {
            return "提醒项：不阻断准入，作为治理或规范优化建议。";
        }
        if ("false_positive".equals(finding.decision)) {
            return "误报项：不进入最终有效报告，可沉淀到误报样本库。";
        }
        return "有效问题：进入报告并按风险等级安排整改。";
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
