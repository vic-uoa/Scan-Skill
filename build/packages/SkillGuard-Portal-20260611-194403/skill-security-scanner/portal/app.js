const loginScreen = document.querySelector("#loginScreen");
const appShell = document.querySelector("#appShell");
const homePage = document.querySelector("#homePage");
const reportPage = document.querySelector("#reportPage");
const aiDialog = document.querySelector("#aiDialog");
const progressBar = document.querySelector("#progressBar");
const scanState = document.querySelector("#scanState");
const scanPercent = document.querySelector("#scanPercent");
const scanHint = document.querySelector("#scanHint");
const reportReady = document.querySelector("#reportReady");
const skillStatus = document.querySelector("#skillStatus");
const dropZone = document.querySelector("#dropZone");
const folderPicker = document.querySelector("#folderPicker");
const localPathInput = document.querySelector("#localPathInput");
const backendStatus = document.querySelector("#backendStatus");
const treePreview = document.querySelector(".tree-preview");
const downloadHtml = document.querySelector("#downloadHtml");
const downloadPdf = document.querySelector("#downloadPdf");
const downloadJson = document.querySelector("#downloadJson");
const openGeneratedReport = document.querySelector("#openGeneratedReport");
const completeScanButton = document.querySelector("#completeScan");

let activeSlide = 0;
let scanTimer = null;
let apiBase = "";
let backendReady = false;
let lastReport = null;
let currentJobId = "";
let scanInProgress = false;
let folderPreviewOnly = false;
let currentScanPath = "";

const demoTree = [
  { name: "skills/customer-risk-check", type: "dir", depth: 0 },
  { name: "SKILL.md", type: "file", depth: 1, required: true },
  { name: "scripts/run.py", type: "file", depth: 1 },
  { name: "references/security-requirements.md", type: "file", depth: 1 },
  { name: "security-analysis/report.json", type: "file", depth: 1 },
  { name: "node_modules/ 已排除", type: "skipped_dir", depth: 1 },
];

function showPortal() {
  loginScreen.classList.add("is-hidden");
  appShell.classList.remove("is-hidden");
  setRoute(location.hash.replace("#", "") || "home");
}

function showLogin() {
  appShell.classList.add("is-hidden");
  loginScreen.classList.remove("is-hidden");
}

function setRoute(route) {
  const target = route === "report" ? "report" : "home";
  homePage.classList.toggle("is-hidden", target !== "home");
  reportPage.classList.toggle("is-hidden", target !== "report");
  document.querySelectorAll("[data-route]").forEach((link) => {
    link.classList.toggle("is-active", link.dataset.route === target);
  });
  location.hash = target;
}

function setSlide(index) {
  const slides = document.querySelectorAll(".cap-slide");
  const tabs = document.querySelectorAll(".cap-tab");
  activeSlide = (index + slides.length) % slides.length;
  slides.forEach((slide, slideIndex) => {
    slide.classList.toggle("is-active", slideIndex === activeSlide);
  });
  tabs.forEach((tab, tabIndex) => {
    tab.classList.toggle("is-active", tabIndex === activeSlide);
  });
}

function setScanMode(button) {
  document.querySelectorAll(".scan-option").forEach((option) => {
    option.classList.toggle("is-selected", option === button);
  });
}

function selectedScanMode() {
  const selected = document.querySelector(".scan-option.is-selected");
  return selected ? selected.dataset.scan : "user";
}

function selectedFormat() {
  const selected = document.querySelector("input[name='format']:checked");
  return selected ? selected.value : "html";
}

function setProgress(value, label) {
  const safeValue = Math.max(0, Math.min(100, Number(value) || 0));
  progressBar.style.width = `${safeValue}%`;
  scanPercent.textContent = `${safeValue}%`;
  scanState.textContent = label;
}

function riskLabel(value) {
  const map = {
    CRITICAL: "严重",
    HIGH: "高危",
    MEDIUM: "中危",
    LOW: "低危",
    INFO: "提示",
  };
  return map[String(value || "").toUpperCase()] || value || "-";
}

