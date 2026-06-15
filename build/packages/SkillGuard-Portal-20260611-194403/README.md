# SkillGuard

SkillGuard 是一个面向本地 Agent Skill 的安全扫描与准入评估工具。它以 Java Jar 形式交付，不依赖 Maven、Gradle 或第三方运行时库，适合在测试团队本地、离线环境、CI 流水线和银行内网环境中使用。

本工具的目标不是证明一个 Skill 绝对安全，而是在安装、共享、上线到团队 Skill 仓库或接入自动化流程之前，提供一套可解释、可审计、可落地的静态风险扫描能力。

## 设计目标

SkillGuard 结合了两个方向：

- **安全预检**：参考 Skill 安全扫描工具的思路，对命令执行、敏感文件访问、外联、数据外传、依赖污染等风险进行静态识别。
- **量化评估**：参考 `darwin-skill` 的评测理念，不只输出问题列表，还给每个 Skill 计算安全分、结构分、测试适配分和综合分。

对银行测试团队来说，它重点关注：

- Skill 是否可能访问生产环境或核心系统。
- Skill 是否可能泄露客户敏感信息、测试账号、Token、Cookie。
- Skill 是否可能执行破坏性数据库或账务操作。
- Skill 是否可能绕过鉴权、安全策略、审批流程。
- Skill 是否具备可审计的说明、验证、回滚和负责人信息。

## 适用场景

- 团队统一管理 `skills/` 目录下的多个 Skill。
- 第三方或其他团队共享 Skill 的准入检查。
- 安全测试、接口测试、UI 自动化、造数清数、压测辅助类 Skill 的风险识别。
- CI/CD 中作为门禁工具使用。
- 输出 HTML/PDF 报告，用于测试负责人、安全测试同学或审计归档。

## 项目结构

```text
.
├── build.ps1                         # 构建脚本
├── dist/
│   └── skillguard.jar                # 构建后的可执行 Jar
├── src/main/java/com/skillguard/
│   ├── SkillGuardCli.java            # CLI 入口
│   ├── SkillScanner.java             # 扫描流程与 Skill 识别
│   ├── BuiltinRules.java             # 内置规则库
│   ├── ReportWriter.java             # console/json/html/pdf 报告生成
│   ├── Rule.java                     # 规则模型
│   ├── Finding.java                  # 问题模型
│   ├── SkillReport.java              # 单个 Skill 报告
│   ├── ScanSummary.java              # 扫描汇总
│   ├── Severity.java                 # 风险等级
│   ├── LlmConfig.java                # 本地模型配置
│   ├── LlmConfigDialog.java          # LLM 可视化配置窗口
│   ├── LlmClient.java                # OpenAI-compatible 模型调用
│   └── LlmRemediationService.java    # AI 个性化整改建议生成
└── examples/skills/                  # 示例 Skill
```

## 构建

当前项目不依赖 Maven 或 Gradle，只要求本机安装 JDK。

已适配 Java 8，构建产物为 class file version `52.0`，可在 `java 1.8.0_202` 这类环境中运行。

```powershell
.\build.ps1
```

构建产物：

```text
dist/skillguard.jar
```

如果在 IDEA 中运行，建议项目 SDK 选择 Java 8 或更高版本；如果使用更高版本 JDK 构建，脚本会自动使用 `--release 8` 输出 Java 8 兼容字节码。

## 快速开始

扫描默认的 `skills` 目录：

```powershell
java -jar dist\skillguard.jar scan
```

扫描指定目录：

```powershell
java -jar dist\skillguard.jar scan .\skills
```

扫描示例目录：

```powershell
java -jar dist\skillguard.jar scan examples\skills
```

## 命令说明

```text
java -jar dist\skillguard.jar scan [path] [options]
```

参数：

| 参数 | 说明 |
|:---|:---|
| `path` | 可选。要扫描的 Skill 目录或包含多个 Skill 的目录。省略时默认扫描 `./skills` |
| `--format console|json|html|pdf` | 输出格式，默认 `console` |
| `-o, --output FILE` | 将报告写入指定文件 |
| `--fail-on LEVEL` | 当整体风险达到指定等级时返回退出码 `1`，支持 `critical`、`high`、`medium`、`low` |
| `--review` | 维护者复核模式。HTML 报告会显示 raw/final/过滤统计、人工复核按钮和误报治理导出工具 |
| `--LLM` / `--llm` | AI 个性化整改建议模式。扫描前弹出模型配置窗口，连接成功后才继续生成报告 |
| `-h, --help` | 查看帮助 |

示例：

