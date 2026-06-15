# -*- coding: utf-8 -*-
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont


ROOT = Path(r"C:\Users\vic\Desktop\hejia\skill-scan-security")
OUT = ROOT / "skillguard-static-analysis-flow.png"


def pick_font() -> str | None:
    candidates = [
        r"C:\Windows\Fonts\msyh.ttc",
        r"C:\Windows\Fonts\msyhbd.ttc",
        r"C:\Windows\Fonts\simhei.ttf",
        r"C:\Windows\Fonts\simsun.ttc",
        r"C:\Windows\Fonts\arial.ttf",
    ]
    return next((p for p in candidates if Path(p).exists()), None)


FONT_PATH = pick_font()


def font(size: int) -> ImageFont.FreeTypeFont | ImageFont.ImageFont:
    if FONT_PATH:
        return ImageFont.truetype(FONT_PATH, size)
    return ImageFont.load_default()


W, H = 1800, 1120
img = Image.new("RGB", (W, H), "#f7f8fb")
d = ImageDraw.Draw(img)

f_title = font(46)
f_sub = font(24)
f_box_title = font(26)
f_box = font(19)
f_small = font(17)

navy = "#162033"
blue = "#2563eb"
blue2 = "#dbeafe"
green = "#059669"
green2 = "#d1fae5"
amber = "#d97706"
amber2 = "#fef3c7"
red = "#dc2626"
red2 = "#fee2e2"
gray = "#475569"
line = "#94a3b8"
white = "#ffffff"


def text_width(text: str, font_obj) -> int:
    bbox = d.textbbox((0, 0), text, font=font_obj)
    return bbox[2] - bbox[0]


def wrap_text(text: str, max_width: int, font_obj) -> list[str]:
    lines: list[str] = []
    for para in text.split("\n"):
        if not para:
            lines.append("")
            continue
        cur = ""
        for ch in para:
            test = cur + ch
            if text_width(test, font_obj) <= max_width:
                cur = test
            else:
                if cur:
                    lines.append(cur)
                cur = ch
        if cur:
            lines.append(cur)
    return lines


def rounded_box(x, y, w, h, title, body, fill, outline, title_color=navy):
    d.rounded_rectangle((x, y, x + w, y + h), radius=22, fill=fill, outline=outline, width=3)
    d.text((x + 24, y + 20), title, font=f_box_title, fill=title_color)
    yy = y + 64
    for ln in wrap_text(body, w - 48, f_box):
        d.text((x + 24, yy), ln, font=f_box, fill=gray)
        yy += 29


def arrow(x1, y1, x2, y2, color=line, width=4):
    import math

    d.line((x1, y1, x2, y2), fill=color, width=width)
    ang = math.atan2(y2 - y1, x2 - x1)
    length = 18
    for a in (ang + 2.55, ang - 2.55):
        d.line(
            (x2, y2, x2 + length * math.cos(a), y2 + length * math.sin(a)),
            fill=color,
            width=width,
        )


d.text((80, 54), "SkillGuard 静态分析能力流程图", font=f_title, fill=navy)
d.text(
    (82, 112),
    "从安全知识库到最终准入结论：先发现可疑点，再结合上下文和证据确认，最后生成可整改、可审计的报告。",
    font=f_sub,
    fill=gray,
)

rounded_box(
    80,
    170,
    1640,
    120,
    "0. 安全库能力（知识底座）",
    "扫描规则库 BuiltinRules.java + 整改建议 Finding.java + 安全需求库 security-requirements.md。定义风险类型、等级、证据要求、建议模板和制度映射。",
    "#eef2ff",
    blue,
)

boxes = [
    (80, 360, 300, 160, "1. 基础扫描", "发现 Skill 目录\n收集可扫描文件\n排除无关目录", blue2, blue),
    (430, 360, 300, 160, "2. 风险召回", "使用规则库初筛\n找出 token、命令、外联\n生成候选 finding", blue2, blue),
    (780, 360, 300, 160, "3. 上下文理解", "识别文件角色\n区分文档/脚本/报告\n读取前后文窗口", green2, green),
    (1130, 360, 300, 160, "4. 证据确认", "判断真实风险证据\n识别占位符和示例\n保留可信问题", green2, green),
    (1480, 360, 240, 160, "5. 误报治理", "过滤或降级\n标记 needs_review\n记录误报原因", amber2, amber),
]
for b in boxes:
    rounded_box(*b)

for i in range(len(boxes) - 1):
    x, y, w, h = boxes[i][:4]
    nx, ny, nw, nh = boxes[i + 1][:4]
    arrow(x + w + 10, y + h / 2, nx - 10, ny + nh / 2)

lower = [
    (250, 650, 360, 170, "6. 整改建议", "结合规则类型生成建议\n说明为什么有风险\n给出可理解的修改方向", "#fff7ed", amber),
    (720, 650, 360, 170, "7. 报告生成", "输出 console / JSON / HTML / PDF\n展示分数、证据、位置\n支持人工复核和 CI", "#f0fdf4", green),
    (1190, 650, 360, 170, "8. 准入治理", "检查 SKILL.md 完整性\n判断文档与行为一致性\n形成 PASS / REVIEW / BLOCK", red2, red),
]
for b in lower:
    rounded_box(*b)

arrow(900, 290, 230, 350, color=blue, width=4)
arrow(1600, 520, 1430, 640, color=line, width=4)
arrow(610, 735, 710, 735)
arrow(1080, 735, 1180, 735)

arrow(1370, 650, 1370, 585, color=red, width=4)
d.arc((330, 245, 1530, 610), start=18, end=168, fill=red, width=4)
arrow(385, 360, 310, 294, color=red, width=4)
d.text((620, 265), "复核结果沉淀为规则优化、误报样本、安全库更新", font=f_small, fill=red)

rounded_box(
    80,
    900,
    500,
    140,
    "关键原则",
    "召回可以宽，确认必须严；不要把安全需求、测试样例、修复示例直接当成真实漏洞。",
    white,
    "#cbd5e1",
)
rounded_box(
    650,
    900,
    500,
    140,
    "当前项目定位",
    "上下文增强型静态安全准入扫描器：比关键词扫描更准，但暂不做完整 AST / 数据流语义分析。",
    white,
    "#cbd5e1",
)
rounded_box(
    1220,
    900,
    500,
    140,
    "下一步短中期重点",
    "Finding 状态分层、误报原因归类、raw/final 报告分离、静态重检闭环、准入结论。",
    white,
    "#cbd5e1",
)

d.text((80, 1070), f"输出文件：{OUT.name}", font=f_small, fill="#64748b")

img.save(OUT)
print(str(OUT))