function admissionLabel(value) {
  const map = {
    PASS: "通过",
    PASS_WITH_WARNINGS: "带提醒通过",
    NEEDS_REVIEW: "需要复核",
    BLOCKED: "阻断",
  };
  return map[String(value || "").toUpperCase()] || value || "-";
}

function decisionLabel(value) {
  const map = {
    confirmed: "已确认",
    probable: "疑似有效",
    needs_review: "需要复核",
    low_risk_notice: "治理提醒",
    false_positive: "误报过滤",
    BLOCKED: "阻断",
    NEEDS_REVIEW: "需要复核",
    PASS_WITH_WARNINGS: "带提醒通过",
    PASS: "通过",
  };
  return map[String(value || "")] || admissionLabel(value);
}

function renderTree(items) {
  treePreview.innerHTML = "";
  (items || demoTree).forEach((item) => {
    const row = document.createElement("div");
    row.className = "tree-row";
    if (item.depth === 0 || item.type === "dir") row.classList.add("root");
    if (item.depth > 0 || item.type === "file") row.classList.add("child");
    if (item.required || /(^|\/)SKILL\.md$/i.test(item.path || item.name)) row.classList.add("required");
    if (item.type === "skipped_dir") row.classList.add("muted");
    row.style.setProperty("--depth", String(Math.min(item.depth || 0, 5)));
    row.textContent = item.path && item.path !== "." ? item.path : item.name;
    treePreview.appendChild(row);
  });
}

function buildPreviewTree(paths) {
  const entries = new Map();
  const normalizedPaths = (paths || [])
    .filter(Boolean)
    .map((path) => String(path).replace(/\\/g, "/").replace(/^\/+/, ""))
    .filter(Boolean);

  normalizedPaths.forEach((path) => {
    const isDirMarker = path.endsWith("/");
    const parts = path.replace(/\/+$/, "").split("/").filter(Boolean);
    parts.forEach((part, index) => {
      const currentPath = parts.slice(0, index + 1).join("/");
      const isLast = index === parts.length - 1;
      const type = isLast && !isDirMarker ? "file" : "dir";
      if (!entries.has(currentPath)) {
        entries.set(currentPath, {
          name: part,
          path: currentPath,
          type,
          depth: index,
          required: /^SKILL\.md$/i.test(part),
        });
      } else if (type === "dir") {
        entries.get(currentPath).type = "dir";
      }
    });
  });

  return Array.from(entries.values())
    .sort((a, b) => {
      if (a.depth !== b.depth) return a.depth - b.depth;
      if (a.type !== b.type) return a.type === "dir" ? -1 : 1;
      return a.path.localeCompare(b.path);
    })
    .slice(0, 180);
}

function fileEntriesFromFileList(files) {
  return Array.from(files || []).map((file) => ({
    file,
    path: file.webkitRelativePath || file.name,
  }));
}

function readAllDirectoryEntries(reader) {
  return new Promise((resolve, reject) => {
    const allEntries = [];
    const readBatch = () => {
      reader.readEntries((entries) => {
        if (!entries.length) {
          resolve(allEntries);
          return;
        }
        allEntries.push(...entries);
        readBatch();
      }, reject);
    };
    readBatch();
  });
}

async function collectEntryFiles(entry, prefix = "") {
  const currentPath = `${prefix}${entry.name}`;
  if (entry.isFile) {
    return new Promise((resolve, reject) => {
      entry.file((file) => resolve([{ file, path: currentPath }]), reject);
    });
  }
  if (!entry.isDirectory) {
    return [];
  }
  const reader = entry.createReader();
  const children = await readAllDirectoryEntries(reader);
  const childFiles = await Promise.all(children.map((child) => collectEntryFiles(child, `${currentPath}/`)));
  return childFiles.flat();
}

