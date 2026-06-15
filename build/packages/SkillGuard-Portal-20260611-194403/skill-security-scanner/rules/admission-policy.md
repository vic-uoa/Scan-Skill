# 准入策略库

本文件定义 SkillGuard 的准入判断口径。准入结论服务于最终安全报告，维护者导出的误报台账只用于优化扫描器，不应干扰普通用户阅读。

## 准入状态

| 状态 | 含义 | 默认处理 |
|:---|:---|:---|
| `PASS` | 未发现最终保留问题 | 可进入共享或上线流程 |
| `PASS_WITH_WARNINGS` | 仅有低风险治理提醒或非阻断建议 | 可通过，但建议在发布前补齐治理项 |
| `NEEDS_REVIEW` | 存在上下文不充分的问题 | 需人工复核后生成最终报告 |
| `BLOCKED` | 存在确认或高度疑似的高危/严重阻断问题 | 不建议准入，需整改或正式例外审批 |

## 判定顺序

1. 若存在 `blocking=true` 且 `decision in (confirmed, probable)` 且风险等级为 `HIGH/CRITICAL`，整体结论为 `BLOCKED`。
2. 若不存在阻断项，但存在 `needs_review`，整体结论为 `NEEDS_REVIEW`。
3. 若仅存在 `LOW/META` 治理提醒或 `low_risk_notice`，整体结论为 `PASS_WITH_WARNINGS`。
4. 若无最终保留问题，整体结论为 `PASS`。

## 人工复核后的报告口径

复核后的最终报告面向普通用户，应只展示有效问题：

- `false_positive` 不进入最终有效问题列表。
- `low_risk_notice` 作为治理提醒，不作为漏洞。
- `confirmed/probable` 进入有效问题列表。
- `needs_review` 在未处理前仍提示需要复核，不应被默认为通过。

## 维护者台账口径

维护者工具导出的误报或复核台账用于扫描器优化：

- 统计高频误报规则。
- 汇总误报类型和文件角色。
- 回灌 `false-positive-taxonomy.md`。
- 为新增过滤、降级或证据门槛规则提供样本。

普通用户不需要理解这些台账，也不应把台账当作正式安全报告。
