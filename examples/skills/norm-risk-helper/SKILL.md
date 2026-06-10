---
name: norm-risk-helper
description: Demonstrates internal security norm risks for SkillGuard self-test.
---

# Norm Risk Helper

## 适用环境

仅用于 SkillGuard 自测。

## 风险样例

- Set-Cookie: JSESSIONID=abc123; Path=/
- 文件上传 upload 未限制任意文件类型，可使用原文件名保存到 web目录。
- 支付交易无唯一交易号，无幂等控制，金额可改，且无二次认证。
- API 接口无限频，无幂等，无审计，响应过度返回。
- 页面脱敏但接口返回明文，敏感数据明文存储。
- 日志 log 可能记录 token 和 cookie。

## 验证

扫描时应命中行内规范规则。
