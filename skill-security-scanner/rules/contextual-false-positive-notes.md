# 上下文误报治理补充说明

更新日期：2026-06-02

本文件用于补充 SkillGuard 静态扫描中的上下文判定原则。目标不是减少规则覆盖面，而是在 raw finding 命中后增加证据门槛，避免把普通代码结构、规则检测逻辑或模板占位符误判为严重漏洞。

## 1. 运行时凭据参数不等于硬编码密钥

下列内容通常应过滤或降级，除非同时进入日志、报告、网络外发、文件写入等泄露路径：

| 场景 | 例子 | 默认处理 |
| --- | --- | --- |
| 从参数读取密码 | `args.password`、`options.token` | 过滤硬编码凭据判断 |
| 从配置对象读取 token | `config.access_token`、`settings.password` | 过滤硬编码凭据判断 |
| 从响应对象拼接鉴权头 | `Authorization: Bearer ${respData.access_token}` | 不作为 token 泄露 |
| 从环境变量读取 | `os.environ.get("ID_TOKEN")` | 不作为本地敏感文件访问 |

保留风险的条件：出现真实高熵字面量、写入日志/报告、外发到 webhook/email、落盘到附件、或与生产环境连接信息组合。

## 2. 模板占位符不等于真实凭据

下列模式应识别为模板或教学占位符：

- `postgresql://{user}:{password}@{host}:{port}/{database}`
- `mssql+pyodbc://${user}:${password}@${host}:${port}/${database}`
- `my-client-secret`
- `xxx`
- `changeme`
- `replace_me`
- `<token>` / `<password>` / `<secret>`

保留风险的条件：占位符被真实账号、真实 token、高熵密钥、生产域名或用户号替换。

## 3. 规则检测逻辑不等于被检测漏洞

扫描器、解析器、规则库或安全测试工具中常见下列内容：

- `re.compile(...)`
- `value_upper` / `key_lower`
- `secretKeyRef.key`
- `Object.keys(...)`
- `ConfigParser`
- `yaml.safe_load`
- `json.loads`

这些代码是在识别风险或解析配置，不应仅因包含 `key`、`token`、`password`、`payload` 被判为严重。只有当它同时执行真实危险动作，例如输出敏感值、发送请求、读取宿主凭据文件、执行命令，才进入 final finding。

## 4. 业务字段标签不等于真实客户数据

仅出现 `交易流水`、`客户`、`账户`、`comment`、`label`、`description` 等业务字段名称，不应直接判为真实客户数据泄露。

保留风险的条件：出现真实身份证号、银行卡号、手机号、生产数据导出、`real_customer`、`prod_dump`、批量导出或报告沉淀上下文。

## 5. 本地凭据文件访问必须有路径或读取动作

`key` / `keys` / `.key` 字段名本身不构成敏感文件访问。`FILE001` 至少需要满足以下之一：

- 明确敏感路径：`~/.ssh/id_rsa`、`.aws/credentials`、`.env`、`*.pem`
- 明确读取动作：`open(".env")`、`cat ~/.ssh/id_rsa`、`Get-Content *.pem`
- 工作区外路径和文件读写/下载/解压语境同时出现

字段名、Kubernetes `secretKeyRef.key`、JSON key、对象属性 key 应默认过滤。

## 6. 仍需保留的真实高风险

以下情况不得因为上下文收敛被过滤：

- 高熵硬编码 token/client_secret/sign_key
- 明确生产环境 JDBC/API/核心系统连接
- 真实用户号、身份证、银行卡、手机号、客户流水
- 将 token/cookie/password 输出到日志、报告、截图、HAR、Webhook、邮件
- 读取宿主真实凭据文件或工作区外敏感文件
- 用户输入拼接到命令、SQL、URL、路径后直接执行

## 7. 常规认证流程不等于会话泄露