```powershell
java -jar dist\skillguard.jar scan .\skills --format html --output report.html
java -jar dist\skillguard.jar scan .\skills --format pdf --output report.pdf
java -jar dist\skillguard.jar scan .\skills --format json --output report.json
java -jar dist\skillguard.jar scan .\skills --fail-on high
java -jar dist\skillguard.jar scan .\skills --format html --output report-review.html --review
java -jar dist\skillguard.jar scan .\skills --format html --output report-ai.html --LLM
java -jar dist\skillguard.jar scan .\skills --format html --output report-ai-review.html --LLM --review
```

注意：PDF 是文件格式，必须通过 `--output` 指定输出路径。

## 报告模式

SkillGuard 当前把普通用户报告、维护者复核报告和 AI 整改建议报告拆成不同命令，避免把维护者工具暴露给普通报告使用者。

### 普通用户报告

```powershell
java -jar dist\skillguard.jar scan .\skills --format html --output report.html
```

普通 HTML 报告只展示安全准入需要的信息：整体风险、准入结论、问题数量、Skill 数量、Skill 评分概览和问题详情。筛选项只保留 Skill、风险等级和阻断项，适合直接交给 Skill 使用者、负责人或评审人员查看。

### 维护者复核报告

```powershell
java -jar dist\skillguard.jar scan .\skills --format html --output report-review.html --review
```

`--review` 用于扫描器维护者或安全测试负责人。该模式会显示 raw/final/过滤统计、确认状态、文件角色、误报原因、人工复核按钮、复核结论导出和误报样本导出，便于把本地复核结果沉淀为后续安全库和误报治理规则。

### AI 个性化整改建议报告

```powershell
java -jar dist\skillguard.jar scan .\skills --format html --output report-ai.html --LLM
```

`--LLM` 会在扫描前打开可视化模型配置窗口。用户需要填写或确认模型 API 地址、模型名称、API Key、temperature、max tokens、组织约束和可选请求体 JSON。点击“测试连接并保存”后，工具会先进行模型连通性校验；只有连接成功才继续扫描并生成报告。如果取消或连接失败，命令会输出 `请先正确连接模型` 并停止，不会继续生成 AI 报告。

模型配置会保存到本地配置文件，下一次使用 `--LLM` 时会再次弹出配置窗口，并自动带出上一次保存的内容，方便用户确认或修改。AI 只用于把已确认的问题建议改写为更具体的整改步骤，不参与风险判定、误报过滤或准入结论计算。

`--LLM` 可以和 `--review` 同时使用：

```powershell
java -jar dist\skillguard.jar scan .\skills --format html --output report-ai-review.html --LLM --review
```

## Skill 识别方式

SkillGuard 的识别逻辑：

- 如果传入目录本身包含 `SKILL.md`，按单个 Skill 扫描。
- 如果传入目录下有多个子目录包含 `SKILL.md`，每个子目录按一个 Skill 扫描。
- 如果传入目录下没有发现 `SKILL.md`，会将该目录作为一个待检查目录，并产生结构类风险。

扫描文件类型包括：

```text
.md .txt .py .js .ts .java .kt .go .rs
.sh .bash .ps1 .bat .cmd
.yml .yaml .json .toml .ini .properties .xml
```

默认排除：

```text
.git .idea .vscode node_modules target build dist
.venv venv __pycache__ .pytest_cache
```

基础扫描会在报告中记录扫描画像：

- `entry_file`：识别到的 Skill 入口文件，例如 `SKILL.md` 或 `skill.md`。
- `scan_scope`：扫描范围类型，例如标准 Skill、兼容小写入口或普通目录扫描。
- `files_scanned`：实际参与规则扫描的文件数。
- `files_skipped`：因扩展名、目录排除或非文本材料被跳过的文件数。
- `directories_skipped`：被跳过的目录数，例如 `build/`、`dist/`、`node_modules/`、`.git/`、测试报告产物目录等。

这样报告使用者能看清楚扫描器到底扫了哪些内容，也能发现是否把生成产物、依赖目录或非标准目录误纳入扫描范围。

## 输出格式

| 格式 | 适用场景 | 特点 |
|:---|:---|:---|
| `console` | 本地快速扫描 | 在终端展示汇总和问题明细 |
| `json` | CI、平台、二次开发 | 字段稳定，便于机器消费 |
| `html` | 团队评审、安全复核 | 可视化最完整，包含评分卡、风险分布、问题明细，并支持按 Skill 和风险等级筛选 |
| `pdf` | 审计归档、审批流转 | 表格化审计摘要，适合作为附件保存 |

生成报告示例：

