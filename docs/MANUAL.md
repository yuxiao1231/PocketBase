# 📖 PocketBase 极客手册 / Technical & Usage Manual

> **版本 / Version：** v1.0.2  
> **核心属性 / Core Attributes：** 本地化 (Local) / 标准协议 (Standard Protocol) / 自动化元数据 (Automated Metadata)

PocketBase 是一个为了解决“移动端碎片化阅读与专业阅读设备隔离”而生的中间件。它通过实现标准的 OPDS 协议，将 Android 设备的本地文件系统转化为一个动态、可扫描、可交互的图书基站。

*PocketBase is a middleware designed to bridge the gap between fragmented mobile reading and professional reading devices. By implementing the standard OPDS protocol, it transforms the local file system of an Android device into a dynamic, scannable, and interactive local library server.*

---

## 📚 名词解释：什么是 OPDS？ / What is OPDS?

**OPDS (Open Publication Distribution System)** 是一种基于 Atom XML 的电子书分发协议。
*OPDS is an e-book distribution protocol based on Atom XML.*

* **它的结构 (Structure)**：它像是一份“动态菜单”。服务器生成一个 XML 文件，里面包含了一本书的标题、作者、封面链接和下载地址。
  *It acts as a "dynamic menu". The server generates an XML file containing a book's title, author, cover image link, and acquisition link.*
* **它在干嘛 (Function)**：它解决了“找书”和“传书”的解耦。阅读器不需要知道服务器里有多少文件，只需读取这份菜单，就能像逛应用商店一样预览并下载书籍。
  *It decouples the processes of "finding" and "transferring" books. The e-reader reads this menu to preview and download books, much like browsing an app store.*
* **存在的意义 (Purpose)**：把散落在手机私有目录里的“数字孤岛”整合起来，建立标准化的局域网“基站”，让支持该协议的电纸书（如 Kindle/KOReader）直接汲取资源。
  *It integrates digital silos scattered across mobile directories into a standardized local "base station," allowing e-readers supporting the protocol to seamlessly fetch resources.*

---

## 🛠️ 操作文档：从采集到降临 / Operation Guide: From Ingest to Outgest

本指南将指导你完成从 Legado 提取资源，经过 PocketBase 净化分发，最终降临到 KOReader 的全过程。
*This guide covers the process of extracting resources from Legado, distributing them via PocketBase, and accessing them in KOReader.*

### 1. 弹药采集：从 Legado 导出 / Data Ingest: Export from Legado
Legado（阅读）是强大的书源聚合器，但缓存通常是私有的。
*Legado is a powerful reading aggregator, but its cache is usually private.*
* **操作步骤 (Steps)**：打开 Legado ➡️ 进入 `我的 (Profile)` ➡️ `本地书籍 (Local Books)` ➡️ 点击右上角选择 `导出书籍 (Export)`。
* **关键点 (Tip)**：建议在手机根目录新建文件夹 `PocketBase_Library`，将导出的 `.txt` 或 `.epub` 统一存放在这里。
  *It is recommended to create a folder named `PocketBase_Library` in your phone's root directory to store all exported files.*

### 2. 基站部署：配置与元数据匹配 / Base Deployment: Config & Metadata Match
* **挂载目录 (Mount Directory)**：打开 PocketBase，点击 **[更改目录 / Change Path]**，选中刚才建立的 `PocketBase_Library`。
* **元数据匹配 (Metadata Match)**：
    * 软件会自动剔除文件名中的噪声（如 `[精校版]`、`[TXT下载]` 等）。
      *The app automatically sanitizes filenames, removing noise like promotional tags.*
    * 点击「高级操作」中的 **[联网补全书籍信息 / Match Meta]**，联网匹配标准的封面与作者信息。
* **启动服务 (Start Service)**：拨动 **[开启 OPDS 广播服务 / Start OPDS]**。获取类似 `http://192.168.1.100:8080/opds` 的坐标。

### 3. 终端接入：导入 KOReader / Terminal Access: Import to KOReader
* **环境前提 (Prerequisite)**：确保手机和阅读器在同一个 Wi-Fi 局域网下。
  *Ensure your phone and e-reader are on the same Wi-Fi network.*
* **操作步骤 (Steps)**：
    1. 打开 KOReader ➡️ 顶部菜单 ➡️ 🔍 (网络/Network) ➡️ **[OPDS 目录 / OPDS Catalog]**。
    2. 点击 **[添加新目录 / Add new catalog]**。
    3. **名称 (Name)** 填 `PocketBase`，**URL** 填入刚才获取的坐标。

---

## 🔗 资源与越狱提示 / Resources & Jailbreak Tips

* **[Legado (阅读)](https://github.com/gedoor/legado/releases)**: Android 侧最强采集端。 / *The most powerful open-source reading app on Android.*
* **[KOReader](https://github.com/koreader/koreader/releases)**: 跨平台排版之王。 / *The ultimate cross-platform document viewer for E-ink devices.*
* **Kindle 破解特别提醒 / Kindle Jailbreak Warning**:
    * 在 Kindle 上运行 KOReader 需要越狱 (Jailbreak is required)。
    * **核心提示 (Core Tip)**：千万不要随意升级系统！高版本固件软破极难。/ *DO NOT update your firmware! Jailbreaking newer firmware is extremely difficult.*
    * **权威链接 (Authoritative Link)**: [MobileRead Kindle Developers Corner](https://www.mobileread.com/forums/forumdisplay.php?f=150)

---

## ⚠️ 极客声明 / Standalone Policy & Disclaimer

1. **协议中立性 (Protocol Neutrality)**：PocketBase 仅提供标准 OPDS 协议的实现，不提供、不分发任何书籍内容。
   *PocketBase only provides the implementation of the OPDS protocol. It does not provide or distribute any book content.*
2. **解耦设计 (Decoupled Design)**：本应用不依赖于任何第三方破解方案。用户在特定硬件上的越狱或侧载风险与本应用无关。
   *This app is independent of any third-party jailbreak solutions. Users assume all risks associated with jailbreaking or sideloading on specific hardware.*
3. **隐私安全 (Privacy & Security)**：所有数据处理均在本地局域网（LAN）内完成，无云端收集。
   *All data processing is done locally within the LAN. No telemetry or cloud data collection is involved.*