async function collectHandleFiles(handle, prefix = "") {
  const currentPath = `${prefix}${handle.name}`;
  if (handle.kind === "file") {
    const file = await handle.getFile();
    return [{ file, path: currentPath }];
  }
  if (handle.kind !== "directory") {
    return [];
  }
  const childFiles = [];
  for await (const child of handle.values()) {
    childFiles.push(...await collectHandleFiles(child, `${currentPath}/`));
  }
  return childFiles;
}

async function fileEntriesFromDrop(event) {
  const items = Array.from(event.dataTransfer.items || []);
  const handleItems = items.filter((item) => typeof item.getAsFileSystemHandle === "function");
  if (handleItems.length) {
    const handles = (await Promise.all(handleItems.map((item) => item.getAsFileSystemHandle()))).filter(Boolean);
    if (handles.length) {
      const nestedHandles = await Promise.all(handles.map((handle) => collectHandleFiles(handle)));
      return nestedHandles.flat();
    }
  }
  const entries = items
    .map((item) => (typeof item.webkitGetAsEntry === "function" ? item.webkitGetAsEntry() : null))
    .filter(Boolean);
  if (!entries.length) {
    return fileEntriesFromFileList(event.dataTransfer.files);
  }
  const nested = await Promise.all(entries.map((entry) => collectEntryFiles(entry)));
  return nested.flat();
}

function setSkillStatus(hasSkill, text) {
  skillStatus.textContent = text || (hasSkill ? "发现 SKILL.md" : "未发现 SKILL.md");
  skillStatus.classList.toggle("ok", Boolean(hasSkill));
  skillStatus.classList.toggle("warn", !hasSkill);
}

function setReportLinks(report) {
  lastReport = report || lastReport;
  if (!lastReport) return;
  const absolute = (url) => (url && url.startsWith("/") ? `${apiBase}${url}` : url);
  const downloadable = (url) => {
    if (!url) return "#";
    const separator = url.includes("?") ? "&" : "?";
    return absolute(`${url}${separator}download=1`);
  };
  const fileName = (url, fallback) => {
    if (!url) return fallback;
    return url.split("/").pop().split("?")[0] || fallback;
  };
  if (lastReport.htmlUrl) {
    downloadHtml.href = downloadable(lastReport.htmlUrl);
    downloadHtml.setAttribute("download", fileName(lastReport.htmlUrl, "skillguard-report.html"));
    openGeneratedReport.href = absolute(lastReport.htmlUrl);
  }
  if (lastReport.pdfUrl) {
    downloadPdf.href = downloadable(lastReport.pdfUrl);
    downloadPdf.setAttribute("download", fileName(lastReport.pdfUrl, "skillguard-report.pdf"));
  }
  if (lastReport.jsonUrl) {
    downloadJson.href = downloadable(lastReport.jsonUrl);
    downloadJson.setAttribute("download", fileName(lastReport.jsonUrl, "skillguard-report.json"));
  }
}

function applyReportSummary(summary) {
  if (!summary) return;
  const cards = document.querySelectorAll(".report-grid article strong");
  if (cards[0]) cards[0].textContent = riskLabel(summary.risk);
  if (cards[1]) cards[1].textContent = summary.totalFindings ?? "-";
  if (cards[2]) cards[2].textContent = summary.blockingFindings ?? "-";
  if (cards[3]) cards[3].textContent = summary.skills ?? "-";

  const verdict = document.querySelector(".report-verdict strong");
  if (verdict) verdict.textContent = admissionLabel(summary.admission) || "已生成";

  const table = document.querySelector(".finding-table");
  if (!table || !Array.isArray(summary.findings) || summary.findings.length === 0) return;
  table.querySelectorAll(".table-row:not(.table-head)").forEach((row) => row.remove());
  summary.findings.slice(0, 6).forEach((finding) => {
    const row = document.createElement("div");
    row.className = "table-row";
    const severity = document.createElement("span");
    severity.className = `severity ${String(finding.severity || "").toLowerCase()}`;
    severity.textContent = riskLabel(finding.severity);
    const rule = document.createElement("span");
    rule.textContent = finding.rule || finding.message || "-";
    const location = document.createElement("span");
    location.textContent = `${finding.file || "-"}${finding.line ? `:${finding.line}` : ""}`;
    const decision = document.createElement("span");
    decision.textContent = decisionLabel(finding.decision || summary.admission);
    row.append(severity, rule, location, decision);
    table.appendChild(row);
  });
}

