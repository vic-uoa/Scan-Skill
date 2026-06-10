---
name: doc-security-mapper
description: 文档安全需求识别与映射
---

# Skill 帮助用户从各类格式文档中提取关键内容，识别其中隐含或明确的安全需求，并将其映射到制度安全需求库（64 条），最终输出结构化的产物文件。

当文档中识别到文件上传相关安全需求时，还会进一步提取上传接口信息，并在用户确认后直接向 `file-upload` skill 下达漏洞测试指令。

当文档中识别到接口越权相关安全需求时，还会进一步提取接口信息与用户信息，并在用户确认后直接向 `web-access-control-sla` skill 下达越权测试指令。

此 skill 中所有调用 python.exe 的操作，python 路径都从 python_path 获取。

**支持的文档格式：**

- PDF（`.pdf`）
- Word 文档（`.docx`、`.doc`）
- Excel 表格（`.xlsx`、`.xls`、`.csv`）
- Markdown（`.md`）
- 纯文本（`.txt`）

**产物文件（统一保存到 `doc-security-mapper/security-analysis/` 下）：**

- `analysis.md`：第二步安全需求识别结果（Markdown 表格）
- `report.json`：第三步完整 JSON 报告
- `report.md`：第三步人类可读摘要报告

> **路径说明：** 产物文件的绝对路径为 `<skills根目录>/doc-security-mapper/security-analysis/`，即与本 `SKILL.md` 同级的 `security-analysis/` 子目录下。不得写入其他位置。

---

## 工作流程，必须严格按照此流程执行，要求输出到屏幕的内容必须输出。一共有六个步骤，第六个步骤为总结步骤，总结前五步的执行结果并输出 md 格式的文档。

### 第一步，提取文档内容

使用 `scripts/extract.py` 脚本提取文档文本内容：

```bash
python scripts/extract.py <文档路径>
```

脚本会自动识别文件格式并提取文本，输出提取的原始文本内容。

如果脚本执行失败或格式不支持，向用户返回：`抱歉，无法解析该文档`。

**第一步完成后，必须向用户展示以下信息：**

**第一步：文档内容解析**

- 文件名：[文件名]
- 文件格式：[格式]
- 提取字数：N 字符
- 内容预览：[前 200 字]

### 第二步，分析文档内容，识别安全需求

读取 `references/security-requirements.md`，获取完整的 64 条安全需求库。

基于提取的文档文本，逐条检查安全需求库，判断文档内容是否涉及该安全需求。

**识别策略（按优先级）：**

1. **直接提及**：文档中明确出现该安全需求的关键词，例如 “TLS”、“加密”、“验证码”、“权限”等
2. **功能隐含**：文档描述的功能隐含该安全需求，例如文档描述 “用户登录功能” 则隐含密码安全、会话管理等需求
3. **场景推断**：根据文档整体场景（如支付系统、移动端 App）推断相关安全需求

**映射置信度定义：**

- `high`：文档明确提及相关安全概念，直接对应
- `medium`：文档描述的功能或场景隐含该需求，有合理依据
- `low`：基于文档整体场景推断，存在不确定性

**文件上传相关需求的专项识别：**

在完成通用安全需求匹配后，额外检查文档是否涉及文件上传功能。满足以下任意一条即判定为检测到文件上传功能（`file_upload_context.detected = true`）：

| 识别信号 | 示例原文 |
| --- | --- |
| 明确提到上传功能 | “用户可上传头像”、“支持上传附件” |
| 接口含 multipart/form-data | `Content-Type: multipart/form-data` |
| 含 curl -F / file=@ 示例 | `curl -F 'file=@test.jpg'` |
| 描述文件类型/大小限制 | “仅允许上传 JPG/PNG，不超过 2MB” |
| 描述上传接口路径 | `POST /api/upload`、`/file/upload` |

同时从文档中提取以下上传相关上下文信息，填入 `file_upload_context`：

- `upload_urls`：文档中出现的上传接口地址（可能为空）
- `tech_stack`：推断的技术栈（PHP / Java / .NET / Node / Python / 未知）
- `auth_info`：认证方式描述（Token / Cookie / 无 / 未知）
- `file_field`：上传字段名（默认 `file`）
- `constraints`：文档中描述的文件类型/大小限制
- `matched_upload_requirements`：命中的与文件上传相关的安全需求 ID 列表

**接口越权相关需求的专项识别：**

在完成通用安全需求匹配后，额外检查文档是否涉及接口越权防护。满足以下任意一条即判定为检测到接口越权相关内容（`access_control_context.detected = true`）：

| 识别信号 | 示例原文 |
| --- | --- |
| 明确提到“越权”或“权限控制” | “防止水平越权”、“基于角色权限控制” |
| 描述水平越权场景 | “仅允许查看本人订单”、“禁止访问他人数据” |
| 描述垂直越权场景 | “普通用户不得调用管理员接口” |
| 描述资源归属校验 | “按用户 ID/租户 ID 进行服务端归属校验” |
| 描述接口路径与角色/身份关系 | `GET /api/user/{id}`、`POST /admin/*` |