许多 Skill 会读取接口响应里的 `access_token`，再拼接 `Authorization: Bearer ...` 调用后续接口，或在 token 不存在时抛出错误。这类逻辑是认证流程本身，不应仅因出现 `access_token`、`Bearer`、`Authorization` 判为严重。

默认过滤或降级的例子：

- `if (!respData.access_token) { throw new Error("access_token not found in response"); }`
- `"Authorization": "Bearer ${respData.access_token}"`
- `return {"Authorization": f"Bearer {resp_data['access_token']}"}`

保留风险的条件：token 被打印、写日志、写报告、保存到文件、发给第三方、拼接到 URL，或出现真实高熵 token/JWT/Cookie 字面量。

## 8. 网络请求能力不等于外联或数据外传

`urlopen(req)`、`requests.get(url)`、`fetch(url)`、`axios(...)` 只说明代码具备网络请求能力。只有当同一上下文中出现真实外部域名、内网地址、用户可控 URL、敏感数据、报告上传、文件下载、凭据外发时，才应作为外联、SSRF 或数据外传风险保留。

默认过滤或降级的例子：

- `with urllib.request.urlopen(req, timeout=30) as resp:`
- `fetch(url, options)`
- `requests.get(api_url, timeout=10)`

保留风险的条件：`url` 来自用户输入、环境变量指向生产/内网、请求体包含 token/customer/report、或该 Skill 明确自动上传/下载敏感产物。

## 9. 依赖元数据和配置标签不等于环境风险

依赖锁文件中的 `integrity: sha512-...`、`resolved`、包名，以及配置文件里的 `VPN`、`proxy`、`jump_host` 标签，通常是元数据或枚举值，不代表 Skill 自动访问跳板机、VPN 或生产环境。

默认过滤或降级的例子：

- `package-lock.json` 中的 `integrity: "sha512-..."`
- `"VPN": "VPN代理"`
- `"VPNOut": "VPN出口"`

保留风险的条件：出现真实跳板机地址、VPN 账号、代理凭据、生产域名、自动连接动作，或文档明确要求绕过准入/审批。

## 10. 静态分发、正则和解析器不等于动态代码执行

扫描器、解析器和测试生成器里常见 `exec`、`eval` 字样，但语义可能完全不同。正则 `RegExp.exec`、解析器内部 `exec`、固定函数表调度，不应和执行不可信代码混为一谈。

默认过滤或降级的例子：

- `stackRegex.exec(errorStack)`
- `pattern.exec(text)`
- `expect_dict[name] = eval("expect_" + name)(data)` 且 `name` 来自固定白名单或表结构

保留风险的条件：外部输入、报告内容、用户参数或不可信配置直接拼接进入 `eval/exec/new Function/subprocess`，且缺少白名单或隔离。

## 11. 扫描器状态日志和检测字典不等于敏感日志

安全扫描类 Skill 经常包含“正在扫描明文密码”“弱口令字典”“password/token 匹配逻辑”等文本。它们说明扫描器在寻找风险，不代表扫描器自己泄露了密码。

默认过滤或降级的例子：

- `print("Step 2: Scanning plaintext passwords...")`
- `["admin", "password", "123456", "12345678"]`
- `if "password" in key_lower:`

保留风险的条件：日志实际输出真实账号、真实密码、真实 token、客户号，或用高权限账号执行真实登录尝试。

## 12. 文档元数据日志不等于客户数据泄露

DOCX/OOXML 解析或修复类 Skill 可能会输出 `RSID`、revision、edit session、style id、paragraph id 等文档内部元数据。这类值通常用于定位文档结构或修订痕迹，不等同于客户号、证件号、银行卡号或生产数据。

默认过滤或降级的例子：

- `print(f"Suggested RSID for edit session: {suggested_rsid}")`
- `"comment": "交易流水"` 作为模板字段说明
- `revision_id`、`style_id`、`paragraph_id` 的解析日志

保留风险的条件：同一上下文中出现真实用户号、身份证号、银行卡号、手机号、生产流水、认证 token，或这些元数据被写入对外报告且可关联真实客户。
