# CandyIDE

Sublimation（凝华）语言的安卓端 IDE。

## 特性（MVP）

- 工程列表 / 新建工程 / 删除工程
- 工程内编辑界面：SoraEditor 代码编辑器（行号、语法高亮、括号配对、自动补全）
- 左侧抽屉：文件树（目录展开/收起，点击文件编辑）
- 右侧抽屉：运行日志输出
- 一键运行：进程内调用 Sublimation 脚本模式解释器，`print` 输出重定向到日志面板
- 标准库：构建时从 `../sublimation/core` 同步进 `assets/stdlib`，首启拷到应用私有目录

## 架构

```
CandyIDE (Android, Compose + SoraEditor)
  └─ 进程内直连
sublimation (../sublimation, 复合构建 includeBuild)
  ├─ frontend       Lexer / Parser / AST
  ├─ compiler       Checker / 类型推导 / 模块系统
  ├─ interpreter    脚本模式解释器（run / runScript / runDir）
  └─ language-service  LSP 分析层（后续接入实时诊断）
``

## 开发

```bat
gradlew.bat :app:assembleDebug
gradlew.bat :app:installDebug   :: 装到已连接的手机
```

- SDK：`F:\AndroidSDK`（见 local.properties）
- 运行需要手机「USB 调试」已连接

## 开源依赖

- [sora-editor](https://github.com/Rosemoe/sora-editor) —— Android 代码编辑器组件，**LGPL-2.1**（见 `OPEN_SOURCE_NOTICES.md`）
```
## Windows 桌面端（`desktop` 模块）

与 Android 端共用的 Compose Multiplatform 跨平台实现（详见 [`desktop/README.md`](desktop/README.md)）：

- 共享 sublimation 语言引擎（frontend/compiler/interpreter/language-service 进程内直连）
- 共享 Android 端平台无关源码（主题、Markdown 渲染、诊断模型）与内置文档资产
- Compose 原生代码编辑器：真实 Lexer 语法高亮、LSP 实时诊断下划线与补全
- 运行：`.subs` 走解释器直连；工程编译后交 Node.js 执行（或解释器模式）
- `gradlew.bat :desktop:run` 开发运行；`gradlew.bat :desktop:createDistributable` 打包免安装 exe

