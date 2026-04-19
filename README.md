# <p align="center">📚📡 PocketBase (口袋基站)</p>

<p align="center">
  <img src="docs/logo.png" width="128" height="128" alt="PocketBase Logo">
</p>

<p align="center">
  <a href="https://opensource.org/licenses/MIT"><img src="https://img.shields.io/badge/License-MIT-yellow.svg"></a>
  <a href="https://developer.android.com"><img src="https://img.shields.io/badge/Platform-Android-green.svg"></a>
</p>

<p align="center">
  <b>Make sure every book has the name it deserves.</b><br>
  <i>让每一本书都有它应有的名字。</i>
</p>

---

# 中文

**PocketBase（口袋基站）** 是一款专为极客打造的本地书籍管理与 OPDS 服务工具。它能让你的 Android 设备瞬间变身为一个高性能的电子书元数据处理中心。

## ✨ 核心特性

- 🛠 **元数据清洗**  
  自动从 Bangumi、豆瓣抓取并修正书籍信息。

- 🔄 **格式转换**  
  支持 TXT ↔ EPUB 的高质量物理双向转换。

- 🌐 **OPDS 服务**  
  内建 OPDS 服务器，为 KOReader 等阅读器提供 WiFi 无线同步。

- 🌍 **动态国际化**  
  内置 I18n 引擎，支持外挂 JSON 语言包，可无缝切换中英文。

- 🎨 **自适应设计**  
  极简 IKB（国际克莱因蓝）艺术风格图标，完美适配 Android 8.0+ Adaptive Icon。

---

## 🚀 快速开始

### 编译运行

确保你已安装 Android SDK，然后在项目根目录执行：

```bash
./gradlew assembleDebug
```

---

## 🌐 多语言贡献指南（I18n Guide）

PocketBase 支持外挂语言包，你可以在 **无需重新编译** 的情况下添加新语言。

- **路径**：  
  `/Android/data/com.xiao.pocketbase/files/langs/`

- **文件格式**：  
  创建 `[Language-Tag].json`（例如：`ja.json`）

- **结构**：  
  复制 `en.json`，将 value 翻译为目标语言即可。

---

## 📜 开源协议

本项目采用 **MIT License** 协议。

---

# English

**PocketBase** is a high‑performance local book metadata processor and OPDS server designed for Android enthusiasts. It transforms your device into a powerful hub for managing and serving ebook collections.

## ✨ Key Features

- 🛠 **Metadata Sanitization**  
  Automatically fetches and corrects metadata from Bangumi and Douban.

- 🔄 **Format Conversion**  
  High‑quality physical bidirectional conversion between TXT and EPUB.

- 🌐 **Built‑in OPDS Server**  
  Serve your library to KOReader and other readers over WiFi.

- 🌍 **Dynamic I18n**  
  Custom I18n engine with external JSON hot‑loading and instant language switching.

- 🎨 **Adaptive Design**  
  Minimalist International Klein Blue (IKB) icon style, fully compliant with Android 8.0+ Adaptive Icons.

---

## 🚀 Getting Started

### Build & Run

Ensure the Android SDK is installed, then run:

```bash
./gradlew assembleDebug
```

---

## 🌐 I18n Contribution Guide

PocketBase supports external language packs without recompilation.

- **Path**:  
  `/Android/data/com.xiao.pocketbase/files/langs/`

- **Format**:  
  Create `[Language-Tag].json` (e.g., `ja.json`)

- **Structure**:  
  Copy `en.json` and translate the values.

---

## 📜 License

This project is licensed under the **MIT License**.