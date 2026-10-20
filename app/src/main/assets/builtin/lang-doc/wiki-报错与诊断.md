# 报错与诊断

编译器诊断分**四级**：错误（`E-`，阻断编译）、警告（`W-`，不阻断但提示风险）、提示（`H-`，改进建议）、
补充（`D-`，类型推导过程等，默认不输出）。全套输出由 `check` / `compile` 命令承担，
`explain <CODE>` 负责离线查码：**已收录 97 个诊断码**（与 `ExplainCoverageTest` 双向同步，不缺码也无幽灵码）。

## 源码片段上下文

每条带位置的诊断，输出时自动附带**源码行 + 行号 + 上箭头**，一眼定位（下面是真实输出）：

```
[错误][1:16] E-TYPE-MISMATCH: 函数 f 声明返回 Nat，但函数体是 Str，两边不一致
  1 | fun f(): Nat = "s"
    |                ^
```

四个分级标签：`[错误]` / `[警告]` / `[提示]` / `[补充]`，位置格式 `[行:列]`。

## `explain <CODE>` 子命令

不记得某个诊断码是什么意思？`explain` 直接给出含义与修复建议（不区分大小写）：

```
java -jar sublimation.jar explain W-UNUSED
# W-UNUSED — 声明后从没用过。—— 变量/参数 `xxx` 声明了但哪个地方都没用到。
# 删掉它，或者改名加 `_` 前缀表示故意不用。

java -jar sublimation.jar explain W-UNUSED      # 大小写随意：w-unused 也行
```

未收录的码会列出全部现有码，方便翻阅：

```
java -jar sublimation.jar explain E-NOPE
# [错误] 未收录诊断码 E-NOPE（现有：D-AUTO-AWAIT / D-BECAUSE-PROVED / … / W-UNUSED）
```

## `-v`：显示补充级

默认只输出错误/警告/提示。加 `-v` 后补充级（类型推导过程）一并输出：

```
[补充][3:5] D-NAT-PROP: 注入自然命题：`n ≥ 0`（Nat 变量自动成立）
  3 |     var n = 42
    |     ^
[补充][3:5] D-TYPE-INFERRED: var n 推导为 Nat
[补充][4:5] D-TYPE-INFERRED: var s 推导为 Str
```

`compile` 的诊断同样支持 `-v`；诊断先出，JS 产物随后。

## 输出与退出

- 诊断文本打在**标准输出**；检查未通过时最后一行 `[错误] 语义检查未通过` 打在**标准错误**，进程非零退出；
- 通过时 `check` 打印 `[完成] 语义检查通过`（目录模式为 `[完成] 模块检查通过`）；
`compile` 则打印 `[完成] 已生成 out.js（N 字节）` 或把 JS 直接打到标准输出（不给 `-o` 时）。

## 多模块（目录）编译的注意点

- 挂进来参与编译的 `core` 标准库会露出它自己的提示和警告（如 `H-ROOT-BUILTIN`、`W-UNUSED`）；
  它们不阻断编译，忽略即可；
- 补充级诊断只保留**根模块**的（其余模块的推导过程被视为噪声，自动过滤）；
- 模块层错误（挂载目标缺失、settings 语法、挂载成环）在树校验阶段最先报出来。

## 常用诊断码速查

按场景分组；每条都能用 `explain <码>` 查到完整解释。

### 类型

| 码 | 典型场景 |
|---|---|
| `E-TYPE-MISMATCH` | 表达式类型与预期不符（返回类型/实参/标注） |
| `E-KIND-MISMATCH` | 类型构造子缺类型参数（`List` 必须写 `List[Nat]`） |
| `E-TYPE-MISSING` / `E-ANY-CALL` / `E-ANY-STRUCT` | 推导不出具体类型，需手动标注 |
| `E-TUPLE-DESTRUCT` / `E-TUPLE-ARITY` | 元组解构目标不是元组、分量数不对 |