```powershell
java -jar dist\skillguard.jar scan examples\skills --format html --output build\sample-report.html
java -jar dist\skillguard.jar scan examples\skills --format pdf --output build\sample-report.pdf
java -jar dist\skillguard.jar scan examples\skills --format json --output build\sample-report.json
```

普通 HTML 报告顶部提供三个面向用户的筛选项：

- 按 Skill 筛选：选择某个 Skill 后，同步过滤 Skill 评分概览和问题详情。
- 按风险等级筛选：只显示 `CRITICAL`、`HIGH`、`MEDIUM`、`LOW` 或 `INFO` 对应的问题。
- 按阻断项筛选：只查看会影响准入的阻断问题。
- 三个筛选可以组合使用，便于安全测试同学先看阻断问题，也便于 Skill 负责人只看自己负责的目录。

维护者使用 `--review` 生成 HTML 时，报告会额外显示确认状态、文件角色、误报原因、来源类型等治理筛选项。

报告中的问题位置默认使用相对 Skill 的路径，例如 `doc-security-mapper/security-analysis/report.json:51`，避免在团队流转报告时暴露本机用户名、完整桌面路径或沙箱路径。

HTML/JSON 报告中的每条问题还会补充安全库字段：

- `rule_domain`：规则所属安全域，例如认证、报告泄露、数据库变更。
- `evidence_gate`：该规则最低证据门槛，帮助判断为什么不是单纯关键词命中。
- `false_positive_type`：当命中与误报治理有关时，标记对应误报类型。
- `recommendation_family`：整改建议所属类别，便于统一治理。
- `admission_policy`：该问题对准入结论的影响说明。

安全库维护材料位于：

```text
skill-security-scanner/rules/security-library-index.md
skill-security-scanner/rules/false-positive-taxonomy.md
skill-security-scanner/rules/remediation-library.md
skill-security-scanner/rules/admission-policy.md
skill-security-scanner/rules/internal-security-rules.md
```

## 评分模型

每个 Skill 会计算四类分数：

| 分数 | 含义 |
|:---|:---|
| `safety_score` | 安全分。风险越多、风险越高，扣分越多 |
| `structure_score` | 结构分。关注是否有 `SKILL.md`、描述、章节、足够说明 |
| `test_fitness_score` | 测试适配分。关注验证、断言、报告、清理、隔离等测试要素 |
| `score` | 综合分 |

当前综合分权重：

```text
综合分 = 结构分 25% + 安全分 55% + 测试适配分 20%
```

风险等级：

| 等级 | 准入建议 |
|:---|:---|
| `CRITICAL` | 默认阻断，除非有正式例外审批 |
| `HIGH` | 建议阻断，需要安全测试或负责人复核 |
| `MEDIUM` | 进入整改计划，确认误报和业务必要性 |
| `LOW` | 发布前清理，保持可维护性和可审计性 |
| `INFO` | 未发现明显风险 |

## 内置规则体系

当前规则库位于：

```text
src/main/java/com/skillguard/BuiltinRules.java
```

规则并非只覆盖恶意代码，还覆盖测试团队在真实工作中容易引入的安全、合规和审计风险。

为了避免把安全需求文档、检查清单、分析报告中的“应当防止某类风险”直接判定为漏洞，扫描器加入了上下文降噪策略：

- 对 `references/`、`requirements`、`analysis`、`report` 等说明性文档，会优先识别其是否只是规范、需求或分析结论。
- 对 Markdown 中的“应、不得、必须、建议、防止、检查、校验、should、must”等规范性语句，不直接按运行时高危行为处理。
- 对 `<token>`、`<secret>`、`${token}`、`dummy`、`placeholder`、`示例`、`脱敏` 等占位或脱敏样例，会降低误报。
- 对路径类规则只在文件路径本身可体现风险时触发，例如流水线文件、密钥 fixture、环境配置文件。
- 对实际可执行片段仍会保留命中，例如 `curl`、`sudo`、`os.system`、`subprocess`、`requests`、`fetch`、`Authorization:`、`Cookie:`、`jdbc:` 等。

因此，SkillGuard 更接近“准入预检工具”，而不是简单的关键词搜索器。它会尽量区分“文档描述了某个安全要求”和“Skill 本身包含可执行风险”。

## 当前能力现状

