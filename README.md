# 时间助手

Android 桌面小部件与应用，用于查看**即将到来的时间点**——从内置「校巴」场景出发，也支持自定义多套时间表与多种生效规则。

## 功能概览

- **多场景**：内置「校巴」场景，可新建最多 20 个自定义场景
- **多种时间表模板**
  - **单套时间表**：每天同一套时间
  - **多套时间表**：按规则生效（工作日/法定假期、每周固定星期、日期区间）
  - **北/南 · 工作日/假期**（内置校巴）：四组时刻表，支持自动/手动切换日程
- **主界面**：显示下一班、再下一班、剩余分钟；按小时分组展示全天安排
- **桌面小部件**：绑定场景，显示即将到来时间；支持刷新、切换校区/日程
- **时刻表编辑**：手动添加/删除、按小时批量编辑、重置为内置默认
- **导入**
  - **文字导入**：粘贴时刻表，支持 `8:00`、`08：30`、全角数字等
  - **图片识别**：基于 [RapidOCR Android](https://github.com/RapidAI/RapidOcrAndroidOnnx) 识别时刻表照片；对 655 线路海报有结构化解析（北/南、工作日/假期两列）
- **法定假期**：联网同步节假日数据（含内置与缓存兜底），自动判断工作日/假期
- **主题色**：橙 / 蓝 / 绿 / 紫 / 青 五套主题，全局统一

## 环境要求

| 项目 | 版本 |
|------|------|
| Android Studio | 推荐最新稳定版（支持 AGP 9.x） |
| JDK | 11+ |
| minSdk | 24 |
| targetSdk | 36 |
| Gradle | 9.4.1（Wrapper 已包含） |

## 快速开始

```bash
git clone https://github.com/Wang-jiayi0111/SchoolBusWidget.git
cd SchoolBusWidget
```

**命令行编译：**

```bash
# Windows
gradlew.bat assembleDebug

# macOS / Linux
./gradlew assembleDebug
```

或在 Android Studio 中 **Open** 项目根目录，等待 Gradle 同步完成后运行 `app`。

> OCR 引擎库 `app/libs/OcrLibrary-1.3.0-release.aar`（约 38 MB）已随仓库提交，**clone 后可直接编译**，无需额外下载。

## 使用说明

### 1. 场景与小部件

1. 打开 App，在「我的场景」中选择或新建场景
2. 进入场景主界面，查看下一班时间与全天安排
3. 长按桌面 → 添加小部件 → 选择「时间助手小部件」→ 绑定场景

### 2. 编辑时刻表

在场景主界面进入「编辑与导入时刻表」：

- **添加时间** / 长按某小时 → 批量添加或删除
- **文字导入** / **图片识别** → 解析后可「替换列表」或「合并去重」
- **保存** 写入本地；**重置** 恢复内置默认

### 3. 多套时间表（自定义场景）

新建「多套时间表」类型场景后，可添加多条规则：

| 规则类型 | 说明 |
|----------|------|
| 工作日 / 法定假期 | 按节假日数据匹配工作日或假期 |
| 每周固定星期 | 如仅周一、周三生效 |
| 日期区间 | 指定起止日期内生效 |

当日有多条规则命中时，按优先级解析生效时间表。

### 4. 校巴场景（内置）

- **校区**：北区 / 南区
- **日程**：自动（依据节假日）/ 工作日 / 假期
- 图片识别支持 655 海报，可一次识别并保存工作日与假期两列

## 项目结构

```
app/src/main/java/com/example/schoolbuswidget/
├── MainActivity.kt                 # 场景主界面
├── data/                           # DataStore、节假日、OCR 封装
├── domain/                         # 时刻表解析、下一班计算、规则解析
├── ui/
│   ├── scenario/                   # 场景列表
│   ├── schedule/                   # 多套时间表管理
│   ├── timetable/                  # 时刻表编辑与导入
│   └── theme/                      # 主题色
└── widget/                         # 桌面小部件

tools/                              # OCR 解析脚本与 golden 测试数据（开发用）
app/libs/OcrLibrary-1.3.0-release.aar  # RapidOCR 引擎（运行时依赖）
```

## 测试

```bash
gradlew.bat test
```

单元测试覆盖时刻表文字解析、节假日 JSON、655 海报结构化解析、多套时间表生效规则等。部分 golden 测试依赖 `tools/` 下的 fixture 文件，缺失时会自动跳过。

## 第三方依赖

| 组件 | 用途 | 说明 |
|------|------|------|
| [RapidOCR Android ONNX 1.3.0](https://github.com/RapidAI/RapidOcrAndroidOnnx) | 图片 OCR | 以 AAR 形式置于 `app/libs/`，随仓库分发 |
| [timor.tech 节假日 API](https://timor.tech/api/holiday/) / [holiday-cn](https://github.com/NateScarlet/holiday-cn) | 法定假期数据 | 需联网；离线时使用缓存或内置数据 |
| AndroidX、Material 3 | UI 与存储 | 见 `gradle/libs.versions.toml` |

## 数据与隐私

- 时刻表、场景、小部件配置保存在本机 **DataStore**，不上传服务器
- 联网仅用于拉取**公开节假日 JSON**（无账号、无个人数据）

## 常见问题

**Q：编译提示缺少 RapidOCR 库？**  
A：确认 `app/libs/OcrLibrary-1.3.0-release.aar` 存在。若 clone 不完整，可从 [Release 1.3.0](https://github.com/RapidAI/RapidOcrAndroidOnnx/releases/download/1.3.0/OcrLibrary-1.3.0-release.aar) 下载后放入该目录。

**Q：小部件不更新？**  
A：在 App 内点刷新，或移除小部件后重新添加。系统时间/时区变更、开机后会自动触发更新。

**Q：图片识别不准？**  
A：尽量拍摄清晰、裁剪后的时刻表区域；655 海报建议使用完整海报图，App 会自动裁剪时刻表区域再识别。

## 许可证

本项目应用代码暂未单独声明开源许可证。OCR 引擎遵循 [RapidOCR 项目](https://github.com/RapidAI/RapidOcrAndroidOnnx) 及其上游组件的许可条款，使用前请自行确认。