### 名字与作用域

| 码 | 典型场景 |
|---|---|
| `E-UNBOUND-NAME` | 未定义名字（拼写、作用域、或没 `mount` 它所在的模块） |
| `E-DUP-DECL` | 同模块重复声明；多个模块声明 `main` |
| `E-FUTURE-FUN` | 前向引用尚未定义的函数 |
| `E-MAIN-PARAMS` | `fun main()` 带了形参或类型参数 |

### 函数与控制流

| 码 | 典型场景 |
|---|---|
| `E-MISSING-RETURN` | 块体函数必须显式 `return`（想省事用表达式体 `= 值`） |
| `E-RETURN-TYPE` / `E-RETURN-OUTSIDE` | return 类型不符 / return 出现在非法位置 |
| `E-ARITY` / `E-NAMED-ARG` | 实参个数 / 命名实参冲突 |
| `E-DEFAULT-PARAM` | 函数形参带默认值（只有 struct 字段支持默认值） |
| `W-UNREACHABLE` / `W-DIV-ZERO` | return 后的代码 / 字面量除以零 |

### 模式匹配（when）

| 码 | 典型场景 |
|---|---|
| `W-NON-EXHAUSTIVE` | 分支没盖全（作为表达式时值类型退化为 Null） |
| `E-NON-SEALED-MATCH` | 非密封 when 的值流向了非 Null 上下文 |
| `H-ARITH-PRECEDENCE` | 混用不同优先级的运算符（语言不做优先级，一律左结合） |

实测例子（缺 `None` 分支）：

```
[警告][1:32] W-NON-EXHAUSTIVE: when 非穷尽（缺 else 或覆盖不全），作为表达式时值类型退化为 Null
[错误][1:32] E-NON-SEALED-MATCH: 函数 g 的返回值来自 when 表达式，但分支不穷尽
  1 | fun g(o: Optional[Nat]): Nat = when(o) { is Some(v) => v }
    |                                ^
```

### 纯度与可变性

| 码 | 典型场景 |
|---|---|
| `E-IMPURE-CALL` | 纯函数里调了有副作用的函数 |
| `E-IMMUT-ASSIGN` / `E-ASSIGN-TARGET` | 给不可变变量赋值 / 赋值目标不是变量 |
| `E-NOT-MUT` | 修改字段需要可变视图（`@mut` 字段 + `@mut T` 视图） |
| `E-IMPURE-REQUIRED` | 参数带 `@mut T`，函数必须标 `@impure` |
| `E-NON-STRUCTURAL-REC` | 纯函数递归停止性存疑（参数没有在变小） |
| `D-DEEP-COPY` | 绑定/赋值涉及 `@mut` 类型时发生了深拷贝 |

### 类型类

| 码 | 典型场景 |
|---|---|
| `E-NO-INSTANCE` | 类型没有实现对应类型类（写一个 `impl … for …`） |
| `E-IMPL-INCOMPLETE` / `E-IMPL-MISMATCH` / `E-IMPL-EXTRA` | impl 缺方法 / 签名不符 / 多了方法（除非 `@new`） |
| `E-AMBIGUOUS-INSTANCE` | 同一方法多个 impl 分不清用哪个 |
| `E-NO-METHOD` / `E-NO-SELF` | 类型上找不到方法 / 方法调用缺接收者 |

### 线性作用域（`%` 块）

| 码 | 典型场景 |
|---|---|
| `E-LINEAR-ESCAPE` | 域里 lambda 捕获并修改了 `@mut` 变量（唯一所有权被破坏） |
| `E-LINEAR-ALIAS` | 域里声明了 `@ref` 变量（别名语义与唯一所有权冲突） |
| `E-LINEAR-RETURN` | 域里带值提前返回（版本化收尾统一在域出口） |
| `D-LINEAR-VERSION` | `@mut` 变量赋值前进到新版本，等式事实进 Γ（`-v` 可见） |
### 命题与证明