| 能力 | 当前状态 | 已有能力 | 主要不足 | 下一步优化方向 |
|:---|:---|:---|:---|:---|
| 安全库能力 | 已扩展外部安全知识映射 | 内置规则库、行内规范规则域、安全需求库、误报类型库、整改建议库、准入策略库；已补充 Agent/LLM 安全、SSRF/内网访问、工作区逃逸、未固定远程依赖、过度代理权限等规则域 | 仍未引入外部 YAML/JSON 规则系统；误报样本库还需要持续从人工复核结果沉淀 | 继续用复核台账回灌误报样本，逐步把高频误报转成过滤/降级规则 |
| 基础扫描能力 | 已增强扫描画像 | 可发现 `SKILL.md` / `skill.md`，扫描单 Skill 或多 Skill，支持多类文本和代码文件；报告输出入口文件、扫描范围、扫描/跳过文件数、跳过目录数 | 单文件扫描和复杂 monorepo 的边界仍需要更多样例；外部自定义排除规则暂未开放 | 增加目录识别回归样例，后续支持可配置 include/exclude 和 baseline 扫描 |
| 风险召回能力 | 已增强证据门槛和重叠收缩 | 覆盖文件、命令、外联、鉴权、TLS 绕过、生产环境、金融个人信息、报告泄露、数据库、CI、治理等风险域；raw 仍宽召回，final 增加证据确认，并对同一位置的泛化规则与专门规则做主规则优先收缩 | 规则仍以内置启发式为主，缺少外部可配置规则和更多行业样本校准 | 继续扩充真实风险/误报样例，将 AUTH/BANK/DATA/REPORT 等高噪声规则沉淀为可测试的召回-确认规则 |
| 上下文理解能力 | 已增强语句画像 | 已识别实现代码、Skill 指令、安全需求、规则库、测试样例、分析报告、模板等文件角色；并输出 `statement_type` 区分赋值、可执行语句、JSON 字段、报告字段、Markdown 表格/列表、规范文本 | 仍未做完整 AST、跨文件调用链或污点分析 | 继续按高频误报样本补充语句类型和上下文窗口识别 |
| 证据确认能力 | 已增强证据摘要 | 已能识别占位 token、示例、规范语句、安全实现、真实敏感值等；输出 `decision_reason`、`why_matched`、`why_kept`、`context_excerpt`、`evidence_summary` | 证据门槛仍以内置启发式为主，少量规则还需更多样本校准 | 持续补充真实风险样例和误报样例，完善每类规则的最低证据门槛 |
| 误报治理能力 | 已有 raw/final 闭环 | 可过滤文档型、规则库、测试 payload、示例、报告摘要、类型声明等误报；支持人工复核、复核后报告、维护者误报 Excel 导出 | 误报样本还未自动回灌规则，仍需维护者分析高频模式 | 将误报 Excel 中的高频模式沉淀到 `false-positive-taxonomy.md` 和代码过滤逻辑 |
| 整改建议能力 | 已增强场景化建议 | 按 `rule_id + file_role + decision + statement_type` 组合生成建议；HTML 中拆分为“优先处置 / 场景化建议 / 修改例子 / 建议类型 / 准入策略”，报告泄露、内网访问、路径逃逸、供应链、模型输出执行等高频风险已有专门建议 | 建议仍以内置规则为主，尚未把建议库外置成可配置材料；部分低频规则还缺少更贴近业务的修复样例 | 持续从真实报告和复核台账中补充修改例子，后续可将建议模板沉淀到独立整改建议库 |
| 报告生成 / 准入治理能力 | 已有准入闭环 | 支持 console/json/html/pdf，输出分数、风险、证据、结构化建议、状态、raw/final 统计和 `PASS / PASS_WITH_WARNINGS / NEEDS_REVIEW / BLOCKED` 准入结论 | 复核结论回放和最终报告固化仍可继续增强 | 支持导入复核结论、生成独立复核后报告、沉淀审计留痕 |

### AI 个性化整改建议

AI 个性化整改建议现在由 CLI 的 `--LLM` 显式开启，普通 HTML 报告不会显示模型配置、AI 按钮或请求参数。静态扫描仍负责问题发现、上下文复核、误报过滤和准入判断；AI 只对已经进入最终报告的问题生成更贴近证据位置的修改策略。

使用流程：

1. 执行带 `--LLM` 的扫描命令，例如 `java -jar dist\skillguard.jar scan .\skills --format html --output report-ai.html --LLM`。
2. 工具弹出“SkillGuard 模型配置”窗口，展示本地配置文件中已保存的默认值。
3. 用户填写或确认 API 地址、模型名称、API Key、temperature、max tokens、组织约束和可选请求体 JSON。
4. 点击“测试连接并保存”。连接成功后配置会落到本地配置文件，并继续执行静态扫描。
5. 如果连接失败或用户取消，命令直接输出 `请先正确连接模型` 并停止，不继续生成 AI 报告。
6. AI 建议只覆盖报告中的整改建议内容，不改变静态扫描的风险等级、确认状态、准入结论和 JSON 结构化字段。

