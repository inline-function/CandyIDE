# CandyIDE ProGuard 规则（Release 构建暂未启用混淆，先占位）

# sora-editor（LGPL-2.1）
-keep class io.github.rosemoe.sora.** { *; }

# Sublimation 编译器栈（Kotlin/JVM 库）
-keep class sugared.functor.** { *; }
