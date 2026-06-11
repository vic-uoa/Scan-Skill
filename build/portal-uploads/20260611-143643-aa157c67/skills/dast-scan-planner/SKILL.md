---
name: dast-scan-planner
description: 授权 Web/API 动态安全测试计划生成 Skill
---

# DAST Scan Planner

## 用途

本 Skill 用于根据授权测试范围生成 Web/API 动态安全测试计划，覆盖文件上传、接口越权、认证会话、输入校验、报告脱敏和测试回滚要求。

## 输入

- 授权测试范围说明
- 测试环境域名或 mock 地址
- 允许测试的接口列表
- 高低权测试账号说明，必须使用脱敏标识

## 输出

- `security-analysis/dast-plan.md`
- `security-analysis/dast-checklist.json`

## 安全边界

- 只生成测试计划，不直接执行扫描器。
- 不对生产环境发起请求。
- 不生成破坏性 payload。
- 不保存真实密码、token、cookie 或 session。
- 若发现输入中包含真实认证信息，应先提示脱敏再继续。

## 验证方式

- 对授权范围样例生成计划，检查是否包含测试前确认、速率限制、测试账号边界和回滚步骤。
- 对生产域名样例应输出阻断提醒。

## 清理与回滚

本 Skill 不执行测试动作，不修改远端系统。本地报告可直接删除。

## 负责人和版本

- owner: security-test-team
- version: 0.1.0
- changelog: 2026-05-26 初始安全测试样例