真实银行数据接入外部模型前，应先确认组织内部的数据脱敏、模型调用、日志留存和跨境合规策略。建议优先使用内网模型服务或已通过审批的模型网关。

现阶段已经完成第一版“判定过程可解释化”：规则原始命中、上下文复核、误报过滤、最终保留结论已经分开记录。下一步应重点把人工复核结果回灌到误报样本库，并把高频误报转成可测试的过滤、降级或证据门槛规则。

## 本地误报治理闭环

第一版 SkillGuard 的误报治理定位是本地维护者闭环，而不是云端收集用户数据：

- 普通使用者在本地运行扫描，只需要查看最终安全报告和准入结论。
- 维护者在本地批量扫描行内已有 Skill，人工复核误报，并把高频误报沉淀为样本和规则。
- 工具不上传 Skill 内容、扫描结果或复核结论。

本地闭环材料：

| 材料 | 路径 | 用途 |
|:---|:---|:---|
| 误报回归样本 | `examples/false-positive-cases/` | 固化安全需求文档、测试 payload、占位报告、安全修复示例等典型误报 |
| 维护者台账 | `examples/review/false-positive-ledger.csv` | 记录本地人工复核过的误报样本、规则、位置、证据和期望结论 |
| 回归脚本 | `scripts/regression-check.ps1` | 每次构建后验证已知误报不进入 final，已知真实风险仍保留 |

当前误报回归集包含：

- `security-requirement-doc`：安全需求文档中的弱口令、文件上传、日志脱敏、MD5/SHA1/DES 等规范语句。
- `test-payload-library`：授权测试 payload 样例，如 XSS、SQLi、目录穿越、云元数据地址。
- `report-placeholder`：报告中的 `<token>`、`<masked>`、`<internal-host>` 等占位符。
- `safe-fix-example`：安全修复建议和安全实现示例。

验收标准：

- 误报回归样本整体风险应为 `INFO`。
- 每个误报样本 Skill 的 `final_findings_count` 必须为 `0`。
- 真实风险样例 `risky-ui-helper` 仍必须保留关键规则，如 `FILE001`、`EXFIL001`、`BANK001`、`BANK002`、`CMD002`。

### 通用安全规则

| 规则 | 风险域 | 示例风险 |
|:---|:---|:---|
| `FILE001` | 敏感文件访问 | `.env`、SSH 密钥、云厂商凭据、证书私钥 |
| `EXFIL001` | 数据外传 | `curl`、`requests.post`、`fetch` 携带 token、secret、`.env` |
| `NET001` | 外部网络请求 | `curl http://`、`wget`、`requests.get/post`、`httpx`、`fetch` |
| `CMD001` | 高危命令 | `sudo`、`rm -rf /`、`chmod 777`、`dd of=/` |
| `CMD002` | 命令执行 | `os.system`、`subprocess shell=True`、`child_process.exec` |
| `INJ001` | 动态执行 | `eval`、`exec`、`__import__`、`Function()` |
| `OBF001` | 混淆调用 | `base64.b64decode`、`atob`、`chr()+chr()`、`getattr` |
| `DEP001` | 全局依赖污染 | `npm install -g`、`pip install --user`、`yarn global` |
| `DEP002` | 强制依赖覆盖 | `--force-reinstall`、`--ignore-installed`、`npm install --force` |
| `SUPPLY001` | 供应链高危模式 | `curl | bash`、`wget && sh`、远程脚本下载后直接执行 |

### 鉴权与会话规则

| 规则 | 关注点 | 示例风险 |
|:---|:---|:---|
| `AUTH001` | 登录凭据硬编码 | 用户名、密码、OTP、短信验证码、动态口令 |
| `AUTH002` | 会话材料泄露 | Bearer Token、JWT、JSESSIONID、Cookie、access token |
| `AUTH003` | TLS 校验关闭 | `verify=False`、`--insecure`、`rejectUnauthorized:false` |
| `AUTH004` | 认证鉴权绕过 | `skipAuth`、`disableAuth`、`mock admin`、绕过鉴权 |
| `AUTH005` | 高权限测试账号 | `admin`、`root`、超级柜员、运维账号、DBA 账号 |
| `AUTH006` | 签名密钥硬编码 | `client_secret`、AK/SK、sign key、加签密钥、验签密钥 |

这类规则主要服务于接口安全测试、登录态测试、权限测试、越权测试和自动化回归场景。

### 数据与隐私规则

