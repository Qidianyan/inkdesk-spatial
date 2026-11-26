# 砚境空间 · InkDesk Spatial（PICO Spatial SDK 6.2.8 原生多窗口工程）

> 2026 PICO 空间应用创作大赛（中国区 Space Program）· Spatial 空间应用创作赛道参赛开源作品（MIT License）
>
> - **1080p 工作室配音实机演示大片（YouTube）**：https://youtu.be/3Lz5gmcbS7I
> - **12 页建筑级决赛路演 PDF Deck & 高清视频直链（Release v3.0）**：https://github.com/Qidianyan/inkdesk-spatial/releases/tag/v3.0
> - **1:1 浏览器 WebXR 交互仿真伴生仓（Three.js + HRTF）**：https://github.com/Qidianyan/inkdesk-spatial-preview

## 1. 作品亮点

**《砚境空间 · InkDesk Spatial》** 是面向设计师、古建/硬件结构研究者与文博学习者的原生多窗口沉浸式空间书房，基于 **Android Kotlin Jetpack Compose + PICO Spatial SDK 6.2.8** 构建：

- **VisionOS 级浅灰钛金磨砂玻璃 × 京都桧木建筑美学**：浅石暖灰玻璃 `#EAE8E3`（烛夜深钛炭 `#171614`）、阳极钛墨 `#232220`、拉丝暖铜金 `#C98A3A`、朱砂微标 `#D64527`，严禁高疲劳蓝紫冷光。
- **三窗舒适弧面原生协同**：主舞台 A（`inkdesk_stage`）+ 左翼空间声景与专注坞 B（`inkdesk_audio_dock`）+ 右翼结构批注与手记 C（`inkdesk_notes_inspector`），由 `SpatialPanelLayoutEngine` 保证 `1.15m–2.40m` 零重叠舒适区排布。
- **4 套真实二进制 glTF 2.0（`.glb`）共享 3D 资产**：打包于 `app/src/main/assets/models/`，节点层级与 `ExplodedModelController.resolveGlbSceneBinding()` 精确一一映射：
  - `yongle_dougong.glb`（658 KB，7 构件：明代永乐大钟重檐五踩斗拱榫卯）
  - `ming_armillary.glb`（2.3 MB，6 构件：明代紫金山制式浑天仪）
  - `xuande_censer.glb`（911 KB，6 构件：宋韵明制桥耳宣德炉）
  - `origami_pagoda.glb`（371 KB，6 构件：空间折纸几何塔）

## 2. 工程目录

```text
code/inkdesk-spatial/
├── settings.gradle.kts                 # PICO Volcengine Maven 仓库与模块配置
├── build.gradle.kts                    # AGP 8.8.2 + Kotlin 2.0.0 + Compose Compiler
├── gradle/libs.versions.toml           # 官方 com.pico.spatial:bom:6.2.8 版本目录
├── app/
│   ├── build.gradle.kts                # compileSdk/minSdk 35, arm64-v8a, Spatial SDK 依赖
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml     # pico.spatial.windowcontainer.* 多窗口声明
│       │   ├── assets/models/*.glb     # 4 套真实二进制 glTF 2.0 3D 资产
│       │   └── java/com/elidot/inkdesk/
│       │       ├── app/                # InkDeskSpatialApplication, LaunchActivity, InkDeskMain
│       │       ├── model/              # InkDeskModels (4 件器物 25 构件与 GLB 节点声明)
│       │       ├── spatial/            # SpatialPanelLayoutEngine, ExplodedModelController, SpatialAudioEngine, SpatialSessionStore
│       │       └── ui/                 # StagePanel, AudioFocusDockPanel, NotesInspectorPanel, InkDeskViewModel, PicoSpatialAudioBridge, InkDeskTheme
│       └── test/java/com/elidot/inkdesk/spatial/     # 20 项 JUnit 4 单元测试（含 GLB 魔数头校验）
└── scripts/run_kotlin_domain_tests.sh  # JDK 17 + Kotlin 编译器独立实跑 20 项单测脚本
```

## 3. 快速验证与构建

### A. 零 Android SDK 环境运行 20 项纯 Kotlin 领域 + GLB 资产单测
```bash
bash scripts/run_kotlin_domain_tests.sh
```

### B. Android Studio / PICO Spatial 模拟器或 PICO Space Pro 真机构建
1. 安装 Android Studio（API 35）并配置 PICO Spatial Emulator（`pico-cli emulator setup`）。
2. 打开 `code/inkdesk-spatial/`，同步 `com.pico.spatial:bom:6.2.8` 依赖。
3. 执行 `./gradlew assembleDebug` 并使用 `pico-cli app install` 部署至模拟器或真机。

## 4. MIT 开源扩展点

- **新增 3D 器物**：将 `.glb` 资产放入 `app/src/main/assets/models/`，在 `ArtifactCatalog.exhibits` 追加 `SpatialArtifact` 及节点拆解向量即可自动接入主舞台拆解、剖切、卡尺测距与右翼批注。
- **自定义空间声景**：扩展 `SoundAnchorId` 并通过 `PicoSpatialAudioBridge.registerAnchorEntity()` 挂载至 PICO Spatial ECS 声源实体。
