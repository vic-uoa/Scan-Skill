# 误报类型与证据门槛

本文件用于沉淀 SkillGuard 静态扫描中的典型误报模式和保留问题的证据要求。规则优化时应优先复用本文件的分类，避免继续增加纯关键词命中。

## 典型误报类型

| 类型 | 典型场景 | 默认处理 |
|:---|:---|:---|
| 文档型误报 | `references/`、`requirements`、安全规范中描述“应防止/禁止/必须校验” | 过滤，除非存在明确“当前实现缺失”证据 |
| 规则库自扫描误报 | 扫描器规则文件、规则目录中包含高危关键词 | 过滤，除非该文件本身包含可执行脚本 |
| 测试 Payload 误报 | `payloads/`、`security-tests/`、`fuzz/` 中的 SQLi/XSS/SSRF 样例 | 降级或标记 `needs_review` |
| 示例/教学误报 | demo、example、sample、错误示例、修复示例 | 降级，要求标题或上下文说明用途 |
| 类型声明误报 | `password: string`、`request_body`、`api_url`、`auth_info` 等字段名 | 过滤，除非字段值是真实敏感值 |
| 报告摘要误报 | `summary`、`recommendation`、`unmatched_note` 中出现安全关键词 | 过滤，除非包含真实认证材料或可复现请求 |
| 报告上下文泄露 | `auth_info`、`users`、`api_url`、`request_params`、`request_body` 包含真实值 | 保留为报告泄露风险，建议脱敏 |
| 安全实现误报 | BCrypt、Argon2、PBKDF2、参数化 SQL、HTML escape、secure_filename | 降级或过滤 |
| 正向规范误报 | “禁止记录敏感日志”“不得使用弱算法”“必须服务端校验” | 过滤，除非描述当前系统未落实 |
| 常规认证流程误报 | `Authorization: Bearer ${resp.access_token}`、`access_token not found`、token 判空 | 过滤，除非 token 被记录、外发、落盘或写入报告 |
| 网络能力封装误报 | `urlopen(req)`、`fetch(url)`、`requests.get(url)` 等通用请求封装 | 过滤或降级，除非出现真实外联目标、用户可控 URL 或敏感数据同现 |
| 依赖元数据/配置标签误报 | `package-lock.json` integrity、`VPN: VPN代理`、配置枚举标签 | 过滤，除非存在真实连接、绕过准入或生产跳板配置 |
| 静态分发/解析器误报 | 正则 `exec`、解析器 `exec`、`eval("expect_" + name)(data)` 静态函数分发 | 过滤或降级，除非用户输入直接进入执行点 |
| 扫描器状态/字典误报 | `Scanning plaintext passwords...`、弱口令检测字典、风险匹配逻辑 | 过滤，除非输出真实凭据或用于真实登录尝试 |
| 文档元数据日志误报 | DOCX/OOXML `RSID`、revision、edit session、style id 等元数据日志 | 过滤，除非同上下文含真实客户标识或敏感认证材料 |

## 保留问题的证据门槛

| 风险类型 | 最低证据要求 |
|:---|:---|
| 敏感文件访问 | 文件路径指向 `.env`、`.ssh`、`.pem`、`.key`、云凭据，且位于实现代码或可执行片段 |
| Token/Cookie 泄露 | 出现真实格式 token/JWT/Cookie，而不是 `<token>`、`masked`、`dummy` |
| 常规认证/会话处理 | 仅构造请求头、检查 token 是否存在、从响应读取 token 不算泄露；必须看到日志/报告/外发/落盘 |
| 命令执行 | 出现真实执行 API；若为固定命令可降级，若拼接用户输入则保留高危 |
| 动态代码执行 | 必须看到用户输入、外部数据或不可信配置进入 `eval/exec/new Function/subprocess`；正则/解析器/静态分发默认降级 |
| 外联/数据外传 | 外联调用与敏感数据、报告、凭据、客户数据存在同一上下文 |
| 网络请求能力 | 仅有请求库调用不构成外联风险；需同时出现真实目标、内网地址、用户可控 URL、报告/凭据/客户数据 |
| 文件上传/下载 | 实现代码中缺少类型、大小、路径、隔离、执行权限等控制证据 |
| 报告泄露 | 报告产物中保留真实域名、用户号、事件号、taskId、请求体、认证上下文 |
| 业务越权 | 文档或脚本描述当前实现存在未授权、资源归属未校验、角色校验缺失 |
| 数据库/账务变更 | 出现真实 SQL 写操作、生产连接、账务字段、无事务/无 where 证据 |
| 环境/跳板配置 | 必须看到真实 VPN、跳板机、代理、生产连接或自动绕过准入；依赖锁摘要和配置标签不算证据 |