| 规则 | 关注点 | 示例风险 |
|:---|:---|:---|
| `BANK002` | 金融个人信息 | 身份证、银行卡、手机号、客户号、账号、卡号 |
| `DATA001` | 真实客户数据 | `real_customer`、`prod_dump`、生产数据、脱敏前、交易流水 |
| `DATA002` | 批量数据导出 | `select * from customer`、`mysqldump`、`pg_dump`、客户/账户/交易导出 |
| `DATA003` | 报告附件泄露 | CSV、Excel、HAR、trace、截图、Allure 附件包含敏感字段 |
| `BANK004` | 敏感日志 | 日志、print、console 输出 token、身份证、银行卡、手机号 |
| `BANK005` | 用户标识暴露 | 报告或样例中残留用户号、柜员号、测试账号、认证主体 |

银行测试团队应重点关注测试 fixture、接口响应样例、截图、HAR、Allure 附件、缺陷描述中的敏感字段。

### 环境隔离与生产误用规则

| 规则 | 关注点 | 示例风险 |
|:---|:---|:---|
| `BANK001` | 生产或核心系统连接 | `prod`、`production`、生产环境、核心库、账务、清算、JDBC |
| `ENV001` | 环境切换到生产 | `ENV=prod`、`SPRING_PROFILES_ACTIVE=production` |
| `ENV002` | 主机/代理/证书修改 | hosts、HTTP_PROXY、truststore、keystore、证书信任 |
| `ENV003` | 访问受控内网入口 | VPN、堡垒机、跳板机、kubeconfig、集群配置 |

这类风险适合设置为强门禁，尤其是测试 Skill 被 Agent 自动执行时。

### 数据库与账务规则

| 规则 | 关注点 | 示例风险 |
|:---|:---|:---|
| `BANK003` | 高危数据库变更 | `drop`、`truncate`、账户/客户/交易表删除 |
| `DB001` | 无条件更新删除 | 无 `where` 的 `delete`、`update` |
| `DB002` | 账务状态修改 | 余额、金额、额度、授信、利率、冻结、状态字段 |
| `DB003` | 事务保护不足 | 自动提交、直接 commit、缺少 rollback |

造数、清数、账务模拟、支付链路测试类 Skill 必须重点审查。

### 接口安全测试规则

| 规则 | 关注点 | 示例风险 |
|:---|:---|:---|
| `APISEC001` | 爆破认证材料 | 登录、密码、OTP、验证码、MFA 字典测试 |
| `APISEC002` | 无边界 fuzz | 批量接口遍历、参数篡改、并发、QPS、wordlist |
| `APISEC003` | 交易重放与金额篡改 | 转账、支付、退款、放款、订单、交易重放 |
| `APISEC004` | 安全测试 payload | SQL 注入、XSS、SSRF、XXE、命令注入、目录穿越 |

这些行为不一定是恶意，但必须限定测试环境、测试账号、速率、审批边界和回滚策略。

### 自动化执行规则

| 规则 | 关注点 | 示例风险 |
|:---|:---|:---|
| `TEST001` | 删除测试产物 | Allure、coverage、reports、screenshots、traces |
| `TEST004` | 压测或无限请求 | `while true`、`wrk`、`jmeter`、`hey`、无速率限制 |
| `TEST005` | 浏览器真实会话 | Chrome profile、Cookie、localStorage、User Data |
| `AUTO001` | 触发真实业务动作 | 点击提交、转账、支付、审批、放款、退款 |
| `AUTO002` | 产物写入非受控目录 | Desktop、Downloads、公共目录、共享目录 |

UI 自动化和浏览器自动化 Skill 尤其要避免读取真实浏览器会话。

### CI/CD 与供应链规则

| 规则 | 关注点 | 示例风险 |
|:---|:---|:---|
| `TEST002` | 修改流水线配置 | `.github/workflows`、`.gitlab-ci.yml`、Jenkinsfile |
| `CI001` | 打印敏感环境变量 | `printenv`、`env`、`echo $TOKEN`、`Get-ChildItem Env:` |
| `CI002` | 跳过测试或扫描 | `skipTests`、`--skip`、跳过扫描、禁用 quality gate |
| `CI003` | 不可复现依赖 | `latest`、`SNAPSHOT`、`--ignore-scripts`、`--no-verify` |

流水线相关 Skill 应默认进入人工复核流程。

### 报告与缺陷流转规则

