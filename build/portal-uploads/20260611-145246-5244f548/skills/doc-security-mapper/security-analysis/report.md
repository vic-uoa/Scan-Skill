# 安全需求映射报告

## 执行摘要

- 文档：技术特性需求说明书.md
- 分析时间：2026-04-01
- 匹配需求：5 条（high: 5, medium: 0, low: 0）
- 文件上传检测：detected: true
- 接口越权检测：detected: true

## 命中需求清单

| ID | 需求名称 | 置信度 | 原文依据 |
| --- | --- | --- | --- |
| 10 | 客户端应保护认证信息并禁用不必要的配置及组件以增强安全 | high | 服务端进行文件类型与内容安全校验，防止恶意文件上传 |
| 11 | 文件上传防范任意文件上传造成的各类攻击 | high | 服务端进行文件类型与内容安全校验，防止恶意文件上传 |
| 15 | 角色权限管理应防范水平越权和其他越权行为 | high | 服务端基于当前登录用户身份对请求资源归属进行强制校验，防止水平越权；服务端基于用户角色对接口功能权限进行校验，防止垂直越权 |
| 58 | API输入应该严格校验数据及其来源的合法性 | high | 所有业务接口统一接入鉴权中间件，强制校验调用方身份与权限 |
| 59 | API需要有访问控制策略，包括认证、会话管理等 | high | 所有业务接口统一接入鉴权中间件，强制校验调用方身份与权限 |

## 覆盖盲区

以下安全领域在文档中未发现相关内容：数据采集与存储安全、日志与审计安全、通信传输安全、用户认证安全、支付安全、业务逻辑安全、移动端安全、密钥管理。

## 文件上传上下文

- 接口：[`http://fbn-view-pc.paasst.cmbchina.cn/sy/attachment/fileUpload.action`](http://fbn-view-pc.paasst.cmbchina.cn/sy/attachment/fileUpload.action)
- 技术栈：未知
- 认证：用户994001
- 限制：文档未明确描述文件类型与大小限制

## 接口越权上下文

- 接口列表：
  - SLA事件管理-搜索：[`https://sla-front.paas.cmbchina.cn/dev-api/api/event/deal/event/list`](https://sla-front.paas.cmbchina.cn/dev-api/api/event/deal/event/list)
  - SLA事件管理-查看详情：[`https://sla-front.paas.cmbchina.cn/dev-api/api/event/deal/event/info`](https://sla-front.paas.cmbchina.cn/dev-api/api/event/deal/event/info)
  - SLA事件管理-事件处理：[`https://sla-front.paas.cmbchina.cn/dev-api/api/event/deal/event/task/push`](https://sla-front.paas.cmbchina.cn/dev-api/api/event/deal/event/task/push)
- 技术栈：Java
- 认证：系统认证（用户ID：80231522，01174498，80370132）
- 测试用户：["80231522", "01174498", "80370132"]
