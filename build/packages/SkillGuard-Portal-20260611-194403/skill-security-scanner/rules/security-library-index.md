# SkillGuard 安全库索引

本文件是扫描器安全库的维护入口，用于把规则、误报治理、整改建议和准入策略拆成可维护的稳定材料。代码仍以内置 Java 规则为主，避免一次性引入外部规则系统；新增规则时必须同步补充本索引或相邻文档。

## 安全库四层结构

| 层级 | 文件 | 作用 |
|:---|:---|:---|
| 规则库 | `internal-security-rules.md`、`src/main/java/com/skillguard/BuiltinRules.java` | 定义风险域、规则 ID、命中语义和扫描召回范围 |
| 误报类型库 | `false-positive-taxonomy.md` | 定义高频误报类型、过滤/降级原因和证据门槛 |
| 整改建议库 | `remediation-library.md` | 按风险域提供建议模板、最小修复动作和示例 |
| 准入策略库 | `admission-policy.md` | 定义 PASS、PASS_WITH_WARNINGS、NEEDS_REVIEW、BLOCKED 的判定依据 |

## 规则域索引

| 规则域 | 规则前缀 | 最低证据门槛 | 默认准入倾向 |
|:---|:---|:---|:---|
| 敏感文件、工作区边界与本地凭据 | `FILE*` | 真实敏感路径、工作区外路径或读取/写入/下载/解压语境；字段名 `key/keys` 不算证据 | confirmed/high+ 阻断 |
| 外联、SSRF 与数据外传 | `NET*`、`EXFIL*` | 外联调用与敏感数据、凭据、报告上传、下载、内网地址或云元数据地址同上下文 | confirmed/probable high+ 阻断 |
| 命令执行与动态代码 | `CMD*`、`INJ*`、`OBF*` | 真实执行 API、危险命令或动态执行；示例/固定安全命令降级 | high+ 阻断或复核 |
| 依赖、供应链与流水线 | `DEP*`、`SUPPLY*`、`CI*` | 全局安装、远程脚本执行、未固定远程依赖、跳过扫描、流水线变更 | high+ 阻断或复核 |
| 认证、会话与密钥 | `AUTH*`、`NORM-AUTH*`、`NORM-SESSION*` | 真实 token、cookie、账号、密钥或认证上下文；占位符过滤 | high+ 阻断 |
| 数据安全与金融个人信息 | `BANK*`、`DATA*`、`NORM-DATA*` | 真实客户标识、金融个人信息、生产数据、批量导出或报告泄露 | high+ 阻断或复核 |
| 环境隔离与生产误用 | `ENV*`、`BANK001` | 生产环境、核心系统、代理/证书/堡垒机等受控入口证据 | critical/high 阻断 |
| 数据库与账务变更 | `DB*`、`BANK003` | SQL 写操作、账务字段、无 where、无事务或生产连接 | critical 阻断 |
| 接口安全测试 | `APISEC*`、`NORM-API*` | 授权测试范围、速率、账号、payload 或交易重放语境 | 复核优先 |
| 访问控制与业务流程 | `NORM-ACCESS*`、`NORM-TRADE*` | 当前实现未鉴权、可绕过、可篡改、缺少幂等等负向证据 | 复核或阻断 |
| 输入输出、文件与客户端安全 | `NORM-INPUT*`、`NORM-FILE*`、`NORM-CLIENT*` | 当前实现缺少校验/编码/隔离；安全需求文档默认过滤 | 复核优先 |
| 报告、日志与缺陷流转 | `REPORT*`、`NORM-LOG*`、`BANK004/005` | 报告产物含真实域名、用户号、请求参数、token、cookie | 脱敏专项复核 |
| 自动化测试执行边界 | `TEST*`、`AUTO*` | 删除产物、真实会话、无限请求、真实业务动作 | high+ 复核或阻断 |
| Agent Skill 治理 | `AGENT*`、`META*` | 绕过策略、隐藏操作、远程指令、不可信工具输出、过度代理权限、模型/工具输出直接执行、缺少 owner/version/rollback/io 等治理字段 | 治理提醒、复核或阻断 |

## 外部安全知识映射

本安全库吸收 OWASP LLM Top 10、OWASP ASVS/WSTG 等稳定风险域，但落地为本地离线静态规则：

- Prompt Injection / 不可信内容覆盖指令：映射到 `AGENT001`、`AGENT004`。
- Sensitive Information Disclosure：映射到 `AUTH*`、`BANK*`、`DATA*`、`REPORT*`。
- Excessive Agency：映射到 `AGENT005`、`AUTO001`、`CMD*`。
- Supply Chain Vulnerabilities：映射到 `SUPPLY*`、`DEP*`、`CI*`。
- SSRF / 内网访问：映射到 `NET002`。
- 文件上传/下载、路径穿越与工作区边界：映射到 `FILE002`、`NORM-FILE*`。

## 新增规则准入要求

新增规则时必须满足以下要求：

1. 说明规则域和风险边界。
2. 给出最低证据门槛，避免纯关键词命中。
3. 给出至少一个真实风险样例和一个误报样例。
4. 明确默认状态：`confirmed`、`probable`、`needs_review`、`low_risk_notice`。
5. 明确是否 `blocking`，以及在什么条件下阻断。
6. 补充整改建议和一个短修复示例。

## 当前落地方式

- Java 内置规则仍是主扫描来源。
- `SecurityKnowledgeBase.java` 提供规则域、证据门槛、误报类型、建议族和准入策略说明。
- JSON/HTML 报告会输出安全库字段，便于用户理解命中依据，也便于维护者统计误报来源。