async function apiFetch(path, options = {}) {
  const response = await fetch(`${apiBase}${path}`, {
    headers: { "Content-Type": "application/json", ...(options.headers || {}) },
    ...options,
  });
  const payload = await response.json().catch(() => ({}));
  if (!response.ok || payload.ok === false) {
    throw new Error(payload.error || `HTTP ${response.status}`);
  }
  return payload;
}

async function detectBackend() {
  const candidates = location.protocol === "file:"
    ? ["http://127.0.0.1:8765", "http://localhost:8765"]
    : [""];
  for (const base of candidates) {
    try {
      apiBase = base;
      await apiFetch("/api/health");
      backendReady = true;
      backendStatus.textContent = "Portal 服务已连接，可读取真实本地目录。";
      backendStatus.classList.add("is-online");
      await loadAiConfig();
      return;
    } catch (error) {
      backendReady = false;
    }
  }
  apiBase = "";
  backendStatus.textContent = "当前为静态预览；启动 Portal 服务后启用真实扫描。";
  backendStatus.classList.remove("is-online");
}

async function uploadSelectedFolder(entries, sourceLabel = "所选目录") {
  if (!backendReady) {
    return null;
  }
  if (!entries.length) {
    scanHint.textContent = "没有读取到可上传的文件。";
    return null;
  }
  const firstPath = entries[0].path || sourceLabel;
  const rootName = firstPath.includes("/") || firstPath.includes("\\")
    ? firstPath.split(/[\\/]/)[0]
    : sourceLabel;
  currentScanPath = "";
  localPathInput.value = "";
  document.body.classList.add("is-uploading");
  scanHint.textContent = `正在上传“${rootName}”到扫描工作区...`;
  const session = await apiFetch("/api/upload/start", {
    method: "POST",
    body: JSON.stringify({ name: rootName }),
  });

  for (let index = 0; index < entries.length; index += 1) {
    const entry = entries[index];
    const path = entry.path || entry.file.name;
    const relative = path.startsWith(`${rootName}/`) ? path.slice(rootName.length + 1) : path;
    const response = await fetch(`${apiBase}/api/upload/file?session=${encodeURIComponent(session.sessionId)}&path=${encodeURIComponent(relative)}`, {
      method: "POST",
      headers: { "Content-Type": "application/octet-stream" },
      body: entry.file,
    });
    if (!response.ok) {
      const payload = await response.json().catch(() => ({}));
      throw new Error(payload.error || `上传失败 HTTP ${response.status}`);
    }
    if (index === 0 || index === entries.length - 1 || index % 8 === 0) {
      const percent = Math.round(((index + 1) / entries.length) * 100);
      scanHint.textContent = `正在上传“${rootName}”：${percent}%`;
    }
  }

  const uploaded = await apiFetch("/api/upload/finish", {
    method: "POST",
    body: JSON.stringify({ sessionId: session.sessionId }),
  });
  currentScanPath = uploaded.path;
  localPathInput.value = `已上传：${rootName}`;
  folderPreviewOnly = false;
  setSkillStatus(uploaded.hasSkillMd, uploaded.hasSkillMd ? "发现 SKILL.md" : "未发现 SKILL.md");
  renderTree(uploaded.items);
  scanHint.textContent = uploaded.hasSkillMd
    ? `已上传“${rootName}”到扫描工作区，可直接开始扫描。`
    : `已上传“${rootName}”，但未发现 SKILL.md，请确认选择的是 Skill 文件夹。`;
  document.body.classList.remove("is-uploading");
  return uploaded;
}