| 码 | 典型场景 |
|---|---|
| `E-PRE-UNPROVEN` | 调用时上文在 Γ 里证不出 |
| `E-POST-UNPROVEN` | 函数体结束时下文不成立 |
| `W-CANNOT-PROVE` | `∵/∴/+∵` 里的命题在当前事实下推不出（不阻断） |
| `E-DEEP-SATURATION` / `E-THEOREM-SEARCH-DEPTH` | 推理太深，把大结论拆成小步骤 |
| `E-PROP-UNBOUND` / `E-PROP-AMBIGUOUS` / `E-QUANT-UNPROVEN` | 前提不在事实里 / 命题有歧义 / `∃` 实例不满足 |

### 模块与注解处理器

- 模块：`E-PARSE-SETTINGS`、`E-CIRCULAR-MOUNT`、以及挂载目标缺失时的 `E-UNBOUND-NAME` —— 见 [模块系统](模块系统)；
- 注解处理器：`E-PROCESSOR-SCRIPT`/`E-PROCESSOR-ERROR`/`E-PROCESSOR-LOOP`/`E-PROCESSOR-EDIT`/`W-PROCESSOR-SCRIPT`/`W-PROCESSOR-EDIT` —— 见 [注解处理器](注解处理器)。

### 词法与语法

| 码 | 典型场景 |
|---|---|
| `E-LEX` | 不认识的字符/token（全角标点、未闭合的字符串等） |
| `E-PARSE` | 不符合文法（缺括号、缺关键字、语句/声明位置错） |
| `E-CHECK` | 检查器内部异常——编译器 bug，请保留源码反馈 |

## IDE（语言服务）里的诊断

编译器码不只命令行用——`language-service` 模块的 LSP 把同一批诊断原样推给 IDE（实时的 `publishDiagnostics`），并多做三件事：

**悬停看说明**：每条诊断都挂了对应 code 的中文说明（`Diagnostic.documentation`），覆盖全部 97 个码，原因与修法一眼可见，不用回头查 `explain`。

**灯泡快速修正**（codeAction，按诊断 code 一键一操作）：

| 触发码 | 修正动作 |
|---|---|
| `E-NEED-ASYNC` | 给所在函数加 `@async` 注解 |
| `E-NAMED-ARG` | 方法糖 `.f(x: 1)` 改写成自由函数 `f(接收者, x: 1)` |
| `E-ARITY` | 删掉调用中超出形参个数的实参 |
| `E-IMPL-INCOMPLETE` | 在 impl 块末尾为缺失方法补 `fun m(签名): R = ???` 存根 |

另有全角→半角标点修正（输入法切错时的兜底）。重命名、转到定义、引用查找、参数提示（signatureHelp）、按 Enter 的智能缩进与文档格式化另有专门路由，不在诊断码体系内。

**注解补全**：输入 `@` 或 `@as` 前缀即提示内建注解（`async` / `impure` / `mut` / `list` / `ref` / `root`），含中文说明——详见 [注解处理器](注解处理器) 与 [副作用与可变性控制](副作用与可变性控制)。

> 位置口径：诊断的 `[行:列]` 与 LSP position 都是相对**用户文件**的；挂进来的 `core` 标准库自身的诊断（行号必然超出用户文件）会被语言服务过滤掉，不推给 IDE——这一点与命令行的多模块行为不同（命令行里 core 的提示会露出来，见下节）。
## 排错流程建议

1. 读带箭头的源码行，先修第一个错误（后面的错误常是它的连锁反应）；
2. 看不懂码 → `explain <CODE>`；
3. 想看推导过程 → 加 `-v`；
4. 涉及跨模块名字 → 先查 `mount` 与模块可见性（[模块系统](模块系统)）；
5. 仍怀疑是编译器问题 → 用 `parse` / `tokens` 单独看语法树与 token 流定位。