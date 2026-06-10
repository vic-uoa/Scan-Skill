# SkillGuard 便携版使用说明

本目录已经包含当前可运行版本：

- 版本保存点：`report-ui-compact-tools-20260601-162751`
- 主程序：`dist/skillguard.jar`
- 同步 Skill 包内置程序：`skill-security-scanner/bin/skillguard.jar`
- 示例报告：`build/example-security-skills-scan.html`

这版报告界面包含：

- 报告顶部概览卡片
- 精简模式
- 问题折叠卡片
- 人工复核闭环默认收起
- 维护者工具默认隐藏在人工复核闭环内
- AI 个性化整改建议默认收起
- 已移除“仅分析当前筛选”
- 已降低横向溢出，不需要左右拖动阅读报告

## 运行前提

目标机器只需要能运行 Java：

```powershell
java -version
```

建议 Java 8 或更高版本。

如果只是运行扫描，不需要 Maven、Gradle，也不需要联网。

## 一键扫描示例 Skill

在 PowerShell 中进入项目目录：

```powershell
cd C:\Users\vic\Desktop\hejia\skill-scan-security
```

运行：

```powershell
.\run-example-scan.ps1
```

脚本会生成：

```text
build\example-security-skills-scan.html
build\example-security-skills-scan.json
```

打开 HTML 即可查看新版报告。

## 扫描自己的 Skill 目录

```powershell
.\scan-skills.ps1 -Path C:\path\to\your\skills
```

默认输出：

```text
build\scan-report.html
build\scan-report.json
```

也可以自定义输出文件：

```powershell
.\scan-skills.ps1 -Path C:\path\to\your\skills -HtmlOutput build\my-report.html -JsonOutput build\my-report.json
```

## 直接使用 Jar

生成 HTML：

```powershell
java -jar .\dist\skillguard.jar scan .\examples\skills --format html --output .\build\example-security-skills-scan.html
```

生成 JSON：

```powershell
java -jar .\dist\skillguard.jar scan .\examples\skills --format json --output .\build\example-security-skills-scan.json
```

## 重新构建

如果修改了 Java 源码，再运行：

```powershell
.\build.ps1
```

构建产物会更新到：

```text
dist\skillguard.jar
skill-security-scanner\bin\skillguard.jar
```

## 打包给别人

直接把整个 `skill-scan-security` 文件夹压缩后发给对方即可。

对方解压后进入目录运行：

```powershell
.\run-example-scan.ps1
```

或者扫描自己的目录：

```powershell
.\scan-skills.ps1 -Path C:\path\to\skills
```

## 当前版本确认

当前 UI 保存点位于：

```text
build\checkpoints\report-ui-compact-tools-20260601-162751
```

其中保存了：

- `ReportWriter.java`
- `skillguard.jar`
- `example-security-skills-scan.html`
- `example-security-skills-scan.json`
- `checkpoint-note.txt`