function runDemoScan() {
  window.clearInterval(scanTimer);
  reportReady.classList.add("is-hidden");
  let progress = 0;
  setProgress(progress, "准备文件结构");
  scanTimer = window.setInterval(() => {
    progress += progress < 35 ? 7 : progress < 72 ? 5 : 4;
    if (progress >= 100) {
      window.clearInterval(scanTimer);
      setProgress(100, "报告已生成");
      reportReady.classList.remove("is-hidden");
      return;
    }
    const label = progress < 40 ? "检查 SKILL.md" : progress < 74 ? "匹配安全规则库" : "生成报告文件";
    setProgress(progress, label);
  }, 260);
}

async function runBackendScan() {
  if (!currentScanPath) {
    setProgress(0, "等待选择目录");
    scanHint.textContent = "请先拖入或选择本地 Skill 文件夹，系统会自动填入扫描路径。";
    return;
  }
  if (folderPreviewOnly) {
    setProgress(0, "等待确认扫描路径");
    scanHint.textContent = "已生成文件树，但文件尚未成功上传到扫描工作区。请重新选择或拖入文件夹。";
    return;
  }
  window.clearInterval(scanTimer);
  reportReady.classList.add("is-hidden");
  document.body.classList.remove("scan-complete");
  document.body.classList.add("is-scanning");
  currentJobId = "";
  scanInProgress = true;
  completeScanButton.disabled = true;
  completeScanButton.classList.add("is-disabled");
  setProgress(4, "提交扫描任务");
  try {
    const job = await apiFetch("/api/scan", {
      method: "POST",
      body: JSON.stringify({
        path: currentScanPath,
        format: selectedFormat(),
        ai: selectedScanMode() === "ai",
        review: false,
      }),
    });
    currentJobId = job.id;
    completeScanButton.disabled = false;
    completeScanButton.classList.remove("is-disabled");
    pollScan(job.id);
  } catch (error) {
    document.body.classList.remove("is-scanning");
    scanInProgress = false;
    completeScanButton.disabled = false;
    completeScanButton.classList.remove("is-disabled");
    setProgress(100, "扫描失败");
    scanHint.textContent = error.message;
  }
}

function pollScan(jobId) {
  scanTimer = window.setInterval(async () => {
    try {
      const job = await apiFetch(`/api/scan/${jobId}`);
      handleScanJob(job, false);
    } catch (error) {
      window.clearInterval(scanTimer);
      document.body.classList.remove("is-scanning");
      scanInProgress = false;
      setProgress(100, "扫描失败");
      scanHint.textContent = error.message;
    }
  }, 650);
}

function handleScanJob(job, manualCheck) {
  setProgress(job.progress, job.message || job.status);
  if (job.status === "complete") {
        window.clearInterval(scanTimer);
        document.body.classList.remove("is-scanning");
        document.body.classList.add("scan-complete");
        scanInProgress = false;
        setProgress(100, "报告已生成");
        setReportLinks(job);
        applyReportSummary(job.summary);
        reportReady.querySelector("strong").textContent =
          `扫描完成：发现 ${job.summary?.totalFindings ?? 0} 个有效问题，${job.summary?.blockingFindings ?? 0} 个阻断项，${job.summary?.manualReviewFindings ?? 0} 个需复核项。`;
        reportReady.classList.remove("is-hidden");
        scanHint.textContent = "报告已写入本地 build/portal-reports，可从完成态打开或下载。";
  } else if (job.status === "failed") {
        window.clearInterval(scanTimer);
        document.body.classList.remove("is-scanning");
        scanInProgress = false;
        setProgress(100, "扫描失败");
        scanHint.textContent = job.error || "扫描任务失败";
  } else if (manualCheck) {
        scanHint.textContent = selectedScanMode() === "ai"
          ? "AI 建议仍在生成中，请等待后台任务完成；当前不会生成假完成态。"
          : "扫描仍在进行中，请等待后台任务完成。";
  }
}

function runScan() {
  if (backendReady) {
    runBackendScan();
  } else {
    runDemoScan();
  }
}

