---
name: security-threat-model-helper
description: 面向需求文档和接口说明的安全威胁建模辅助 Skill
---

# Security Threat Model Helper

## 用途

本 Skill 用于在授权项目范围内分析需求文档、接口说明和架构说明，识别潜在的认证、鉴权、数据保护、日志审计、文件上传和外联风险，输出威胁建模报告。

## 输入

- 需求说明、接口文档、架构图说明或安全需求清单
- 目标系统所属环境：仅限测试、UAT 或沙箱环境
- 评审范围和负责人

## 输出

- `security-analysis/threat-model.md`
- `security-analysis/threat-model.json`

## 安全边界

- 不执行漏洞利用。
- 不访问生产环境。
- 不调用真实业务接口。
- 不保存真实 token、cookie、session、客户数据或生产请求参数。
- 报告中出现的用户号、接口地址、事件号必须默认脱敏。

## 验证方式

- 使用示例需求文档运行后，应只输出风险分类、证据摘要和建议。
- 报告不得包含真实认证材料或可复现请求。

## 清理与回滚

本 Skill 只生成本地报告文件，不修改业务系统。清理时删除 `security-analysis/` 下本次生成的报告即可。

## 负责人和版本

- owner: security-test-team
- version: 0.1.0
- changelog: 2026-05-26 初始安全测试样例