同时从文档中提取以下越权相关上下文信息，填入 `access_control_context`：

- `api_list`：文档中出现的待测接口列表（功能名、接口地址、请求参数）
- `tech_stack`：推断的技术栈（PHP / Java / .NET / Node / Python / 未知）
- `auth_info`：认证方式描述（Token / Cookie / 无 / 未知）
- `users`：文档中可识别的高权/低权测试用户（若缺失则标记需补充）
- `matched_access_control_requirements`：命中的与接口越权相关的安全需求 ID 列表

**第二步完成后：**

1. 只将置信度为 high 的识别结果写入 `security-analysis/analysis.md`，内容格式如下：

```markdown
# 安全需求分析报告

## 文档信息
文件名：[filename]
分析时间：[datetime]

## 匹配结果（N 条）

| ID | 需求名称 | 置信度 | 原文依据 | 需求设计 |
| --- | --- | --- | --- | --- |
| 9 | 互联网开放API应使用TLS/HTTPS | high | 文档提到“...” | 应使用 TLS 1.2 及以上... |

## 文件上传专项
- detected: true/false
- upload_urls: [...]
- tech_stack: ...

## 接口越权专项
- detected: true/false
- api_list: [...]
- tech_stack: ...
- users: [...]

## 覆盖盲区
[unmatched_note]
```

2. 必须在屏幕向用户展示（命中的需求只展示与文件上传和接口越权相关的需求）：

**第二步：安全需求识别，完成后立即向用户展示**

