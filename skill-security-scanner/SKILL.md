---
name: skill-security-scanner
description: Scans skills for static and dynamic security risks and generates review reports.
---

# Skill 安全检测助手

## 用途

本 Skill 面向内网 devagent 场景，用于对团队 Skill 做安全准入检查。它内置离线 Java 扫描器，可执行静态扫描、生成动态安全测试 Prompt 包，并由 devagent 自动记录目标 Skill 的动态回答，再生成动态复核报告。

## 在 devagent 中的角色

当前 Skill 是“安全检测 Skill”，不是被检测的业务 Skill。使用链路应理解为：

```text
用户
  -> devagent 识别并调用 skill-security-scanner
  -> skill-security-scanner 使用内置 bin/skillguard.jar 生成扫描报告或动态测试计划
  -> 目标业务 Skill 作为被扫描/被测试对象
```

执行命令时优先以本 Skill 目录为工作目录，使用：

```powershell
java -jar bin\skillguard.jar ...
```

如果 devagent 的工作目录不是本 Skill 目录，则必须把 `bin\skillguard.jar` 替换为本 Skill 的绝对路径，例如：

```powershell
java -jar C:\path\to\skill-security-scanner\bin\skillguard.jar ...
```

## 默认交互方式

本 Skill 支持三类稳定工作流：

1. 静态扫描：直接扫描目标 `skills` 目录并输出报告。
2. 动态测试计划：为目标 Skill 生成动态安全测试 Prompt 包。
3. 动态结果分析：在用户或 devagent 已提供对话记录后，生成动态复核报告。

如果用户只说“动态扫描并输出报告”，应先说明当前 devagent 是否支持自动调用目标业务 Skill。若当前环境不能自动驱动目标 Skill 或不能可靠记录 transcript，则不要声称已经完成完整动态扫描，应优先生成动态测试计划，并提示用户执行后再用 `dynamic-report` 分析记录。

用户示例：

```text
请使用 skill-security-scanner，对 C:\xxx\skills 做静态安全扫描，输出 HTML 报告。
```

```text
请使用 skill-security-scanner，为 C:\xxx\skills\doc-security-mapper 生成动态安全测试计划，输出 HTML。
```

```text
请使用 skill-security-scanner，根据 C:\xxx\dynamic-transcript.txt 生成动态安全扫描报告，输出 HTML。
```

## 适用边界

- 仅用于本地、内网、隔离测试目录。
- 不直接调用生产环境、真实账号、真实 token、真实客户数据。
- Jar 本身不直接调用 devagent API，也不能自行和目标业务 Skill 对话。
- 动态扫描的自动化程度取决于 devagent 是否允许当前 Skill 驱动目标业务 Skill。
- 如果 devagent 无法自动执行第二步，动态扫描应降级为“生成测试计划 + 人工或 devagent 执行 + 回填 transcript + 生成报告”。
- 输出报告可选 HTML、JSON、PDF。

## 静态扫描

当用户要求扫描某个 `skills` 目录时，运行：

```powershell
java -jar bin\skillguard.jar scan <skills目录> --format html --output reports\static-report.html
```

可选格式：

```powershell
--format html
--format json
--format pdf
```

静态扫描重点检查：

- 生产环境、核心系统、数据库连接。
- Token、Cookie、Session、用户号、柜员号、客户敏感信息。
- 命令执行、外联、下载后执行、依赖污染。
- 文件上传下载、路径遍历、报告/日志脱敏。
- `SKILL.md` 与脚本行为是否一致。
- 行内研发安全规范覆盖情况。

### 静态重检闭环

静态扫描报告生成后，应优先对 `CRITICAL`、`HIGH`、`needs_review` 和低置信度问题做二次语义复核。复核时不要只看命中关键词，应回到命中文件和相邻上下文，判断：

- 命中内容是可执行行为、配置、样例数据，还是规范说明、风险提示、报告字段或变量名。
- 是否存在真实文件读取、网络请求、命令执行、认证凭据、生产地址、审批绕过等可复现行为。
- 是否已经有明确的防护语义，例如“必须审批”“暂停确认”“仅沙箱”“脱敏后输出”“mock 环境”。
- 建议是否能落地；最终报告中的建议应结合规则预设建议和当前代码上下文，给出简短修改例子。

推荐闭环：

```text
静态扫描 JSON/HTML
  -> devagent 读取每条 finding 的 file、line、evidence、rule_id
  -> 回到源码相邻上下文做二次判断
  -> 将结论更新为 confirmed / probable / false_positive / low_risk_notice
  -> 为 confirmed/probable 问题生成带示例的最终建议
  -> 输出最终静态报告
```

复核结论建议口径：

- `confirmed`：证据能对应真实高风险行为或敏感数据。
- `probable`：证据不足以直接确认，但存在实现路径或缺少必要边界说明。
- `false_positive`：变量名、字段名、规范说明、已防护代码或报告文本导致的关键词误报。
- `low_risk_notice`：不是漏洞，但属于编码规范、输出规范或可维护性提醒。

### 静态分析判定原则

静态扫描仍以规则库和正则作为第一层召回，不应丢弃已有规则体系；但规则命中只代表“可能需要看一眼”，不能直接等同于漏洞。命中后必须经过上下文证据门禁：

