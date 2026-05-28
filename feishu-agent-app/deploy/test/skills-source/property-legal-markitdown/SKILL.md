---
name: property-legal-markitdown
version: 1.0.0
description: "物业法律材料格式转换 Skill。用于在调用法律摆渡人系列 Skill 前，将 PDF、Word/DOCX、Excel/XLSX/XLS、PPT/PPTX、HTML、CSV、JSON、XML、TXT 等材料通过 Microsoft MarkItDown 转换为 Markdown；当用户提供非 Markdown/纯文本格式的物业涉法材料时使用。"
user_invocable: true
metadata:
  requires:
    bins: ["python"]
---

# 物业法律材料 MarkItDown 转换 Skill

本 Skill 是“法律摆渡人”组合库的材料预处理工具。它不做法律分析，只负责把常见文件格式转换成 Markdown，供 `property-legal-dispatcher` 或其他物业法律 Skill 使用。


## 国内网络环境安装/重装

如果用户电脑无法访问 GitHub 或外网 PyPI，不要使用 `git clone https://github.com/microsoft/markitdown`。优先使用本地安装脚本，通过国内 PyPI 镜像安装 PyPI 版本 MarkItDown 和常用解析依赖：

```bash
~/.agents/tools/markitdown/install-markitdown-cn.sh
```

可指定镜像或 Python：

```bash
PIP_INDEX_URL=https://pypi.tuna.tsinghua.edu.cn/simple PIP_EXTRA_INDEX_URL=https://mirrors.aliyun.com/pypi/simple PYTHON_BIN=python3.11 ~/.agents/tools/markitdown/install-markitdown-cn.sh
```

该脚本会安装/更新：

- `markitdown`
- PDF 解析：`pdfminer-six`、`pypdf`
- Word：`python-docx`
- Excel：`openpyxl`、`xlrd`、`pandas`
- PPT：`python-pptx`
- HTML/XML：`beautifulsoup4`、`lxml`、`markdownify`、`defusedxml`
- 基础请求库：`requests`

注意：该脚本不安装 OCR、音频、YouTube 等扩展，避免内网/比赛环境依赖过重。图片、截图和扫描件仍需单独 OCR。

## 本地工具位置

MarkItDown 已安装在本地虚拟环境：

```bash
~/.agents/tools/markitdown/.venv/bin/markitdown
```

如需 Python API：

```bash
~/.agents/tools/markitdown/.venv/bin/python
```

## 支持格式

优先支持：

- PDF：`.pdf`
- Word：`.docx`、`.doc`
- Excel：`.xlsx`、`.xls`
- PowerPoint：`.pptx`、`.ppt`
- HTML：`.html`、`.htm`
- CSV：`.csv`
- JSON：`.json`
- XML：`.xml`
- 纯文本：`.txt`、`.md`

说明：不同格式的实际转换效果取决于 MarkItDown 及其依赖。扫描版 PDF 或图片内文字通常需要 OCR，不应假装已识别。

## 不处理图片 OCR

- 图片、扫描件、截图中的文字 OCR 不由本 Skill 负责。
- 如果用户提供的是图片、截图、扫描版 PDF，先提示需要使用图片 OCR 技能/工具提取文字，再把 OCR 文本交给法律 Skill。
- 对于含图片的 Word/PPT/PDF，MarkItDown 可能只能提取可读文本和结构；图片内文字仍需 OCR。

## 基本命令

```bash
# 转换单个文件为 Markdown
~/.agents/tools/markitdown/.venv/bin/markitdown "/path/to/input.pdf" -o "/path/to/output.md"

# 自动根据原文件名输出到同目录
in="/path/to/input.docx"
out="${in%.*}.md"
~/.agents/tools/markitdown/.venv/bin/markitdown "$in" -o "$out"

# 从 stdin 读取时指定扩展名提示
cat input.csv | ~/.agents/tools/markitdown/.venv/bin/markitdown -x .csv -o output.md
```


## 强制预处理规则

- 如果用户输入是 PDF、Word、Excel、PPT、HTML、CSV、JSON、XML、TXT 等非对话文本文件，必须先转换成 Markdown，再进入法律分析。
- 不得在未转换或未读取文本的情况下直接分析文件内容。
- 如果转换结果为空、乱码、明显缺页、缺表，必须停止法律分析，提示重新提供材料或改用 OCR/其他解析方式。
- 如果材料是截图、照片、扫描件或图片型 PDF，必须提示先 OCR；本 Skill 不负责图片文字识别。

## 转换后交接模板

转换完成后必须输出：

```text
【材料转换结果】
- 原始文件：
- 文件类型：
- 输出 Markdown：
- 转换状态：成功 / 部分成功 / 失败
- 是否可能缺失图片/扫描文字：是 / 否 / 不确定
- 是否需要 OCR：是 / 否 / 不确定
- 下一步建议调用：property-legal-dispatcher

【交给法律 Skill 的材料摘要】
- 材料名称：
- 主要内容：
- 关键日期/金额/主体：
- 待人工复核项：
```

## 推荐工作流

1. 判断用户材料格式。
2. 如为 PDF/Word/Excel/PPT/HTML/CSV/JSON/XML/TXT，先用本 Skill 转为 Markdown。
3. 检查转换后的 Markdown 是否为空、乱码、明显缺页或缺表。
4. 如果材料是扫描件/图片，提示先 OCR。
5. 将转换后的 Markdown 摘要或全文交给 `property-legal-dispatcher`，由总入口自动分诊和编排法律 Skill。

## 输出给用户时说明

转换完成后，向用户报告：

```text
已转换为 Markdown：/path/to/output.md
下一步可将该 Markdown 交给 property-legal-dispatcher 进行涉法场景分诊和处置方案生成。
```

## 安全与边界

- 只转换用户明确提供或授权处理的文件。
- 不删除、不覆盖原文件。
- 默认输出到同目录或工作区新文件；如目标文件已存在，先改名或确认再覆盖。
- 转换结果只是材料文本化，不代表法律分析结论。


## 弱模型稳定执行规则（必须遵守）

1. **禁止只给原则**：必须输出可执行清单、材料清单、风险等级和下一步动作。
2. **禁止只列 Skill 名称**：如涉及组合执行，必须逐步给出每个步骤的判断结果和输出。
3. **禁止确定性法律定性**，除非用户明确提供已经法务确认的结论。不得直接说：
   - “构成违法”
   - “构成不正当竞争”
   - “物业无需承担责任”
   - “物业应当赔偿”
   - “合同可以解除”
   - “业主大会决议无效”
   - “对方必须返还”
   - “可以直接强制清理”
4. 替代表述应使用：
   - “存在……风险”
   - “需进一步核查……”
   - “建议由法务评估……”
   - “可作为内部初步判断……”
   - “不宜在未核实前作确定性表述……”
5. **一线优先**：每次输出最后必须有【你下一步只需要做什么】，用 3-7 条短句写清楚。
6. **缺材料也要先给动作**：紧急或高风险事项，不得因为材料不足而只追问；先给立即动作，再列需补充信息。
7. **引用材料要标注来源**：从用户材料中抽取的日期、金额、期限、主体、案号等，必须标注“来源：用户材料/待人工复核”。