- 高置信度需求（high）：
  - [ID] [需求名称]（high）
  - [ID] [需求名称]（high）
  - [逐条列出所有 confidence=high 的命中需求）
- 产物文件：`security-analysis/analysis.md`

### 第三步，生成需求结果文件

将分析结果分别写入两个文件，全部只记录置信度为 high 的需求：

**3.1 写入 `security-analysis/report.json`，内容为完整 JSON：**

```json
{
  "document": {
    "filename": "文件名",
    "format": "文件格式",
    "extracted_text_length": 1234,
    "summary": "文档内容简要描述（100字以内）"
  },
  "analysis": {
    "total_requirements_matched": 12,
    "high_confidence": 5,
    "medium_confidence": 4,
    "low_confidence": 3
  },
  "matched_requirements": [
    {
      "id": 9,
      "name": "互联网开放API应使用TLS/HTTPS等安全传输加密",
      "confidence": "high",
      "evidence": "文档第3节提到“所有API接口需通过HTTPS访问”",
      "design": "面向互联网开放API时，应使用TLS等安全通道加密传输。建议使用TLS 1.2及以上版本。"
    }
  ],
  "unmatched_note": "以下安全领域在文档中未发现相关内容：支付安全、移动端安全",
  "file_upload_context": {
    "detected": true,
    "upload_urls": ["POST /api/v1/upload"],
    "tech_stack": "Java",
    "auth_info": "Authorization: Bearer <token>",
    "file_field": "file",
    "constraints": "仅允许 JPG/PNG，不超过 2MB",
    "matched_upload_requirements": [12, 15, 28]
  },
  "access_control_context": {
    "detected": true,
    "api_list": [
      {
        "api_name": "查询订单详情",
        "api_url": "GET /api/order/detail",
        "request_body": "{\"orderId\":\"123\"}"
      }
    ],
    "tech_stack": "Java",
    "auth_info": "Authorization: Bearer <token>",
    "users": ["username1", "username2", "username3"],
    "matched_access_control_requirements": [21, 22, 23]
  }
}
```

**3.2 写入 `security-analysis/report.md`，内容为人类可读摘要：**

```markdown
# 安全需求映射报告

## 执行摘要
- 文档：[filename]
- 分析时间：[datetime]
- 匹配需求：N 条（high: X, medium: Y, low: Z）
- 文件上传检测：detected: true/false
- 接口越权检测：detected: true/false

## 命中需求清单
| ID | 需求名称 | 置信度 | 原文依据 |
| --- | --- | --- | --- |

## 覆盖盲区
[unmatched_note]

## 文件上传上下文
（仅 detected=true 时输出）
- 接口：[upload_urls]
- 技术栈：[tech_stack]
- 认证：[auth_info]
- 限制：[constraints]

## 接口越权上下文
（仅 detected=true 时输出）
- 接口列表：[api_list]
- 技术栈：[tech_stack]
- 认证：[auth_info]
- 测试用户：[users]
```

**第三步完成后，立即向用户展示：**

**第三步：安全需求总结**

- `security-analysis/analysis.md`（需求识别明细）
- `security-analysis/report.json`（完整 JSON）
- `security-analysis/report.md`（可读摘要）

### 第四步，触发文件上传漏洞测试（仅当 `file_upload_context.detected == true`）

完成报告文件写入后，若 `file_upload_context.detected` 为 `true`，则执行本步骤。

**4.1 展示发现摘要并请求用户确认**

向用户展示以下摘要，并等待确认：

**第四步：调用 file-upload skill**

发现文件上传相关安全需求（N 条）：

- [需求名称]（置信度：high/medium）
- ...

检测到上传接口：[upload_urls 或“文档未提供，需手动补充”]

是否立即对该接口发起文件上传漏洞测试？

1. 是，立即测试
2. 否，仅查看安全需求报告

若文档中未找到上传接口 URL（`upload_urls` 为空），在摘要中提示用户补充后再启动测试，不强制继续。

**4.2 用户确认后，构造并下达测试指令**

用户选择“是，立即测试”后，将 `file_upload_context` 中的信息自动转译为 `file-upload` skill 的启动指令，格式如下：

```text
对 [upload_urls] 接口进行文件上传漏洞测试。
技术栈：[tech_stack]
认证方式：[auth_info]
上传字段名：[file_field]
文档中已明确的限制：[constraints]
重点验证的安全需求（来自文档分析）：[matched_upload_requirements 对应的需求名称]
```

随后移交控制权给 `file-upload` skill，进入其三阶段 H1TL 流程（案例设计 -> 测试执行 -> 测试总结）。

### 第五步，触发接口越权漏洞测试（仅当 `access_control_context.detected == true`）

完成报告文件写入后，若 `access_control_context.detected` 为 `true`，则执行本步骤。

**5.1 展示发现摘要并请求用户确认**

向用户展示以下摘要，并等待确认：

**第五步：调用 web-access-control-sla skill**

发现接口越权相关安全需求（N 条）：

- [需求名称]（置信度：high/medium）
- ...

检测到接口列表：[api_list 或“文档未提供，需手动补充”]
检测到测试用户：[users 或“文档未提供，需手动补充”]

是否立即对上述接口发起越权安全测试？

1. 是，立即测试
2. 否，仅查看安全需求报告

若文档中未找到接口列表或测试用户，在摘要中提示用户补充后再启动测试，不强制继续。

**5.2 用户确认后，构造并下达测试指令**

用户选择“是，立即测试”后，将 `access_control_context` 中的信息自动转译为 `web-access-control-sla` skill 的启动指令，格式如下：

```text
对系统 https://sla-front.paas.cmbchina.cn，用户1闫猛（80231522），低权用户3肖双全（80370132），用户2王博宇（81174498）
下列功能接口进行越权测试
SLA事件管理-搜索
https://sla-front.paas.cmbchina.cn/dev-api/api/event/deal/event/list?guaranteeName=&status=&category=&draw=1&length=10&bbkId=&delayDateStart=&delayDateEnd=
SLA事件管理-查看详情
https://sla-front.paas.cmbchina.cn/dev-api/api/event/deal/event/info?eventId=20362568
SLA事件管理-事件处理
https://sla-front.paas.cmbchina.cn/dev-api/api/event/deal/event/task/push
{"taskIds":[24],"eventId":"84176150"}
```

随后移交控制权给 `web-access-control-sla` skill，进入其专项越权检测流程。

---

### 第六步，整理前五步的信息，汇总返回总结报告，并写入 md 格式文件。

如果第四步与第五步选择“是，立即测试”，则需要等待第四步第五步执行完成才执行第六步。在目录中产生 md 格式的报告。

如果选择“否，仅查看安全需求报告”，则可以直接执行第六步。

关键字段：

章节1：只展示置信度为 high 的需求分析结果（第三步结果）

章节2：案例设计结果（分别来自第四步与第五步的结果）

章节3：案例执行结果（与案例设计结果一一对应）

章节4：测试总结结论

章节5：修复意见

## 注意事项

- 如果文档无法解析，直接告知用户：`抱歉，无法解析该文档`，并说明可能原因（如加密 PDF、损坏文件等）
- 匹配时优先质量而非数量，不要为了多匹配而降低标准；宁可少匹配也不要误映射
- `evidence` 字段应该引用文档原文或对应段落的描述，而不是安全需求库的内容
- `security-analysis/` 必须保存在 `doc-security-mapper/` 目录下，绝对路径为 `<skills根目录>/doc-security-mapper/security-analysis/`（即与 `SKILL.md` 同级），若已存在则直接覆盖同名文件
- 第四步触发漏洞测试前，必须等待用户明确确认，不得自动跳过人工确认环节
- 第五步触发漏洞测试前，必须等待用户明确确认，不得自动跳过人工确认环节
- 屏幕输出保持精炼，详细内容均保存在产物文件中，不在对话中重复展示