async function completeScan() {
  if (backendReady && currentJobId) {
    try {
      const job = await apiFetch(`/api/scan/${currentJobId}`);
      handleScanJob(job, true);
    } catch (error) {
      scanHint.textContent = error.message;
    }
    return;
  }
  if (backendReady) {
    scanHint.textContent = "还没有真实扫描任务。请先点击“开始扫描”。";
    return;
  }
  window.clearInterval(scanTimer);
  setProgress(100, "报告已生成");
  reportReady.classList.remove("is-hidden");
}

async function previewPaths(paths, sourceLabel = "所选目录", fileMeta = []) {
  const normalizedPaths = (paths || []).filter(Boolean);
  const hasSkill = normalizedPaths.some((path) => /(^|\/|\\)SKILL\.md$/i.test(path));
  setSkillStatus(hasSkill, hasSkill ? "发现 SKILL.md" : "未发现 SKILL.md");
  folderPreviewOnly = backendReady;
  const items = buildPreviewTree(normalizedPaths);
  if (items.length) renderTree(items);
  if (backendReady) {
    const firstPath = normalizedPaths[0] || "";
    const folderName = firstPath.includes("/") || firstPath.includes("\\")
      ? firstPath.split(/[\\/]/)[0]
      : sourceLabel;
    scanHint.textContent = `已读取“${folderName}”文件树，共 ${normalizedPaths.length} 个条目。`;
  }
}

async function previewFiles(files) {
  const entries = fileEntriesFromFileList(files);
  await previewPaths(entries.map((entry) => entry.path), "所选目录");
  try {
    await uploadSelectedFolder(entries, "所选目录");
  } catch (error) {
    document.body.classList.remove("is-uploading");
    folderPreviewOnly = true;
    scanHint.textContent = `文件树已预览，但上传到扫描工作区失败：${error.message}`;
  }
}

async function loadAiConfig() {
  if (!backendReady) return;
  try {
    const config = await apiFetch("/api/config");
    document.querySelector("#aiEndpoint").value = config.endpoint || "https://api.openai.com/v1/chat/completions";
    document.querySelector("#aiModel").value = config.model || "gpt-4.1-mini";
    document.querySelector("#aiApiKey").placeholder = config.apiKeyConfigured ? "已保存，留空则沿用" : "仅保存到本地配置";
    document.querySelector("#aiTemperature").value = config.temperature || "0.2";
    document.querySelector("#aiMaxTokens").value = config.maxTokens || "900";
    document.querySelector("#aiPolicy").value = config.organizationPolicy || document.querySelector("#aiPolicy").value;
    document.querySelector("#aiRequestBody").value = config.requestBody || "";
  } catch (error) {
    backendStatus.textContent = `AI 配置读取失败：${error.message}`;
  }
}