## 判定状态建议

| 状态 | 使用条件 |
|:---|:---|
| `confirmed` | 证据完整，风险行为明确，默认进入整改 |
| `probable` | 高度疑似，但仍需负责人确认上下文 |
| `needs_review` | 上下文不足，不能直接判定真风险或误报 |
| `false_positive` | 明确属于文档、示例、规则库、占位符或安全实现误报 |
| `low_risk_notice` | 不构成漏洞，但属于治理或规范提醒 |

## 代码判定原因映射

以下 `decision_reason` 会出现在 JSON/HTML 报告中，维护者导出误报样本后应优先按这些原因统计。

| `decision_reason` | 对应误报/降级类型 | 默认含义 |
|:---|:---|:---|
| `filtered_security_requirement` | 文档型误报 | 安全需求、规范或正向要求，不代表当前实现存在漏洞 |
| `filtered_positive_control_statement` | 正向规范误报 | “禁止/必须/应当”等控制要求，不作为风险实现 |
| `filtered_rule_definition` | 规则库自扫描误报 | 扫描器规则或安全库文件中的关键词 |
| `filtered_test_payload` | 测试 Payload 误报 | 授权测试样例、payload 字典或 fuzz 样本 |
| `filtered_example_doc` | 示例/教学误报 | demo、sample、example 或修复说明 |
| `filtered_type_or_property_access` | 类型声明误报 | 字段名、属性名、类型声明，不含真实敏感值 |
| `filtered_report_summary` | 报告摘要误报 | summary、recommendation、unmatched_note 等结论字段 |
| `kept_report_context_leak` | 报告上下文泄露专项 | 报告中保留真实域名、用户号、请求体或认证上下文 |
| `filtered_safe_implementation` | 安全实现误报 | 命中的是 BCrypt、Argon2、escapeHtml、参数化 SQL 等缓解实现 |
| `downgraded_mitigation_present` | 缓解措施降级 | 同上下文中已有明显安全控制，降为提醒 |
| `filtered_placeholder_value` | 占位符误报 | `<token>`、`dummy`、`masked`、`example` 等非真实值 |
| `filtered_routine_auth_flow` | 常规认证流程误报 | 仅处理 access_token/Authorization/Cookie 的正常业务流程，未输出或泄露 |
| `filtered_capability_only_network` | 网络能力封装误报 | 只体现请求能力，没有真实目标、敏感数据或用户可控外联 |
| `filtered_dependency_or_label_metadata` | 依赖元数据或配置标签误报 | 依赖锁摘要、VPN/代理字段标签、配置枚举不代表真实环境风险 |
| `filtered_static_dispatch_eval` | 静态分发或解析器执行误报 | 正则/解析器/静态函数映射中的 exec/eval，不是执行不可信代码 |
| `filtered_scanner_status_log` | 扫描器状态日志误报 | 进度输出或扫描说明，不是敏感日志泄露 |
| `filtered_scanner_credential_corpus` | 扫描器候选凭据字典误报 | 弱口令扫描器内置候选词，不是真实高权限账号 |
| `filtered_document_metadata_log` | 文档元数据日志误报 | DOCX/OOXML 修订、RSID、编辑会话等元数据，不是真实客户数据 |
| `filtered_scanner_detection_logic` | 扫描器/规则检测逻辑误报 | 扫描器识别 password/token/payload 的逻辑，不代表自身泄露 |
| `filtered_parser_regex_exec` | 解析器或正则执行误报 | 正则匹配 API 或解析器内部执行，不等于命令执行 |
| `filtered_config_label_only` | 配置标签误报 | 配置项名称或枚举值，不代表真实敏感配置 |
| `filtered_config_or_env_reference` | 环境变量或配置引用误报 | 从环境变量/配置读取参数，不等于硬编码或本地文件读取 |
| `filtered_generic_business_label` | 通用业务标签误报 | `交易流水`、`客户`、`comment` 等标签，不是真实客户数据 |
| `downgraded_capability_only` | 能力声明降级 | 描述“支持/用于检测某风险”，但不是实际执行逻辑 |
| `downgraded_instruction_or_rule_file` | 指令/规则文件降级 | 命中来自 SKILL.md 或规则文档，默认进入复核而非阻断 |

## 优化原则

- 召回规则可以宽，确认规则必须严。
- `NORM-*` 规则在安全需求库、规则库、模板文件中默认不作为运行时风险。
- Markdown 中的规范性语句不直接构成漏洞，除非出现“当前实现未做/缺少/可被绕过”等负向实现证据。
- 报告产物中的内部接口和用户标识不应简单过滤，应作为“报告流转脱敏风险”单独判断。
- 每次新增规则，应同时补充一个真实风险样例和一个误报样例。