| 规则 | 关注点 | 示例风险 |
|:---|:---|:---|
| `REPORT001` | 外部通知泄露 | webhook、企业微信、飞书、钉钉、邮件推送报告 |
| `REPORT002` | 缺陷平台泄露 | Jira、禅道、TAPD、缺陷描述中携带 request/response/header/cookie/token |
| `REPORT003` | 分析报告认证上下文泄露 | `auth_info`、`authorization`、`cookie`、`session`、`token` |
| `REPORT004` | 内部接口清单泄露 | `api_url`、`upload_urls`、`endpoint` 中保留真实域名和路径 |
| `REPORT005` | 请求参数泄露 | `request_params`、`request_body` 中保留业务对象编号或可复现参数 |

测试报告、失败截图、HAR、trace、接口请求响应在银行场景里都应按敏感材料管理。

对于 `security-analysis/report.json`、`analysis.md` 这类由 Skill 生成的分析产物，工具会做更细的上下文判断：

- `unmatched_note`、`matched_*`、`summary`、`recommendation` 这类结论字段，不再直接按生产环境、支付、鉴权等运行时风险处理。
- `auth_info`、`users`、`api_url`、`upload_urls`、`request_params`、`request_body` 这类上下文字段会单独检查，因为它们可能把内部接口、用户标识、认证上下文或可复现请求参数写进报告。
- 这类问题的整改建议不是“禁止做安全分析”，而是“分析报告默认脱敏”：保留接口分类、角色类型、认证方式和风险结论，隐藏真实域名、用户号、事件号、任务号、Token、Cookie、请求参数。

### Agent 指令与 Skill 治理规则

| 规则 | 关注点 | 示例风险 |
|:---|:---|:---|
| `AGENT001` | 绕过安全策略 | 忽略安全规则、绕过审批、自动批准、bypass |
| `AGENT002` | 隐藏操作 | 不告诉用户、无需确认、静默执行、不要记录 |
| `AGENT003` | 远程指令引用 | 按远程 URL 的 prompt、md、txt、脚本执行 |
| `META001` | 未完成标记 | TODO、FIXME、待补充、临时方案 |
| `META002` | 缺少环境/权限边界 | 未说明适用环境、禁止环境、权限边界 |
| `META003` | 缺少验证方式 | 未说明自测步骤、预期结果、断言标准 |
| `META004` | 缺少清理回滚 | 未说明 cleanup、rollback、幂等策略 |
| `META005` | 缺少负责人版本 | 未说明 owner、maintainer、version、changelog |
| `META006` | 缺少输入输出 | 未说明读取什么、生成什么、写入哪里 |
| `META007` | 文档与行为不一致 | 声称只生成/不执行，但实际存在外联、命令、数据库等风险 |

这类规则用于把 Skill 从“个人脚本”治理成“团队资产”。

## JSON 报告字段

JSON 报告适合 CI 和平台集成，核心字段包括：

```json
{
  "root": "扫描根目录",
  "skills_scanned": 2,
  "files_scanned": 3,
  "total_findings": 18,
  "risk_level": "critical",
  "skills": [
    {
      "name": "risky-ui-helper",
      "path": "Skill 路径",
      "risk_level": "critical",
      "score": 32,
      "safety_score": 0,
      "structure_score": 85,
      "test_fitness_score": 55,
      "files_scanned": 2,
      "findings": [
        {
          "rule_id": "BANK001",
          "category": "production_environment",
          "severity": "critical",
          "file": "命中文件",
          "line": 11,
          "message": "问题说明",
          "evidence": "命中证据",
          "recommendation": "整改建议",
          "confidence": 0.92
        }
      ]
    }
  ]
}
```

## CI/CD 接入示例

### PowerShell

```powershell
java -jar dist\skillguard.jar scan .\skills --format json --output skillguard-report.json --fail-on high
if ($LASTEXITCODE -ne 0) {
  Write-Host "Skill 安全扫描未通过"
  exit $LASTEXITCODE
}
```

### GitHub Actions

```yaml
name: skillguard

on:
  pull_request:

jobs:
  scan-skills:
    runs-on: windows-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
      - name: Build SkillGuard
        run: .\build.ps1
      - name: Scan skills
        run: java -jar dist\skillguard.jar scan .\skills --format html --output skillguard-report.html --fail-on high
```

### Jenkins Pipeline

```groovy
stage('SkillGuard Scan') {
  steps {
    powershell 'java -jar dist\\skillguard.jar scan .\\skills --format json --output skillguard-report.json --fail-on high'
    archiveArtifacts artifacts: 'skillguard-report.json', fingerprint: true
  }
}
```

## 银行测试团队推荐准入流程

建议流程：

```text
提交 Skill
  ↓
本地扫描 console/html
  ↓
修复 CRITICAL/HIGH
  ↓
生成 HTML 报告给安全测试复核
  ↓
生成 PDF 摘要归档
  ↓
CI 使用 --fail-on high 作为门禁
  ↓
隔离环境试运行
  ↓
进入团队 Skill 仓库
```

