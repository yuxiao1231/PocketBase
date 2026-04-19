# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.2] - 2026-04-19
### Added
- **OPDS 动态封面引擎**：新增 `CoverProvider`，支持动态解析提取 EPUB 内置封面。
- **纯文本封面生成**：为 TXT 文件自动生成基于 HashCode 绑定的莫兰迪色系纯文字封面，优化接受端（如 KOReader）的视觉体验。
- **桌面快捷指令 (App Shortcuts)**：支持长按桌面图标弹出快捷菜单，可静默开启或彻底关闭 (Kill) OPDS 服务。
- **多语言基础支持**：提取桌面应用名称与快捷指令文本至 `strings.xml`，支持中英双语。

### Fixed
- **深色模式重绘拦截**：移除 `AndroidManifest.xml` 中的 `uiMode` 属性，修复切换主题时 App 无法自动加载 `DayNight` 资源的问题。
- **UI 对比度异常 (光学迷彩)**：重构了 `values-night/colors.xml` 的主色调 (Primary Color) 明度，修复了深色模式下 `OutlinedButton` 和 `TextButton`（如“更改目录”、“批量提取 txt”）文字隐形的问题。
- **状态栏/导航栏沉浸式适配**：修复了深浅色模式切换时，系统状态栏和底部导航栏出现刺眼白条的问题。

## [1.0.1] - 2026-04-XX
### Added
- 发布 PocketBase 初始正式版。
- 实现基础的本地书库目录扫描与文件映射。
- 引入 Ktor / Netty 构建本地 OPDS 广播服务端。
- 支持影子转换（Shadow Convert）：在网络传输层将本地 TXT 文件虚拟打包为 EPUB 格式下发。
- 提供基础的深浅色模式开关与后台日志监控 UI。