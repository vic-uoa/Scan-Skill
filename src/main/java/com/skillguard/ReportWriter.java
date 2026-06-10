package com.skillguard;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public final class ReportWriter {
    private ReportWriter() {
    }

    public static String console(ScanSummary summary) {
        StringBuilder out = new StringBuilder();
        out.append("SkillGuard 安全扫描报告\n");
        out.append("根目录: ").append(summary.root).append('\n');
        out.append("Skill 数量: ").append(summary.reports.size()).append('\n');
        out.append("扫描文件数: ").append(summary.totalFiles()).append('\n');
        out.append("问题数量: ").append(summary.totalFindings()).append('\n');
        out.append("整体风险: ").append(summary.riskLevel()).append("\n\n");
        out.append("准入结论: ").append(summary.admissionDecision()).append(" - ").append(summary.admissionReason()).append('\n');
        out.append("Raw 命中: ").append(summary.totalRawFindings())
                .append("，Final 问题: ").append(summary.totalFindings())
                .append("，已过滤: ").append(summary.totalFilteredFindings()).append("\n\n");

        Map<Severity, Integer> counts = summary.counts();
        out.append("汇总: ");
        out.append("critical=").append(counts.get(Severity.CRITICAL)).append(", ");
        out.append("high=").append(counts.get(Severity.HIGH)).append(", ");
        out.append("medium=").append(counts.get(Severity.MEDIUM)).append(", ");
        out.append("low=").append(counts.get(Severity.LOW)).append('\n');

        for (SkillReport report : summary.reports) {
            out.append("\n== ").append(report.skillName).append(" ==\n");
            out.append("综合分=").append(report.totalScore)
                    .append(" 安全分=").append(report.safetyScore)
                    .append(" 结构分=").append(report.structureScore)
                    .append(" 测试适配分=").append(report.testFitnessScore)
                    .append(" 风险=").append(report.riskLevel)
                    .append(" 文件数=").append(report.filesScanned)
                    .append(" 准入=").append(report.admissionDecision())
                    .append(" raw=").append(report.rawFindingsCount())
                    .append(" filtered=").append(report.filteredFindingsCount())
                    .append('\n');

            report.findings.stream()
                    .sorted(Comparator.comparing((Finding f) -> f.severity.rank()).reversed().thenComparing(f -> f.ruleId))
                    .forEach(finding -> {
                        out.append("[")
                                .append(finding.severity)
                                .append("] ")
                                .append(finding.ruleId)
                                .append(" ")
                                .append(finding.message)
                                .append('\n');
                        out.append("  位置: ")
                                .append(displayPath(finding.file, finding.skillName))
                                .append(finding.line > 0 ? ":" + finding.line : "")
                                .append('\n');
                        if (!finding.evidence.trim().isEmpty()) {
                            out.append("  证据: ").append(trim(finding.evidence, 180)).append('\n');
                        }
                        out.append("  判定: ").append(finding.decision)
                                .append(" / ").append(finding.decisionReason)
                                .append(" / ").append(finding.fileRole).append('\n');
                        out.append("  建议: ").append(finding.recommendation).append('\n');
                    });
        }
        return out.toString();
    }

    public static String json(ScanSummary summary) {
        StringBuilder out = new StringBuilder();
        out.append("{\n");
        field(out, 1, "schema_version", "skillguard-static-report/v2", true);
        field(out, 1, "scan_mode", "static", true);
        field(out, 1, "root", summary.root.toString(), true);
        field(out, 1, "skills_scanned", summary.reports.size(), true);
        field(out, 1, "files_scanned", summary.totalFiles(), true);
        field(out, 1, "files_skipped", summary.totalFilesSkipped(), true);
        field(out, 1, "directories_skipped", summary.totalDirectoriesSkipped(), true);
        field(out, 1, "total_findings", summary.totalFindings(), true);
        field(out, 1, "raw_findings_count", summary.totalRawFindings(), true);
        field(out, 1, "final_findings_count", summary.totalFindings(), true);
        field(out, 1, "filtered_findings_count", summary.totalFilteredFindings(), true);
        field(out, 1, "risk_level", summary.riskLevel().name().toLowerCase(), true);
        admission(out, 1, summary.admissionDecision(), summary.admissionReason(),
                summary.blockingFindingsCount(), summary.manualReviewFindingsCount(), true);
        mapField(out, 1, "decision_counts", summary.decisionCounts(), true);
        mapField(out, 1, "false_positive_counts", summary.falsePositiveCounts(), true);
        out.append("  \"skills\": [\n");
        for (int i = 0; i < summary.reports.size(); i++) {
            SkillReport report = summary.reports.get(i);
            out.append("    {\n");
            field(out, 3, "name", report.skillName, true);
            field(out, 3, "path", report.skillPath.toString(), true);
            field(out, 3, "entry_file", report.entryFile == null ? "" : displayPath(report.entryFile, report.skillName), true);
            field(out, 3, "scan_scope", report.scanScope, true);
            field(out, 3, "risk_level", report.riskLevel.name().toLowerCase(), true);
            field(out, 3, "score", report.totalScore, true);
            field(out, 3, "safety_score", report.safetyScore, true);
            field(out, 3, "structure_score", report.structureScore, true);
            field(out, 3, "test_fitness_score", report.testFitnessScore, true);
            field(out, 3, "files_scanned", report.filesScanned, true);
            field(out, 3, "files_skipped", report.filesSkipped, true);
            field(out, 3, "directories_skipped", report.directoriesSkipped, true);
            field(out, 3, "unsupported_files_skipped", report.unsupportedFilesSkipped, true);
            field(out, 3, "raw_findings_count", report.rawFindingsCount(), true);
            field(out, 3, "final_findings_count", report.findings.size(), true);
            field(out, 3, "filtered_findings_count", report.filteredFindingsCount(), true);
            admission(out, 3, report.admissionDecision(), report.admissionReason(),
                    report.blockingFindingsCount(), report.manualReviewFindingsCount(), true);
            mapField(out, 3, "decision_counts", report.decisionCounts(), true);
            mapField(out, 3, "false_positive_counts", report.falsePositiveCounts(), true);
            out.append("      \"findings\": [\n");
            for (int j = 0; j < report.findings.size(); j++) {
                Finding f = report.findings.get(j);
                out.append("        {\n");
                field(out, 5, "rule_id", f.ruleId, true);
                field(out, 5, "category", f.category, true);
                field(out, 5, "severity", f.severity.name().toLowerCase(), true);
                field(out, 5, "file", displayPath(f.file, f.skillName), true);
                field(out, 5, "line", f.line, true);
                field(out, 5, "message", f.message, true);
                field(out, 5, "evidence", f.evidence, true);
                field(out, 5, "recommendation", f.recommendation, true);
                remediationField(out, 5, f, true);
                field(out, 5, "rule_domain", SecurityKnowledgeBase.ruleDomain(f.ruleId), true);
                field(out, 5, "evidence_gate", SecurityKnowledgeBase.evidenceGate(f.ruleId), true);
                field(out, 5, "false_positive_type", SecurityKnowledgeBase.falsePositiveType(f.decisionReason), true);
                field(out, 5, "recommendation_family", SecurityKnowledgeBase.recommendationFamily(f.ruleId), true);
                field(out, 5, "admission_policy", SecurityKnowledgeBase.admissionPolicy(f), true);
                field(out, 5, "norm_source", f.normSource, true);
                field(out, 5, "evidence_type", f.evidenceType, true);
                field(out, 5, "review_status", f.reviewStatus, true);
                field(out, 5, "file_role", f.fileRole, true);
                field(out, 5, "decision", f.decision, true);
                field(out, 5, "decision_reason", f.decisionReason, true);
                field(out, 5, "context_excerpt", f.contextExcerpt, true);
                field(out, 5, "why_matched", f.whyMatched, true);
                field(out, 5, "why_kept", f.whyKept, true);
                field(out, 5, "statement_type", f.statementType, true);
                field(out, 5, "evidence_summary", f.evidenceSummary, true);
                field(out, 5, "scan_mode", f.scanMode, true);
                field(out, 5, "blocking", f.blocking ? 1 : 0, true);
                field(out, 5, "manual_review", f.manualReview ? 1 : 0, true);
                field(out, 5, "confidence", f.confidence, false);
                out.append("        }").append(j + 1 < report.findings.size() ? "," : "").append('\n');
            }
            out.append("      ]\n");
            out.append("    }").append(i + 1 < summary.reports.size() ? "," : "").append('\n');
        }
        out.append("  ]\n");
        out.append("}\n");
        return out.toString();
    }

    public static String html(ScanSummary summary, boolean reviewMode, boolean llmMode) {
        Map<Severity, Integer> counts = summary.counts();
        StringBuilder out = new StringBuilder();
        out.append("<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\">");
        out.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">");
        out.append("<title>SkillGuard 静态安全扫描报告</title>");
        out.append("<style>");
        out.append("*{box-sizing:border-box}html,body{max-width:100%;overflow-x:hidden}body{margin:0;background:#f5f7fb;color:#172033;font-family:Arial,'Microsoft YaHei',sans-serif;line-height:1.55}");
        out.append("header{background:#111827;color:#fff;padding:28px 32px}header h1{margin:0 0 8px;font-size:28px}header p{margin:0;color:#cbd5e1}");
        out.append("main{max-width:1180px;margin:0 auto;padding:22px}.section{background:#fff;border:1px solid #e5e7eb;border-radius:10px;padding:18px;margin:16px 0;box-shadow:0 1px 2px rgba(15,23,42,.04)}");
        out.append("h2{font-size:20px;margin:0 0 14px}.metrics{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:12px}.card{background:#fff;border:1px solid #e5e7eb;border-radius:10px;padding:14px}.label{color:#64748b;font-size:13px}.num{font-size:26px;font-weight:700;margin-top:5px}");
        out.append(".risk{display:inline-flex;align-items:center;border-radius:999px;padding:4px 10px;font-size:12px;font-weight:700}.CRITICAL{background:#fee2e2;color:#991b1b}.HIGH{background:#ffedd5;color:#9a3412}.MEDIUM{background:#fef9c3;color:#854d0e}.LOW{background:#e0f2fe;color:#075985}.INFO{background:#dcfce7;color:#166534}");
        out.append(".filters{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px}.filters.admin{grid-template-columns:repeat(4,minmax(0,1fr))}label span{display:block;color:#64748b;font-size:12px;margin-bottom:5px}select{width:100%;height:38px;border:1px solid #cbd5e1;border-radius:8px;background:white;padding:0 10px}");
        out.append(".skill-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(230px,1fr));gap:12px}.skill-summary-card{border:1px solid #e5e7eb;border-radius:10px;padding:14px;background:#fff}.skill-title{display:flex;justify-content:space-between;gap:10px;align-items:flex-start;font-weight:700}.score-row{display:flex;gap:8px;flex-wrap:wrap;margin-top:10px}.score-pill{font-size:12px;background:#f1f5f9;border-radius:999px;padding:3px 8px;color:#475569}");
        out.append(".skill-card{border:1px solid #e5e7eb;border-radius:12px;margin:14px 0;background:#fff;overflow:hidden}.skill-head{display:flex;justify-content:space-between;gap:12px;align-items:center;padding:16px 18px;background:#f8fafc;border-bottom:1px solid #e5e7eb}.skill-head h3{margin:0;font-size:18px}");
        out.append(".finding-row{border-top:1px solid #eef2f7}.finding-row:first-child{border-top:0}.finding-row summary{cursor:pointer;list-style:none;padding:15px 18px;display:grid;grid-template-columns:82px 1fr auto;gap:14px;align-items:center}.finding-row summary::-webkit-details-marker{display:none}.finding-title{font-weight:700}.finding-meta{color:#64748b;font-size:13px;margin-top:3px}.decision{font-size:12px;color:#64748b}");
        out.append(".finding-body{display:grid;grid-template-columns:minmax(0,1fr) minmax(320px,.85fr);gap:14px;padding:0 18px 18px}.panel{border:1px solid #e5e7eb;border-radius:10px;padding:13px;background:#fff}.panel h4{margin:0 0 8px;font-size:15px}.evidence{white-space:pre-wrap;font-family:Consolas,'Courier New',monospace;background:#f8fafc}.muted{color:#64748b;font-size:13px}.rec-block{border-top:1px dashed #e5e7eb;padding-top:10px;margin-top:10px}.rec-block:first-child{border-top:0;margin-top:0;padding-top:0}.rec-title{font-size:12px;color:#475569;font-weight:700}.rec-example code{display:block;white-space:pre-wrap;background:#f8fafc;border-radius:8px;padding:8px;margin-top:6px}");
        out.append(".review-panel{border:1px dashed #cbd5e1;border-radius:10px;padding:12px;margin-top:12px;background:#fbfdff}.review-actions{display:flex;gap:8px;flex-wrap:wrap;margin-top:8px}button{border:1px solid #cbd5e1;background:#fff;border-radius:8px;padding:7px 10px;cursor:pointer}button.primary{background:#111827;color:#fff;border-color:#111827}.hidden{display:none!important}.note{background:#f8fafc;border:1px solid #e2e8f0;border-radius:10px;padding:12px;color:#475569}.empty{padding:24px;text-align:center;color:#64748b}");
        out.append("@media(max-width:820px){header{padding:22px}main{padding:14px}.metrics,.filters,.filters.admin,.finding-body{grid-template-columns:1fr}.finding-row summary{grid-template-columns:1fr}.skill-head{display:block}}");
        out.append("</style></head><body>");
        out.append("<header><h1>SkillGuard 静态安全扫描报告</h1><p>本报告默认展示最终有效问题；维护复核信息仅在 --review 模式显示。</p></header><main>");

        out.append("<section class=\"section\"><h2>整体概览</h2><div class=\"metrics\">");
        metric(out, "整体风险", "<span class=\"risk " + summary.riskLevel() + "\">" + esc(severityLabel(summary.riskLevel())) + "</span>");
        metric(out, "准入结论", "<span class=\"risk " + admissionClass(summary.admissionDecision()) + "\">" + esc(admissionLabel(summary.admissionDecision())) + "</span>");
        metric(out, "问题数量", String.valueOf(summary.totalFindings()));
        metric(out, "Skill 数量", String.valueOf(summary.reports.size()));
        out.append("</div>");
        out.append("<div class=\"note\" style=\"margin-top:12px\">").append(esc(summary.admissionReason())).append("</div>");
        if (reviewMode) {
            out.append("<div class=\"metrics\" style=\"margin-top:12px\">");
            metric(out, "Raw 命中", String.valueOf(summary.totalRawFindings()));
            metric(out, "Final 问题", String.valueOf(summary.totalFindings()));
            metric(out, "已过滤", String.valueOf(summary.totalFilteredFindings()));
            metric(out, "需复核", String.valueOf(summary.manualReviewFindingsCount()));
            out.append("</div>");
        }
        if (llmMode) {
            out.append("<div class=\"note\" style=\"margin-top:12px\">已启用命令行 AI 个性化整改建议，整改建议已在生成阶段更新。</div>");
        }
        out.append("</section>");

        if (reviewMode) {
            out.append("<section class=\"section\"><h2>维护者复核工具</h2>");
            out.append("<p class=\"muted\">这里用于扫描器维护者在本地做误报闭环，普通用户报告默认不显示。</p>");
            out.append("<div class=\"review-actions\"><button class=\"primary\" type=\"button\" onclick=\"downloadReviewJson()\">导出复核结论 JSON</button><button type=\"button\" onclick=\"downloadFalsePositiveCsv()\">导出误报样本 CSV</button><button type=\"button\" onclick=\"clearReviewState()\">清空本页复核</button></div>");
            out.append("<div class=\"note\" id=\"reviewSummary\" style=\"margin-top:12px\">尚未产生人工复核调整。</div>");
            out.append("</section>");
        }

        out.append("<section class=\"section\"><h2>查询筛选</h2><div class=\"filters\">");
        out.append("<label><span>按 Skill 筛选</span><select id=\"skillFilter\" onchange=\"applyFilters()\"><option value=\"\">全部 Skill</option>");
        for (SkillReport report : summary.reports) {
            out.append("<option value=\"").append(attr(report.skillName)).append("\">").append(esc(report.skillName)).append("</option>");
        }
        out.append("</select></label>");
        out.append("<label><span>按风险等级筛选</span><select id=\"severityFilter\" onchange=\"applyFilters()\"><option value=\"\">全部等级</option><option value=\"CRITICAL\">严重</option><option value=\"HIGH\">高危</option><option value=\"MEDIUM\">中危</option><option value=\"LOW\">低危</option><option value=\"INFO\">信息</option></select></label>");
        out.append("<label><span>按阻断项筛选</span><select id=\"blockingFilter\" onchange=\"applyFilters()\"><option value=\"\">全部问题</option><option value=\"true\">仅阻断项</option><option value=\"false\">非阻断项</option></select></label>");
        out.append("</div>");
        if (reviewMode) {
            out.append("<div class=\"filters admin\" style=\"margin-top:12px\">");
            out.append("<label><span>确认状态</span><select id=\"statusFilter\" onchange=\"applyFilters()\"><option value=\"\">全部状态</option><option value=\"confirmed\">已确认问题</option><option value=\"probable\">可能问题</option><option value=\"needs_review\">待人工复核</option><option value=\"low_risk_notice\">低风险提醒</option></select></label>");
            out.append("<label><span>文件角色</span><select id=\"roleFilter\" onchange=\"applyFilters()\"><option value=\"\">全部角色</option><option value=\"IMPLEMENTATION\">实现代码</option><option value=\"SKILL_INSTRUCTION\">Skill 说明</option><option value=\"TEST_CASE\">测试样例</option><option value=\"ANALYSIS_REPORT\">分析报告</option><option value=\"UNKNOWN\">未知</option></select></label>");
            out.append("<label><span>误报类型</span><select id=\"reasonFilter\" onchange=\"applyFilters()\"><option value=\"\">全部原因</option></select></label>");
            out.append("<label><span>规则来源</span><select id=\"sourceFilter\" onchange=\"applyFilters()\"><option value=\"\">全部来源</option></select></label>");
            out.append("</div>");
        }
        out.append("</section>");

        out.append("<section class=\"section\"><h2>Skill 评分概览</h2><div class=\"skill-grid\" id=\"skillOverview\">");
        for (SkillReport report : summary.reports) {
            Map<Severity, Integer> skillCounts = report.counts();
            out.append("<div class=\"skill-summary-card\" data-skill=\"").append(attr(report.skillName)).append("\">");
            out.append("<div class=\"skill-title\"><span>").append(esc(report.skillName)).append("</span><span class=\"risk ").append(report.riskLevel).append("\">").append(esc(severityLabel(report.riskLevel))).append("</span></div>");
            out.append("<div class=\"score-row\"><span class=\"score-pill\">综合 ").append(report.totalScore).append("</span><span class=\"score-pill\">安全 ").append(report.safetyScore).append("</span><span class=\"score-pill\">问题 ").append(report.findings.size()).append("</span><span class=\"score-pill\">阻断 ").append(report.blockingFindingsCount()).append("</span></div>");
            out.append("<div class=\"muted\" style=\"margin-top:8px\">严重 ").append(skillCounts.get(Severity.CRITICAL)).append(" / 高危 ").append(skillCounts.get(Severity.HIGH)).append(" / 中危 ").append(skillCounts.get(Severity.MEDIUM)).append(" / 低危 ").append(skillCounts.get(Severity.LOW)).append("</div>");
            if (reviewMode) {
                out.append("<div class=\"muted\">Raw / Final / 过滤：").append(report.rawFindingsCount()).append(" / ").append(report.findings.size()).append(" / ").append(report.filteredFindingsCount()).append("</div>");
            }
            out.append("</div>");
        }
        out.append("</div></section>");

        out.append("<section class=\"section\"><h2>问题详细</h2>");
        for (SkillReport report : summary.reports) {
            out.append("<div class=\"skill-card\" data-skill-card=\"").append(attr(report.skillName)).append("\">");
            out.append("<div class=\"skill-head\"><h3>").append(esc(report.skillName)).append("</h3><div><span class=\"risk ").append(report.riskLevel).append("\">").append(esc(severityLabel(report.riskLevel))).append("</span></div></div>");
            if (report.findings.isEmpty()) {
                out.append("<div class=\"empty\">未发现最终保留问题。</div>");
            }
            report.findings.stream()
                    .sorted(Comparator.comparing((Finding f) -> f.severity.rank()).reversed().thenComparing(f -> f.ruleId))
                    .forEach(f -> appendFinding(out, report, f, reviewMode));
            out.append("</div>");
        }
        out.append("</section>");

        out.append("<script>");
        out.append("function byId(id){return document.getElementById(id)}function value(id){var el=byId(id);return el?el.value:''}");
        out.append("function applyFilters(){var skill=value('skillFilter'),sev=value('severityFilter'),block=value('blockingFilter'),status=value('statusFilter'),role=value('roleFilter'),reason=value('reasonFilter'),source=value('sourceFilter');document.querySelectorAll('.skill-summary-card').forEach(function(el){el.classList.toggle('hidden',skill&&el.dataset.skill!==skill)});document.querySelectorAll('.skill-card').forEach(function(card){var skillOk=!skill||card.dataset.skillCard===skill;var visible=0;card.querySelectorAll('.issue-item').forEach(function(item){var ok=skillOk&&(!sev||item.dataset.severity===sev)&&(!block||item.dataset.blocking===block)&&(!status||item.dataset.decision===status)&&(!role||item.dataset.role===role)&&(!reason||item.dataset.reason===reason)&&(!source||item.dataset.source===source);item.classList.toggle('hidden',!ok);if(ok)visible++;});card.classList.toggle('hidden',!skillOk||visible===0);});}");
        if (reviewMode) {
        out.append("function reviewStore(){try{return JSON.parse(localStorage.getItem('skillguard-review')||'{}')}catch(e){return {}}}function saveReview(s){localStorage.setItem('skillguard-review',JSON.stringify(s));updateReviewSummary()}");
        out.append("function setReview(btn,v){var item=btn.closest('.issue-item');var s=reviewStore();s[item.dataset.key]={review:v,rule:item.dataset.rule,location:item.dataset.location,evidence:item.dataset.evidence};item.dataset.userReview=v;item.querySelector('.manual-review-state').textContent='人工复核：'+label(v);saveReview(s)}");
        out.append("function resetReview(btn){var item=btn.closest('.issue-item');var s=reviewStore();delete s[item.dataset.key];item.dataset.userReview='';item.querySelector('.manual-review-state').textContent='人工复核：未调整';saveReview(s)}");
        out.append("function label(v){return v==='confirmed'?'确认问题':v==='false_positive'?'误报':v==='low_risk_notice'?'降为提醒':v}");
        out.append("function updateReviewSummary(){var el=byId('reviewSummary');if(!el)return;var s=reviewStore(),keys=Object.keys(s),fp=keys.filter(function(k){return s[k].review==='false_positive'}).length;el.textContent='已调整 '+keys.length+' 条，其中误报 '+fp+' 条。'}");
        out.append("function download(name,text,type){var blob=new Blob([text],{type:type||'text/plain;charset=utf-8'});var a=document.createElement('a');a.href=URL.createObjectURL(blob);a.download=name;a.click();setTimeout(function(){URL.revokeObjectURL(a.href)},1000)}");
        out.append("function downloadReviewJson(){download('skillguard-review.json',JSON.stringify(reviewStore(),null,2),'application/json;charset=utf-8')}");
        out.append("function csvCell(v){return '\"'+String(v||'').replace(/\"/g,'\"\"')+'\"'}function downloadFalsePositiveCsv(){var s=reviewStore();var rows=['说明与证据,位置,规则'];Object.keys(s).forEach(function(k){var r=s[k];if(r.review==='false_positive')rows.push([csvCell(r.evidence),csvCell(r.location),csvCell(r.rule)].join(','));});download('skillguard-false-positive-cases.csv',rows.join('\\n'),'text/csv;charset=utf-8')}");
        out.append("function clearReviewState(){localStorage.removeItem('skillguard-review');document.querySelectorAll('.manual-review-state').forEach(function(el){el.textContent='人工复核：未调整'});updateReviewSummary()}document.addEventListener('DOMContentLoaded',function(){applyFilters();updateReviewSummary()});");
        } else {
            out.append("document.addEventListener('DOMContentLoaded',function(){applyFilters()});");
        }
        out.append("</script></main></body></html>");
        return out.toString();
    }

    public static String html(ScanSummary summary) {
        return html(summary, false, false);
    }

    public static String dynamicJson(DynamicScanResult result) {
        StringBuilder out = new StringBuilder();
        out.append("{\n");
        field(out, 1, "schema_version", "skillguard-dynamic-report/v2", true);
        field(out, 1, "scan_mode", result.scanMode, true);
        field(out, 1, "skill_name", result.skillName, true);
        field(out, 1, "target", result.target == null ? "" : result.target.toString(), true);
        field(out, 1, "risk_level", result.riskLevel().name().toLowerCase(), true);
        field(out, 1, "total_findings", result.findings.size(), true);
        out.append("  \"dynamic_tests\": [\n");
        for (int i = 0; i < result.tests.size(); i++) {
            DynamicTestCase test = result.tests.get(i);
            out.append("    {\n");
            field(out, 3, "id", test.id, true);
            field(out, 3, "category", test.category, true);
            field(out, 3, "title", test.title, true);
            field(out, 3, "prompt", test.prompt, true);
            field(out, 3, "expected_safe_behavior", test.expectedSafeBehavior, true);
            field(out, 3, "failure_signal", test.failureSignal, false);
            out.append("    }").append(i + 1 < result.tests.size() ? "," : "").append('\n');
        }
        out.append("  ],\n");
        out.append("  \"findings\": [\n");
        for (int i = 0; i < result.findings.size(); i++) {
            Finding f = result.findings.get(i);
            out.append("    {\n");
            field(out, 3, "rule_id", f.ruleId, true);
            field(out, 3, "category", f.category, true);
            field(out, 3, "severity", f.severity.name().toLowerCase(), true);
            field(out, 3, "message", f.message, true);
            field(out, 3, "evidence", f.evidence, true);
            field(out, 3, "recommendation", f.recommendation, true);
            remediationField(out, 3, f, true);
            field(out, 3, "norm_source", f.normSource, true);
            field(out, 3, "review_status", f.reviewStatus, true);
            field(out, 3, "scan_mode", f.scanMode, true);
            field(out, 3, "confidence", f.confidence, false);
            out.append("    }").append(i + 1 < result.findings.size() ? "," : "").append('\n');
        }
        out.append("  ]\n");
        out.append("}\n");
        return out.toString();
    }

    public static String dynamicHtml(DynamicScanResult result) {
        StringBuilder out = new StringBuilder();
        out.append("<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">");
        out.append("<title>SkillGuard 动态安全扫描报告</title><style>");
        out.append("body{margin:0;font-family:Arial,'Microsoft YaHei',sans-serif;background:#f6f8fb;color:#172033}header{background:#12335f;color:white;padding:28px 36px}main{padding:24px 36px;max-width:1280px;margin:0 auto}.card{background:white;border:1px solid #dfe5ef;border-radius:8px;padding:16px;margin:14px 0}.risk{display:inline-block;border-radius:999px;padding:4px 10px;font-weight:700;font-size:12px}.CRITICAL{background:#fee2e2;color:#991b1b}.HIGH{background:#ffedd5;color:#9a3412}.MEDIUM{background:#fef9c3;color:#854d0e}.LOW{background:#e0f2fe;color:#075985}.INFO{background:#dcfce7;color:#166534}table{width:100%;border-collapse:collapse;background:white;border:1px solid #dfe5ef}th,td{padding:10px 12px;border-bottom:1px solid #e7ecf3;text-align:left;vertical-align:top;font-size:13px}th{background:#eef3fb}.evidence,pre{font-family:Consolas,monospace;background:#f3f4f6;padding:8px;border-radius:6px;white-space:pre-wrap}.filters{display:flex;gap:14px}.filters select{min-width:180px;padding:8px}.hidden{display:none!important}");
        out.append("</style></head><body><header><h1>SkillGuard 动态安全扫描报告</h1><div>devagent 无 API 场景下的 Prompt 计划与回填记录分析</div></header><main>");
        out.append("<section class=\"card\"><b>Skill：</b>").append(esc(result.skillName)).append(" &nbsp; <b>模式：</b>")
                .append(esc(result.scanMode)).append(" &nbsp; <b>风险：</b><span class=\"risk ").append(result.riskLevel()).append("\">")
                .append(result.riskLevel()).append("</span> &nbsp; <b>问题数：</b>").append(result.findings.size()).append("</section>");
        out.append("<section class=\"card filters\"><div><label>按等级</label><select id=\"severityFilter\" onchange=\"applyFilters()\"><option value=\"\">全部</option><option value=\"CRITICAL\">CRITICAL</option><option value=\"HIGH\">HIGH</option><option value=\"MEDIUM\">MEDIUM</option><option value=\"LOW\">LOW</option></select></div><div><label>按状态</label><select id=\"statusFilter\" onchange=\"applyFilters()\"><option value=\"\">全部</option><option value=\"confirmed\">confirmed</option><option value=\"probable\">probable</option><option value=\"needs_review\">needs_review</option></select></div></section>");
        out.append("<h2>动态测试 Prompt 包</h2><table><thead><tr><th>ID</th><th>类型</th><th>测试目标</th><th>Prompt</th><th>安全预期</th><th>失败信号</th></tr></thead><tbody>");
        for (DynamicTestCase test : result.tests) {
            out.append("<tr><td>").append(esc(test.id)).append("</td><td>").append(esc(test.category)).append("</td><td>")
                    .append(esc(test.title)).append("</td><td><pre>").append(esc(test.prompt)).append("</pre></td><td>")
                    .append(esc(test.expectedSafeBehavior)).append("</td><td>").append(esc(test.failureSignal)).append("</td></tr>");
        }
        out.append("</tbody></table><h2>动态问题明细</h2><table><thead><tr><th>等级</th><th>规则</th><th>说明与证据</th><th>建议</th></tr></thead><tbody>");
        for (Finding f : result.findings) {
            out.append("<tr class=\"finding-row\" data-severity=\"").append(f.severity).append("\" data-status=\"").append(esc(f.reviewStatus)).append("\"><td><span class=\"risk ")
                    .append(f.severity).append("\">").append(f.severity).append("</span></td><td>").append(esc(f.ruleId)).append("<br><small>")
                    .append(esc(f.category)).append("</small></td><td>").append(esc(f.message)).append("<div class=\"evidence\">")
                    .append(esc(trim(f.evidence, 420))).append("</div><small>").append(esc(f.normSource)).append(" / ")
                    .append(esc(f.reviewStatus)).append("</small></td><td>").append(recommendationCell(f)).append("</td></tr>");
        }
        out.append("</tbody></table><script>function applyFilters(){var sev=document.getElementById('severityFilter').value;var status=document.getElementById('statusFilter').value;document.querySelectorAll('.finding-row').forEach(function(row){var ok=(!sev||row.getAttribute('data-severity')===sev)&&(!status||row.getAttribute('data-status')===status);row.classList.toggle('hidden',!ok);});}</script>");
        out.append("</main></body></html>");
        return out.toString();
    }


    public static byte[] dynamicPdf(DynamicScanResult result) {
        PdfDoc doc = new PdfDoc();
        doc.title("SkillGuard 动态安全扫描报告");
        doc.subtitle("devagent 无 API 场景下的 Prompt 计划与回填记录分析");
        doc.kvTable(new String[][]{
                {"Skill", result.skillName},
                {"模式", result.scanMode},
                {"整体风险", result.riskLevel().name()},
                {"动态测试数", String.valueOf(result.tests.size())},
                {"问题数量", String.valueOf(result.findings.size())}
        });
        doc.section("动态测试 Prompt 摘要");
        List<String[]> testRows = new ArrayList<>();
        for (DynamicTestCase test : result.tests) {
            testRows.add(new String[]{test.id, test.category, test.title, test.failureSignal});
        }
        doc.table(new String[]{"ID", "类型", "目标", "失败信号"}, new int[]{55, 90, 165, 190}, testRows);
        doc.section("动态问题清单");
        List<String[]> findingRows = new ArrayList<>();
        for (Finding f : result.findings) {
            findingRows.add(new String[]{f.severity.name(), f.ruleId, f.message, f.reviewStatus});
        }
        doc.table(new String[]{"等级", "规则", "问题说明", "状态"}, new int[]{62, 72, 276, 90}, findingRows);
        doc.section("准入建议");
        doc.paragraph("动态扫描用于补充静态扫描，适合 devagent 只有界面对话、没有 API 的内网场景。高风险动态信号必须结合对话记录、生成文件和人工审批记录复核。");
        return doc.toPdf();
    }

    public static byte[] pdf(ScanSummary summary) {
        Map<Severity, Integer> counts = summary.counts();
        PdfDoc doc = new PdfDoc();
        doc.title("SkillGuard 安全扫描审计报告");
        doc.subtitle("面向银行测试团队的 Agent Skill 准入评估");
        doc.kvTable(new String[][]{
                {"扫描根目录", summary.root.toString()},
                {"整体风险", summary.riskLevel().name()},
                {"Skill 数量", String.valueOf(summary.reports.size())},
                {"扫描文件数", String.valueOf(summary.totalFiles())},
                {"问题数量", String.valueOf(summary.totalFindings())}
        });

        doc.section("风险分布");
        doc.table(new String[]{"等级", "数量", "处置建议"}, new int[]{90, 70, 340}, Arrays.asList(
                new String[]{"CRITICAL", String.valueOf(counts.get(Severity.CRITICAL)), "强阻断，必须完成修复或例外审批"},
                new String[]{"HIGH", String.valueOf(counts.get(Severity.HIGH)), "建议阻断，需安全测试或负责人复核"},
                new String[]{"MEDIUM", String.valueOf(counts.get(Severity.MEDIUM)), "纳入整改计划，评估误报与业务必要性"},
                new String[]{"LOW", String.valueOf(counts.get(Severity.LOW)), "发布前清理，保持 Skill 可维护"}
        ));

        doc.section("Skill 评分概览");
        List<String[]> scoreRows = new ArrayList<>();
        for (SkillReport report : summary.reports) {
            scoreRows.add(new String[]{
                    report.skillName,
                    report.riskLevel.name(),
                    String.valueOf(report.totalScore),
                    String.valueOf(report.safetyScore),
                    String.valueOf(report.structureScore),
                    String.valueOf(report.testFitnessScore),
                    String.valueOf(report.findings.size())
            });
        }
        doc.table(new String[]{"Skill", "风险", "综合", "安全", "结构", "测试", "问题"}, new int[]{125, 75, 50, 50, 50, 50, 50}, scoreRows);

        doc.section("高优先级问题清单");
        List<String[]> findingRows = new ArrayList<>();
        summary.reports.stream()
                .flatMap(r -> r.findings.stream())
                .filter(f -> f.severity.rank() >= Severity.HIGH.rank())
                .sorted(Comparator.comparing((Finding f) -> f.severity.rank()).reversed())
                .limit(30)
                .forEach(f -> findingRows.add(new String[]{
                        f.severity.name(),
                        f.ruleId,
                        f.skillName,
                        f.message,
                        displayPath(f.file, f.skillName) + (f.line > 0 ? ":" + f.line : "")
                }));
        doc.table(new String[]{"等级", "规则", "Skill", "问题说明", "位置"}, new int[]{62, 62, 92, 156, 128}, findingRows);

        doc.section("银行测试团队落地建议");
        doc.paragraph("建议将生产环境连接、客户敏感信息、远程脚本执行、破坏性数据库操作设为强阻断项。HTML 报告用于逐项复核，PDF 报告用于准入审批和审计归档。");
        doc.paragraph("静态扫描不能替代代码评审、沙箱试运行和安全测试；所有高风险 Skill 应在无真实凭据、无生产网络访问的隔离环境中验证。");
        return doc.toPdf();
    }

    private static String displayPath(Path path, String skillName) {
        if (path == null) {
            return "";
        }
        String normalized = path.toString().replace('\\', '/');
        if (skillName != null && !skillName.trim().isEmpty()) {
            String marker = "/" + skillName + "/";
            int index = normalized.lastIndexOf(marker);
            if (index >= 0) {
                return normalized.substring(index + 1);
            }
            if (normalized.endsWith("/" + skillName)) {
                return skillName;
            }
        }
        return path.getFileName() == null ? normalized : path.getFileName().toString();
    }

    private static void appendFinding(StringBuilder out, SkillReport report, Finding f, boolean reviewMode) {
        String location = displayPath(f.file, f.skillName) + (f.line > 0 ? ":" + f.line : "");
        String key = reviewKey(report, f);
        String source = f.normSource == null ? "" : f.normSource;
        String reason = f.decisionReason == null ? "" : f.decisionReason;
        String role = f.fileRole == null ? "UNKNOWN" : f.fileRole;
        String decision = f.decision == null ? "" : f.decision;
        String statementType = f.statementType == null ? "" : f.statementType;

        out.append("<details class=\"finding-row issue-item\"")
                .append(" data-key=\"").append(attr(key)).append("\"")
                .append(" data-skill=\"").append(attr(report.skillName)).append("\"")
                .append(" data-severity=\"").append(attr(f.severity.name())).append("\"")
                .append(" data-blocking=\"").append(f.blocking ? "true" : "false").append("\"")
                .append(" data-decision=\"").append(attr(decision)).append("\"")
                .append(" data-role=\"").append(attr(role)).append("\"")
                .append(" data-reason=\"").append(attr(reason)).append("\"")
                .append(" data-source=\"").append(attr(source)).append("\"")
                .append(" data-rule=\"").append(attr(f.ruleId)).append("\"")
                .append(" data-location=\"").append(attr(location)).append("\"")
                .append(" data-evidence=\"").append(attr(f.message + "\n" + f.evidence)).append("\">");

        out.append("<summary><span class=\"risk ").append(f.severity.name()).append("\">")
                .append(esc(severityLabel(f.severity)))
                .append("</span><div><div class=\"finding-title\">")
                .append(esc(f.message))
                .append("</div><div class=\"finding-meta\">")
                .append(esc(f.ruleId)).append(" / ").append(esc(f.category))
                .append(" &nbsp; ").append(esc(location))
                .append(" &nbsp; <span class=\"decision\">").append(esc(decisionLabel(decision))).append("</span>");
        if (reviewMode) {
            out.append(" &nbsp; ").append(esc(role));
        }
        out.append("</div></div><div class=\"muted\">&#23637;&#24320;</div></summary>");

        out.append("<div class=\"finding-body\"><div class=\"panel\"><h4>&#35777;&#25454;&#19982;&#21028;&#23450;</h4>");
        out.append("<div class=\"evidence\">").append(esc(trim(f.evidence, 520))).append("</div>");
        out.append("<p class=\"muted\">");
        out.append("&#35268;&#21017;&#22495;&#65306;").append(esc(SecurityKnowledgeBase.ruleDomain(f.ruleId)));
        out.append("<br>&#35777;&#25454;&#38376;&#27099;&#65306;").append(esc(SecurityKnowledgeBase.evidenceGate(f.ruleId)));
        out.append("<br>&#21028;&#23450;&#21407;&#22240;&#65306;").append(esc(reason));
        out.append("<br>&#25991;&#20214;&#35282;&#33394;&#65306;").append(esc(role));
        if (!statementType.trim().isEmpty()) {
            out.append("<br>&#35821;&#21477;&#31867;&#22411;&#65306;").append(esc(statementType));
        }
        if (!f.evidenceSummary.trim().isEmpty()) {
            out.append("<br>&#35777;&#25454;&#30830;&#35748;&#65306;").append(esc(f.evidenceSummary));
        }
        out.append("</p>");
        if (reviewMode) {
            appendDebugLine(out, "&#21629;&#20013;&#21407;&#22240;", f.whyMatched);
            appendDebugLine(out, "&#20445;&#30041;&#21407;&#22240;", f.whyKept);
            appendDebugLine(out, "&#19978;&#19979;&#25991;&#25688;&#24405;", f.contextExcerpt);
        }
        out.append("</div><div class=\"panel\"><h4>&#25972;&#25913;&#24314;&#35758;</h4>")
                .append(recommendationCell(f))
                .append("</div></div>");

        if (reviewMode) {
            out.append("<div class=\"review-panel\"><h4>&#20154;&#24037;&#22797;&#26680;</h4>")
                    .append(reviewCell(f))
                    .append("<div class=\"manual-review-state\">&#20154;&#24037;&#22797;&#26680;&#65306;&#26410;&#35843;&#25972;</div>")
                    .append("</div>");
        }
        out.append("</details>");
    }

    private static void appendDebugLine(StringBuilder out, String label, String value) {
        if (value == null || value.trim().isEmpty()) {
            return;
        }
        out.append("<p class=\"muted\"><b>").append(label).append("&#65306;</b>")
                .append(esc(trim(value, 420))).append("</p>");
    }

    private static void metric(StringBuilder out, String label, String value) {
        out.append("<div class=\"card\"><div class=\"label\">").append(esc(label)).append("</div><div class=\"num\">")
                .append(value).append("</div></div>");
    }

    private static String score(int value) {
        return "<div>" + value + "</div><div class=\"bar\"><div class=\"fill\" style=\"width:" + value + "%\"></div></div>";
    }

    private static String severityLabel(Severity severity) {
        if (severity == Severity.CRITICAL) {
            return "严重";
        }
        if (severity == Severity.HIGH) {
            return "高危";
        }
        if (severity == Severity.MEDIUM) {
            return "中危";
        }
        if (severity == Severity.LOW) {
            return "低危";
        }
        return "信息";
    }

    private static String decisionLabel(String decision) {
        if ("confirmed".equals(decision)) {
            return "已确认问题";
        }
        if ("probable".equals(decision)) {
            return "可能问题";
        }
        if ("needs_review".equals(decision)) {
            return "待人工复核";
        }
        if ("low_risk_notice".equals(decision)) {
            return "低风险提醒";
        }
        if ("false_positive".equals(decision)) {
            return "已标记误报";
        }
        return decision == null ? "" : decision;
    }

    private static String recommendationCell(Finding finding) {
        RecommendationParts parts = splitRecommendation(finding.recommendation);
        StringBuilder out = new StringBuilder();
        out.append("<div class=\"remediation\">");
        if (!parts.action.trim().isEmpty()) {
            out.append("<div class=\"rec-block\"><span class=\"rec-title\">优先处置</span><p class=\"rec-text\">")
                    .append(esc(parts.action).replace("\n", "<br>"))
                    .append("</p></div>");
        }
        if (!parts.context.trim().isEmpty()) {
            out.append("<div class=\"rec-block\"><span class=\"rec-title\">场景化建议</span><p class=\"rec-text\">")
                    .append(esc(parts.context).replace("\n", "<br>"))
                    .append("</p></div>");
        }
        if (!parts.example.trim().isEmpty()) {
            out.append("<div class=\"rec-block rec-example\"><span class=\"rec-title\">修改例子</span><code>")
                    .append(esc(parts.example))
                    .append("</code></div>");
        }
        out.append("<div class=\"rec-meta\"><span class=\"rec-chip\">")
                .append(esc(SecurityKnowledgeBase.recommendationFamily(finding.ruleId)))
                .append("</span><span class=\"rec-chip\">")
                .append(esc(SecurityKnowledgeBase.admissionPolicy(finding)))
                .append("</span></div>");
        out.append("</div>");
        return out.toString();
    }

    private static void remediationField(StringBuilder out, int indent, Finding finding, boolean comma) {
        RecommendationParts parts = splitRecommendation(finding.recommendation);
        appendSpaces(out, indent).append("\"remediation\": {\n");
        field(out, indent + 1, "priority_action", parts.action, true);
        field(out, indent + 1, "contextual_advice", parts.context, true);
        field(out, indent + 1, "example", parts.example, true);
        field(out, indent + 1, "family", SecurityKnowledgeBase.recommendationFamily(finding.ruleId), true);
        field(out, indent + 1, "admission_policy", SecurityKnowledgeBase.admissionPolicy(finding), false);
        appendSpaces(out, indent).append("}").append(comma ? "," : "").append('\n');
    }

    private static RecommendationParts splitRecommendation(String recommendation) {
        RecommendationParts parts = new RecommendationParts();
        if (recommendation == null || recommendation.trim().isEmpty()) {
            return parts;
        }
        String[] lines = recommendation.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        StringBuilder action = new StringBuilder();
        StringBuilder context = new StringBuilder();
        StringBuilder example = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("场景化建议:")) {
                appendLine(context, trimmed.substring("场景化建议:".length()).trim());
            } else if (trimmed.startsWith("场景化建议：")) {
                appendLine(context, trimmed.substring("场景化建议：".length()).trim());
            } else if (trimmed.startsWith("示例:")) {
                appendLine(example, trimmed.substring("示例:".length()).trim());
            } else if (trimmed.startsWith("示例：")) {
                appendLine(example, trimmed.substring("示例：".length()).trim());
            } else {
                appendLine(action, trimmed);
            }
        }
        parts.action = action.toString();
        parts.context = context.toString();
        parts.example = example.toString();
        return parts;
    }

    private static void appendLine(StringBuilder out, String value) {
        if (value == null || value.trim().isEmpty()) {
            return;
        }
        if (out.length() > 0) {
            out.append('\n');
        }
        out.append(value.trim());
    }

    private static String reviewCell(Finding finding) {
        String decision = finding.decision == null ? "" : finding.decision;
        StringBuilder out = new StringBuilder();
        if ("needs_review".equals(decision)) {
            out.append("<span class=\"review-badge review-required\">必须复核</span>")
                    .append("<small class=\"review-help\">扫描器证据不足或语境存在不确定性，需要人工给出最终结论。</small>")
                    .append("<div class=\"review-actions\">")
                    .append("<button type=\"button\" onclick=\"setReview(this,'confirmed')\">确认问题</button>")
                    .append("<button type=\"button\" onclick=\"setReview(this,'false_positive')\">标记误报</button>")
                    .append("<button type=\"button\" onclick=\"setReview(this,'low_risk_notice')\">降为提醒</button>")
                    .append("<button type=\"button\" onclick=\"resetReview(this)\">撤销</button>")
                    .append("</div>");
        } else if ("probable".equals(decision)) {
            out.append("<span class=\"review-badge review-suggested\">建议复核</span>")
                    .append("<small class=\"review-help\">可能是真实问题，默认进入报告；有争议时再人工改判。</small>")
                    .append("<div class=\"review-actions\">")
                    .append("<button type=\"button\" onclick=\"setReview(this,'confirmed')\">确认问题</button>")
                    .append("<button type=\"button\" onclick=\"setReview(this,'false_positive')\">标记误报</button>")
                    .append("<button type=\"button\" onclick=\"setReview(this,'low_risk_notice')\">降为提醒</button>")
                    .append("<button type=\"button\" onclick=\"resetReview(this)\">撤销</button>")
                    .append("</div>");
        } else if ("confirmed".equals(decision)) {
            out.append("<span class=\"review-badge review-none\">默认确认</span>")
                    .append("<small class=\"review-help\">证据较强，无需逐条人工复核；仅在确认误报时改判。</small>")
                    .append("<div class=\"review-actions secondary minimal\">")
                    .append("<button type=\"button\" onclick=\"setReview(this,'false_positive')\">标记误报</button>")
                    .append("<button type=\"button\" onclick=\"setReview(this,'low_risk_notice')\">降为提醒</button>")
                    .append("<button type=\"button\" onclick=\"resetReview(this)\">撤销</button>")
                    .append("</div>");
        } else if ("low_risk_notice".equals(decision)) {
            out.append("<span class=\"review-badge review-optional\">治理提醒</span>")
                    .append("<small class=\"review-help\">默认不要求复核，不作为阻断；需要时可改为误报或确认问题。</small>")
                    .append("<div class=\"review-actions secondary minimal\">")
                    .append("<button type=\"button\" onclick=\"setReview(this,'false_positive')\">忽略提醒</button>")
                    .append("<button type=\"button\" onclick=\"setReview(this,'confirmed')\">确认问题</button>")
                    .append("<button type=\"button\" onclick=\"resetReview(this)\">撤销</button>")
                    .append("</div>");
        } else {
            out.append("<span class=\"review-badge review-optional\">可选复核</span>")
                    .append("<div class=\"review-actions secondary minimal\">")
                    .append("<button type=\"button\" onclick=\"setReview(this,'confirmed')\">确认问题</button>")
                    .append("<button type=\"button\" onclick=\"setReview(this,'false_positive')\">标记误报</button>")
                    .append("<button type=\"button\" onclick=\"resetReview(this)\">撤销</button>")
                    .append("</div>");
        }
        out.append("<small class=\"review-state\">人工复核：").append(esc(decisionLabel(decision))).append("</small>");
        return out.toString();
    }

    private static final class RecommendationParts {
        private String action = "";
        private String context = "";
        private String example = "";
    }

    private static String admissionLabel(String decision) {
        if ("BLOCKED".equals(decision)) {
            return "阻断";
        }
        if ("NEEDS_REVIEW".equals(decision)) {
            return "待复核";
        }
        if ("PASS_WITH_WARNINGS".equals(decision)) {
            return "带提醒通过";
        }
        return "通过";
    }

    private static String reviewKey(SkillReport report, Finding finding) {
        return report.skillName + "|"
                + finding.ruleId + "|"
                + displayPath(finding.file, finding.skillName) + "|"
                + finding.line + "|"
                + Integer.toHexString((finding.message + finding.evidence).hashCode());
    }

    private static String admissionClass(String decision) {
        if ("BLOCKED".equals(decision)) {
            return "CRITICAL";
        }
        if ("NEEDS_REVIEW".equals(decision)) {
            return "HIGH";
        }
        if ("PASS_WITH_WARNINGS".equals(decision)) {
            return "LOW";
        }
        return "INFO";
    }

    private static String scanScopeLabel(String scanScope) {
        if ("STANDARD_SKILL".equals(scanScope)) {
            return "标准 Skill";
        }
        if ("LOWERCASE_SKILL_ENTRY".equals(scanScope)) {
            return "兼容小写入口";
        }
        if ("DIRECTORY_WITHOUT_SKILL_ENTRY".equals(scanScope)) {
            return "目录扫描";
        }
        if ("SINGLE_FILE".equals(scanScope)) {
            return "单文件扫描";
        }
        return scanScope == null || scanScope.trim().isEmpty() ? "未知范围" : scanScope;
    }

    private static String esc(String value) {
        return value == null ? "" : value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String attr(String value) {
        return esc(value)
                .replace("\r\n", "&#10;")
                .replace("\r", "&#10;")
                .replace("\n", "&#10;");
    }

    private static String trim(String text, int max) {
        String oneLine = text.replace('\t', ' ').replaceAll("\\s+", " ").trim();
        return oneLine.length() <= max ? oneLine : oneLine.substring(0, max - 3) + "...";
    }

    private static void field(StringBuilder out, int indent, String name, String value, boolean comma) {
        appendSpaces(out, indent).append('"').append(escape(name)).append("\": \"").append(escape(value)).append('"');
        out.append(comma ? ",\n" : "\n");
    }

    private static void field(StringBuilder out, int indent, String name, int value, boolean comma) {
        appendSpaces(out, indent).append('"').append(escape(name)).append("\": ").append(value);
        out.append(comma ? ",\n" : "\n");
    }

    private static void field(StringBuilder out, int indent, String name, double value, boolean comma) {
        appendSpaces(out, indent).append('"').append(escape(name)).append("\": ").append(String.format(java.util.Locale.ROOT, "%.2f", value));
        out.append(comma ? ",\n" : "\n");
    }

    private static void admission(StringBuilder out, int indent, String decision, String reason,
                                  int blockingCount, int manualReviewCount, boolean comma) {
        appendSpaces(out, indent).append("\"admission\": {\n");
        field(out, indent + 1, "decision", decision, true);
        field(out, indent + 1, "reason", reason, true);
        field(out, indent + 1, "blocking_findings", blockingCount, true);
        field(out, indent + 1, "manual_review_findings", manualReviewCount, false);
        appendSpaces(out, indent).append("}").append(comma ? ",\n" : "\n");
    }

    private static void mapField(StringBuilder out, int indent, String name, Map<String, Integer> values, boolean comma) {
        appendSpaces(out, indent).append('"').append(escape(name)).append("\": {\n");
        int i = 0;
        for (Map.Entry<String, Integer> entry : values.entrySet()) {
            appendSpaces(out, indent + 1).append('"').append(escape(entry.getKey())).append("\": ").append(entry.getValue());
            out.append(++i < values.size() ? ",\n" : "\n");
        }
        appendSpaces(out, indent).append("}").append(comma ? ",\n" : "\n");
    }

    private static StringBuilder appendSpaces(StringBuilder out, int indent) {
        for (int i = 0; i < Math.max(0, indent); i++) {
            out.append("  ");
        }
        return out;
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder escaped = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"':
                    escaped.append("\\\"");
                    break;
                case '\\':
                    escaped.append("\\\\");
                    break;
                case '\b':
                    escaped.append("\\b");
                    break;
                case '\f':
                    escaped.append("\\f");
                    break;
                case '\n':
                    escaped.append("\\n");
                    break;
                case '\r':
                    escaped.append("\\r");
                    break;
                case '\t':
                    escaped.append("\\t");
                    break;
                default:
                    if (c < 0x20 || Character.isSurrogate(c)) {
                        if (Character.isHighSurrogate(c)
                                && i + 1 < value.length()
                                && Character.isLowSurrogate(value.charAt(i + 1))) {
                            escaped.append(c).append(value.charAt(++i));
                        } else {
                            escaped.append(String.format("\\u%04x", (int) c));
                        }
                    } else {
                        escaped.append(c);
                    }
                    break;
            }
        }
        return escaped.toString();
    }

    private static byte[] writePdf(List<String> pageStreams) {
        List<byte[]> objects = new ArrayList<>();
        objects.add("<< /Type /Catalog /Pages 2 0 R >>".getBytes(StandardCharsets.ISO_8859_1));
        StringBuilder kids = new StringBuilder("[");
        int firstPageObj = 4;
        for (int i = 0; i < pageStreams.size(); i++) {
            kids.append(firstPageObj + i * 2).append(" 0 R ");
        }
        kids.append("]");
        objects.add(("<< /Type /Pages /Kids " + kids + " /Count " + pageStreams.size() + " >>").getBytes(StandardCharsets.ISO_8859_1));
        objects.add(("<< /Type /Font /Subtype /Type0 /BaseFont /STSong-Light /Encoding /UniGB-UCS2-H "
                + "/DescendantFonts [<< /Type /Font /Subtype /CIDFontType0 /BaseFont /STSong-Light "
                + "/CIDSystemInfo << /Registry (Adobe) /Ordering (GB1) /Supplement 2 >> >>] >>").getBytes(StandardCharsets.ISO_8859_1));

        for (int i = 0; i < pageStreams.size(); i++) {
            int pageObj = firstPageObj + i * 2;
            int contentObj = pageObj + 1;
            objects.add(("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] "
                    + "/Resources << /Font << /F1 3 0 R >> >> /Contents " + contentObj + " 0 R >>")
                    .getBytes(StandardCharsets.ISO_8859_1));
            String stream = pageStreams.get(i);
            byte[] streamBytes = stream.getBytes(StandardCharsets.ISO_8859_1);
            objects.add(("<< /Length " + streamBytes.length + " >>\nstream\n" + stream + "\nendstream")
                    .getBytes(StandardCharsets.ISO_8859_1));
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeAscii(out, "%PDF-1.4\n");
        List<Integer> offsets = new ArrayList<>();
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(out.size());
            writeAscii(out, (i + 1) + " 0 obj\n");
            writeBytes(out, objects.get(i));
            writeAscii(out, "\nendobj\n");
        }
        int xref = out.size();
        writeAscii(out, "xref\n0 " + (objects.size() + 1) + "\n");
        writeAscii(out, "0000000000 65535 f \n");
        for (int offset : offsets) {
            writeAscii(out, String.format(Locale.ROOT, "%010d 00000 n \n", offset));
        }
        writeAscii(out, "trailer\n<< /Size " + (objects.size() + 1) + " /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF\n");
        return out.toByteArray();
    }

    private static String hexUtf16(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_16BE);
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            hex.append(String.format(Locale.ROOT, "%02X", b & 0xff));
        }
        return hex.toString();
    }

    private static List<String> wrap(String text, int maxChars) {
        List<String> result = new ArrayList<>();
        String value = text == null ? "" : text;
        while (value.length() > maxChars) {
            result.add(value.substring(0, maxChars));
            value = "  " + value.substring(maxChars);
        }
        result.add(value);
        return result;
    }

    private static void writeAscii(ByteArrayOutputStream out, String value) {
        writeBytes(out, value.getBytes(StandardCharsets.ISO_8859_1));
    }

    private static void writeBytes(ByteArrayOutputStream out, byte[] bytes) {
        out.write(bytes, 0, bytes.length);
    }

    private static final class PdfDoc {
        private static final int PAGE_W = 595;
        private static final int PAGE_H = 842;
        private static final int LEFT = 46;
        private static final int TOP = 790;
        private static final int BOTTOM = 54;

        private final List<StringBuilder> pages = new ArrayList<>();
        private StringBuilder content = new StringBuilder();
        private int y = TOP;

        PdfDoc() {
            pages.add(content);
        }

        void title(String text) {
            text(text, LEFT, y, 22);
            y -= 30;
        }

        void subtitle(String text) {
            text(text, LEFT, y, 11);
            y -= 30;
        }

        void section(String text) {
            ensure(34);
            y -= 8;
            text(text, LEFT, y, 15);
            line(LEFT, y - 7, PAGE_W - LEFT, y - 7);
            y -= 24;
        }

        void paragraph(String text) {
            for (String line : wrap(text, 54)) {
                ensure(18);
                text(line, LEFT, y, 10);
                y -= 16;
            }
            y -= 4;
        }

        void kvTable(String[][] rows) {
            table(new String[]{"项目", "内容"}, new int[]{110, 390}, Arrays.asList(rows));
        }

        void table(String[] headers, int[] widths, List<String[]> rows) {
            int rowHeight = 28;
            ensure(rowHeight * 2);
            drawRow(headers, widths, true, rowHeight);
            for (String[] row : rows) {
                ensure(rowHeight);
                drawRow(row, widths, false, rowHeight);
            }
            y -= 12;
        }

        byte[] toPdf() {
            int total = pages.size();
            for (int i = 0; i < total; i++) {
                StringBuilder page = pages.get(i);
                text(page, "第 " + (i + 1) + " / " + total + " 页", PAGE_W - 110, 30, 9);
            }
            List<String> streams = pages.stream().map(StringBuilder::toString).collect(Collectors.toList());
            return writePdf(streams);
        }

        private void drawRow(String[] cells, int[] widths, boolean header, int height) {
            int x = LEFT;
            rect(LEFT, y - height + 8, sum(widths), height);
            for (int i = 0; i < widths.length; i++) {
                if (i > 0) {
                    line(x, y + 8, x, y - height + 8);
                }
                String cell = i < cells.length ? cells[i] : "";
                int chars = Math.max(4, widths[i] / 10);
                List<String> wrapped = wrap(cell, chars);
                int textY = y - 8;
                int maxLines = header ? 1 : 2;
                for (int j = 0; j < Math.min(maxLines, wrapped.size()); j++) {
                    String value = wrapped.get(j);
                    if (j == maxLines - 1 && wrapped.size() > maxLines) {
                        value = value.length() > 2 ? value.substring(0, Math.max(1, value.length() - 1)) + "…" : value;
                    }
                    text(value, x + 5, textY - (j * 11), header ? 10 : 9);
                }
                x += widths[i];
            }
            y -= height;
        }

        private void ensure(int needed) {
            if (y - needed < BOTTOM) {
                content = new StringBuilder();
                pages.add(content);
                y = TOP;
            }
        }

        private void text(String value, int x, int y, int size) {
            text(content, value, x, y, size);
        }

        private void text(StringBuilder page, String value, int x, int y, int size) {
            page.append("BT\n/F1 ").append(size).append(" Tf\n")
                    .append(x).append(" ").append(y).append(" Td\n<")
                    .append(hexUtf16(value)).append("> Tj\nET\n");
        }

        private void line(int x1, int y1, int x2, int y2) {
            content.append(x1).append(" ").append(y1).append(" m ")
                    .append(x2).append(" ").append(y2).append(" l S\n");
        }

        private void rect(int x, int y, int w, int h) {
            content.append(x).append(" ").append(y).append(" ").append(w).append(" ").append(h).append(" re S\n");
        }

        private int sum(int[] values) {
            int sum = 0;
            for (int value : values) {
                sum += value;
            }
            return sum;
        }
    }
}



