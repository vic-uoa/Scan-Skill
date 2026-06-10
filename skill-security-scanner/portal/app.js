const loginScreen = document.querySelector("#loginScreen");
const appShell = document.querySelector("#appShell");
const homePage = document.querySelector("#homePage");
const reportPage = document.querySelector("#reportPage");
const aiDialog = document.querySelector("#aiDialog");
const progressBar = document.querySelector("#progressBar");
const scanState = document.querySelector("#scanState");
const scanPercent = document.querySelector("#scanPercent");
const reportReady = document.querySelector("#reportReady");
const skillStatus = document.querySelector("#skillStatus");
const dropZone = document.querySelector("#dropZone");
const folderPicker = document.querySelector("#folderPicker");

let activeSlide = 0;
let scanTimer = null;

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

function setProgress(value, label) {
  progressBar.style.width = `${value}%`;
  scanPercent.textContent = `${value}%`;
  scanState.textContent = label;
}

function runStaticScan() {
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

function completeScan() {
  window.clearInterval(scanTimer);
  setProgress(100, "报告已生成");
  reportReady.classList.remove("is-hidden");
}

function previewFiles(files) {
  const hasSkill = Array.from(files || []).some((file) => /(^|\/|\\)SKILL\.md$/i.test(file.webkitRelativePath || file.name));
  skillStatus.textContent = hasSkill ? "发现 SKILL.md" : "未发现 SKILL.md";
  skillStatus.classList.toggle("ok", hasSkill);
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

document.querySelector("#runScan").addEventListener("click", runStaticScan);
document.querySelector("#completeScan").addEventListener("click", completeScan);

document.querySelectorAll("#openAiConfigInline").forEach((button) => {
  button.addEventListener("click", () => aiDialog.showModal());
});

folderPicker.addEventListener("change", () => previewFiles(folderPicker.files));

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

dropZone.addEventListener("drop", (event) => {
  previewFiles(event.dataTransfer.files);
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