建议强阻断项：

- 生产环境或核心系统连接。
- 真实客户数据、生产导出数据、客户流水。
- Token、Cookie、Session、签名密钥硬编码。
- 下载脚本后直接执行。
- 破坏性数据库操作或账务字段修改。
- 绕过认证、鉴权、安全策略、审批流程。
- 读取真实浏览器 profile、Cookie、localStorage。

## 示例

仓库内置两个示例：

```text
examples/skills/safe-api-helper
examples/skills/risky-ui-helper
```

`safe-api-helper` 是合规示例，包含：

- 适用环境和禁止环境。
- 权限边界。
- 输入输出说明。
- 验证方式。
- 清理策略。
- 版本和变更记录。

`risky-ui-helper` 是风险示例，故意包含：

- 读取 `.env`。
- 外联上传数据。
- 删除测试报告目录。
- 全局安装依赖。
- 浏览器会话风险。
- 生产 JDBC。
- 客户身份证和账户号。
- 破坏性 SQL。
- Skill 文档治理缺项。

运行：

```powershell
java -jar dist\skillguard.jar scan examples\skills
java -jar dist\skillguard.jar scan examples\skills --format html --output build\sample-report.html
java -jar dist\skillguard.jar scan examples\skills --format pdf --output build\sample-report.pdf
```

## 误报处理建议

当前版本是静态规则扫描，可能存在误报。建议处理方式：

- 先看 `evidence` 字段，确认命中的具体文本。
- 判断命中内容是实际执行逻辑、测试样例、禁止性说明还是安全测试 payload。
- 对于真实误报，优先调整 Skill 文档表达，避免模糊词。
- 对于规则误报，应在规则库中收紧正则，而不是简单忽略。
- 对于确有业务必要的高风险行为，应补充环境边界、审批记录、回滚策略、负责人和执行限制。

## 已知限制

- 当前工具是静态扫描，不执行 Skill，也不做动态行为监控。
- 当前规则以正则为主，不是完整 AST、污点分析或依赖图分析。
- PDF 报告是零依赖内置生成的表格化摘要，复杂排版建议使用 HTML 报告。
- 规则库内置在 Jar 中，当前版本暂未提供外部 YAML 规则配置。
- 扫描结果不能替代代码评审、沙箱试运行、安全测试和合规审批。

## 后续可扩展方向

- 外部规则文件：支持 YAML/JSON 自定义规则。
- 白名单与例外审批：支持按规则、文件、Skill、有效期配置例外。
- Baseline 模式：只阻断新增风险。
- AST 分析：对 Python、Shell、JavaScript 做更精确的上下文判断。
- 敏感数据脱敏检测：对身份证、银行卡、手机号做更严格格式校验。
- 报告签名与归档：满足审计留痕要求。
- 与缺陷平台、流水线平台、Skill 管理平台集成。

## 退出码

| 退出码 | 含义 |
|:---|:---|
| `0` | 扫描完成，未触发 `--fail-on` 阈值 |
| `1` | 扫描完成，但风险达到 `--fail-on` 阈值 |
| `2` | 参数错误、路径不存在、路径不可读或 PDF 未指定输出文件 |

## 常用命令速查

```powershell
# 构建
.\build.ps1

# 查看帮助
java -jar dist\skillguard.jar --help

# 扫描默认 skills 目录
java -jar dist\skillguard.jar scan

# 扫描指定目录
java -jar dist\skillguard.jar scan .\skills

# 生成 HTML 报告
java -jar dist\skillguard.jar scan .\skills --format html --output report.html

# 生成 PDF 报告
java -jar dist\skillguard.jar scan .\skills --format pdf --output report.pdf

# 生成 JSON 报告
java -jar dist\skillguard.jar scan .\skills --format json --output report.json

# CI 门禁
java -jar dist\skillguard.jar scan .\skills --fail-on high
```

## 无控制台 LLM 启动

如果希望以图形方式启动 AI 个性化整改建议扫描，并且不在后台显示 cmd/PowerShell 窗口，可以双击项目根目录下的：

```text
scan-skills-llm-gui.vbs
```

该脚本会使用 `javaw.exe` 启动以下扫描：

```powershell
java -jar dist\skillguard.jar scan .\skills --format html --output build\skillguard-ai-report.html --LLM
```

运行后会先打开模型配置窗口。连接成功后继续扫描并生成 `build\skillguard-ai-report.html`；取消或连接失败则不会生成 AI 报告。命令行方式仍然保留，适合需要查看退出码和控制台提示的场景。
