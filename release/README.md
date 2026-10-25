# 发布产物

## Android —— candy-ide-2.0.0-debug.apk
debug 签名，可直接安装运行（目标 SDK 35 / minSdk 26）。

## Windows —— 桌面端
完整的 Windows app-image（含 CandyIDE.exe 与运行时，约 180MB）因超过 GitHub 单文件 100MB 限制，
未随仓库提交。本地构建一行命令即可复现：

```powershell
$env:JAVA_HOME = '<JDK17 路径>'
.\gradlew :desktop:createDistributable
```

产物：desktop/build/compose/binaries/main/app/CandyIDE.exe（免安装，解压即用）。
配置仅 AppImage（不含 WiX/MSI），无需安装额外工具链。
