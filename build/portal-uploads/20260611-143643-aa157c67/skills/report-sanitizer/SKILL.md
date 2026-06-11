---
name: report-sanitizer
description: 安全测试报告脱敏检查与修订 Skill
---

# Report Sanitizer

## 用途

本 Skill 用于检查安全测试报告、接口分析报告和缺陷附件中是否残留认证上下文、用户标识、内部接口、请求参数、响应片段或可复现业务对象编号，并输出脱敏建议。

## 输入

- Markdown、JSON、HTML 或纯文本报告
- 报告流转范围：本地、团队内、跨团队或外发

## 输出

- `security-analysis/sanitized-report.md`
- `security-analysis/sanitization-findings.json`

## 安全边界

- 不上传报告内容。
- 不调用外部接口。
- 不保存原始真实凭据。
- 默认将 token、cookie、session、用户号、事件号、taskId、内网域名、请求体中的业务 ID 标记为需要脱敏。

## 验证方式

- 输入包含 `Bearer <token>` 的样例时，应识别为占位符而非真实泄露。
- 输入包含真实格式 JWT、用户号或完整内网 URL 的样例时，应输出脱敏建议。

## 清理与回滚

仅生成本地脱敏报告，不修改原始文件。需要清理时删除输出目录。

## 负责人和版本

- owner: security-test-team
- version: 0.1.0
- changelog: 2026-05-26 初始安全测试样例