- 文件角色：区分真实实现代码、`SKILL.md` 指令、规范文档、测试 payload、示例/demo、分析报告、Prompt 模板。
- 语义方向：区分“禁止/必须/建议/检查是否存在”等安全正向语义，和“当前实现未校验/真实 token/明文输出”等负向实现语义。
- 证据强度：认证类问题需要看到凭据赋值、明文值、认证调用或输出；命令类问题需要看到可执行路径和输入来源；报告类问题需要看到真实 token、cookie、URL 或业务对象，而不是字段名。
- 测试隔离：`security-tests`、`payloads`、`examples`、`demo`、`report.json` 中的 payload、样例 token、接口样例默认不作为真实漏洞，只能作为测试材料或低风险提醒。
- 实现优先：`.py/.js/.ts/.ps1` 等真实脚本中的文件读取、外联、命令执行、数据库变更仍按原规则重点检查。

常见误报模式应优先按上下文降级或过滤：

- 文档型误报：`requirements.md`、`security_rules.md`、`SKILL.md` 中的“禁止、必须、建议、检查是否存在”是规范说明，不是漏洞证据。
- 测试样例误报：`payload`、`XSS`、`SQL 注入`、`../`、`shell.php` 如果位于 `security-tests`、`payloads`、`examples`、`demo`，默认是测试材料。
- 安全框架误报：`BCryptPasswordEncoder`、`Argon2`、`PBKDF2`、`validatePassword()` 通常是安全实现或教学代码，不应仅因 `password` 命中。
- 类型声明误报：`userPassword: string`、`request_body`、`auth_info`、`api_url` 这类字段名或类型定义，不等同于真实凭据或接口泄露。
- 推荐实践误报：`getenv`、`process.env`、`config.get`、`secrets manager` 通常是推荐的配置读取方式，不能和硬编码密码混同。
- 报告字段误报：`report.json/result.json` 中的字段名、脱敏值、占位 token、接口样例，只有出现真实 token/cookie/URL/业务对象时才作为问题。
- 修复示例误报：标题或注释含 `fixed/example/demo/正确示例/修复后` 时，应优先按示例处理，除非同时存在真实执行路径。

建议编写可被扫描器准确理解的材料：

- 安全规范使用“必须/禁止/不得/仅允许/需要审批”等明确正向词。
- 攻击样例放到 `security-tests/payloads`、`examples` 或 `demo` 目录。
- 示例 token 写成 `<token>`、`<masked>`、`example_token`，不要写成像真实 JWT 或真实 cookie 的值。
- 修复前后对照代码在标题或注释中标明“错误示例/修复后示例”。
- 真实实现代码和安全研究材料分目录保存，避免同一文件混合生产逻辑和 payload 字典。

最终报告应尽量输出证据链，而不是只输出“命中了什么词”。推荐证据链格式：

```text
规则: <rule_id>
文件角色: implementation / skill_instruction / security_requirement / test_case / analysis_report
命中内容: <evidence>
上下文判断: 实现行为 / 文档说明 / 测试 payload / 示例数据 / 报告字段
结论: confirmed / probable / false_positive / low_risk_notice
建议: 结合规则预设建议和当前上下文给出修改例子
```

## 动态扫描计划

当用户只要求生成动态测试计划时，运行：

```powershell
java -jar bin\skillguard.jar scan <目标Skill目录> --mode dynamic-plan --format html --output reports\dynamic-plan.html
```

生成的 Prompt 包覆盖：

- 正常能力测试。
- 越权诱导测试。
- 隐私泄露测试。
- 工具滥用测试。
- 策略绕过测试。
- 业务风险测试。

## 动态扫描工作流

动态扫描分为两个阶段，避免在 devagent 不支持自动调用目标 Skill 时产生误导：

### 阶段 1：生成动态测试计划

```powershell
java -jar bin\skillguard.jar scan <目标Skill目录> --mode dynamic-plan --format html --output reports\dynamic-plan.html
```

也可以输出 JSON，便于 devagent 或人工逐条执行：

```powershell
java -jar bin\skillguard.jar scan <目标Skill目录> --mode dynamic-plan --format json --output reports\.skillguard-work\<Skill名称>\dynamic-plan.json
```

### 阶段 2：根据执行记录生成动态报告

当动态 Prompt 已经由用户或 devagent 在目标业务 Skill 中执行，并形成对话记录后，保存为：

```text
reports\.skillguard-work\<Skill名称>\dynamic-transcript.txt
```

推荐记录格式：

```text
目标 Skill: <Skill名称>
测试时间: <时间>

[DT-001] 正常能力测试
Prompt:
...
目标 Skill 回答:
...
工具调用摘要:
...
观察结论:
...
```

然后生成动态报告：

```powershell
java -jar bin\skillguard.jar scan reports\.skillguard-work\<Skill名称>\dynamic-transcript.txt --mode dynamic-report --skill <Skill名称> --format html --output reports\<Skill名称>-dynamic-report.html
```

如需 JSON 或 PDF，将 `--format` 改为 `json` 或 `pdf`，并指定对应输出文件。

## 动态结果分析

如果用户已经提供现成的 devagent 对话记录，可以跳过自动执行 Prompt，直接运行：

```powershell
java -jar bin\skillguard.jar scan <devagent对话记录.txt> --mode dynamic-report --skill <Skill名称> --format html --output reports\dynamic-report.html
```

动态分析规则包括：

- `DYN001` 未声明高危行为。
- `DYN002` 敏感信息或认证上下文输出。
- `DYN003` 生产环境或真实链路倾向。
- `DYN004` 绕过审批或隐藏操作。
- `DYN005` 描述与动态行为不一致。
- `DYN006` 需要人工复核。

## 输出要求

报告中应关注：

- `CRITICAL` 和 `HIGH` 默认作为准入阻断项。
- `needs_review` 需要人工复核，不能直接忽略。
- 动态扫描只能作为辅助评估，不能替代安全测试和代码评审。
- 报告流转前必须确认其中没有真实 token、cookie、用户号、客户数据或生产地址。
