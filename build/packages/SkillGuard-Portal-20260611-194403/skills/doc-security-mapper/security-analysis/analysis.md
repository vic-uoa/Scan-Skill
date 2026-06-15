# 安全需求分析报告

## 文档信息

- 文件名：技术特性需求说明书.md
- 分析时间：2026-04-01

## 匹配结果（5 条）

| ID | 需求名称 | 置信度 | 原文依据 | 需求设计 |
| --- | --- | --- | --- | --- |
| 10 | 客户端应保护认证信息并禁用不必要的配置及组件以增强安全 | high | 服务端进行文件类型与内容安全校验，防止恶意文件上传。 | 检查上传文件类型和大小，上传文件存放在 Web 应用范围之外，上传路径需过滤特殊字符。 |
| 11 | 文件上传防范任意文件上传造成的各类攻击 | high | 服务端进行文件类型与内容安全校验，防止恶意文件上传。 | 检查上传文件类型和大小，判断是否为可执行文件，上传文件存储在 Web 应用范围之外，禁止任何文件执行。 |
| 15 | 角色权限管理应防范水平越权和其他越权行为 | high | 服务端基于当前登录用户身份对请求资源归属进行强制校验，防止水平越权。 | 账号登录的用户角色验证和授权机制必须在服务器端有效，确保只有经过授权的用户才能访问相应功能。 |
| 58 | API 输入应该严格校验参数及其来源的合法性 | high | 所有业务接口统一接入鉴权中间件，强制校验调用方身份与权限。 | 应严格检查输入数据的格式、类型、长度、范围等要素，对非预期的数据进行拒绝，使用白名单或黑名单方式验证。 |
| 59 | API 需要有访问控制策略，包括认证、会话管理等 | high | 所有业务接口统一接入鉴权中间件，强制校验调用方身份与权限。 | 实施接口访问控制策略，对敏感数据和功能的访问实施控制，对需授权访问的 API 严格认证调用方身份。 |

## 文件上传专项

- detected: true
- upload_urls:
  - `http://fbn-view-pc.paasst.cmbchina.cn/sy/attachment/fileUpload.action`
- tech_stack: 未知

## 接口鉴权专项

- detected: true
- api_list:
  - api_name: `SLA事件管理-搜索`
    - api_url: `https://sla-front.paas.cmbchina.cn/dev-api/api/event/deal/event/list`
    - request_params: `guaranteeName=&status=&category=&draw=1&length=10&bbkId=&delayDateStart=&delayDateEnd=`
  - api_name: `SLA事件管理-查看详情`
    - api_url: `https://sla-front.paas.cmbchina.cn/dev-api/api/event/deal/event/info`
    - request_params: `eventId=20362568`
  - api_name: `SLA事件管理-事件处理`
    - api_url: `https://sla-front.paas.cmbchina.cn/dev-api/api/event/deal/event/task/push`
    - request_body: `{"taskIds":[24],"eventId":"84176150"}`
- tech_stack: Java

## 覆盖情况

以下安全领域在文档中未发现相关内容：数据采集与存储安全、日志与审计安全、通信传输安全、用户认证安全、支付安全、业务逻辑安全、移动端安全、密钥管理。文档主要聚焦于文件上传和接口鉴权防护两个专项安全需求。