async function saveAiConfig(event) {
  event.preventDefault();
  const dialogStatus = document.querySelector("#aiDialogStatus");
  if (!backendReady) {
    dialogStatus.textContent = "Portal 服务未连接，暂不能测试模型连接。";
    dialogStatus.className = "dialog-status is-error";
    return;
  }
  const submitButton = aiDialog.querySelector(".dialog-actions .primary-action");
  const originalText = submitButton.textContent;
  const shouldTest = document.querySelector("#aiTestConnection").checked;
  const shouldSave = document.querySelector("#aiSaveLocal").checked;
  if (!shouldSave) {
    dialogStatus.textContent = "当前 Portal 的 AI 扫描和测试连接依赖本地配置，请先勾选保存到本地配置。";
    dialogStatus.className = "dialog-status is-error";
    return;
  }
  submitButton.disabled = true;
  submitButton.textContent = "保存中";
  dialogStatus.textContent = "正在保存本地模型配置...";
  dialogStatus.className = "dialog-status is-busy";
  try {
    await apiFetch("/api/config", {
      method: "POST",
      body: JSON.stringify({
        endpoint: document.querySelector("#aiEndpoint").value.trim(),
        model: document.querySelector("#aiModel").value.trim(),
        apiKey: document.querySelector("#aiApiKey").value.trim(),
        temperature: document.querySelector("#aiTemperature").value.trim(),
        maxTokens: document.querySelector("#aiMaxTokens").value.trim(),
        organizationPolicy: document.querySelector("#aiPolicy").value,
        requestBody: document.querySelector("#aiRequestBody").value,
      }),
    });
    if (shouldTest) {
      submitButton.textContent = "测试连接中";
      dialogStatus.textContent = "配置已保存，正在测试模型连接...";
      await apiFetch("/api/config/test", {
        method: "POST",
        body: JSON.stringify({}),
      });
      dialogStatus.textContent = "模型连接测试通过，配置已保存。";
      dialogStatus.className = "dialog-status is-ok";
      scanHint.textContent = "AI 配置已保存且连接测试通过，用户 + AI 扫描会复用该配置。";
    } else {
      dialogStatus.textContent = "AI 配置已保存，已跳过连接测试。";
      dialogStatus.className = "dialog-status is-ok";
      scanHint.textContent = "AI 配置已保存，已按开关跳过连接测试。";
    }
  } catch (error) {
    dialogStatus.textContent = shouldTest
      ? `模型连接测试失败：${error.message}`
      : `AI 配置保存失败：${error.message}`;
    dialogStatus.className = "dialog-status is-error";
    scanHint.textContent = dialogStatus.textContent;
  } finally {
    submitButton.disabled = false;
    submitButton.textContent = originalText;
  }
}

document.querySelector("#enterPortal").addEventListener("click", showPortal);
document.querySelector("#backToLogin").addEventListener("click", showLogin);
document.querySelectorAll("[data-route], [data-route-jump]").forEach((item) => {
  item.addEventListener("click", (event) => {
    const route = item.dataset.route || item.dataset.routeJump;
    if (route) {
      event.preventDefault();
      setRoute(route);
    }
  });
});

document.querySelectorAll(".cap-tab").forEach((tab) => {
  tab.addEventListener("click", () => setSlide(Number(tab.dataset.slide)));
});

document.querySelectorAll(".scan-option").forEach((button) => {
  button.addEventListener("click", () => setScanMode(button));
});

document.querySelector("#runScan").addEventListener("click", runScan);
document.querySelector("#completeScan").addEventListener("click", completeScan);

document.querySelectorAll("#openAiConfigInline").forEach((button) => {
  button.addEventListener("click", () => aiDialog.showModal());
});

aiDialog.querySelector(".dialog-actions .primary-action").addEventListener("click", saveAiConfig);
folderPicker.addEventListener("change", () => {
  previewFiles(folderPicker.files);
});

["dragenter", "dragover"].forEach((eventName) => {
  dropZone.addEventListener(eventName, (event) => {
    event.preventDefault();
    dropZone.classList.add("is-dragging");
  });
});

["dragleave", "drop"].forEach((eventName) => {
  dropZone.addEventListener(eventName, (event) => {
    event.preventDefault();
    dropZone.classList.remove("is-dragging");
  });
});

dropZone.addEventListener("drop", async (event) => {
  const droppedFiles = event.dataTransfer.files;
  scanHint.textContent = "正在读取拖拽目录结构...";
  try {
    const entries = await fileEntriesFromDrop(event);
    await previewPaths(entries.map((entry) => entry.path), "拖拽目录");
    await uploadSelectedFolder(entries, "拖拽目录");
  } catch (error) {
    document.body.classList.remove("is-uploading");
    scanHint.textContent = `拖拽目录读取失败：${error.message}`;
    await previewFiles(droppedFiles);
  }
});

window.addEventListener("hashchange", () => {
  if (!appShell.classList.contains("is-hidden")) {
    setRoute(location.hash.replace("#", "") || "home");
  }
});

window.setInterval(() => {
  if (!window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
    setSlide(activeSlide + 1);
  }
}, 5200);

renderTree(demoTree);
detectBackend();
