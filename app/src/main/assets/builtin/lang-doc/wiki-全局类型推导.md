# 全局类型推导（脚本模式）

> **定位**：脚本模式下省略类型/可变性/效应标注时，编译器**自动填入**「能过检查、符合所想」的标注。
> 设计文档为 `idea doc/全局类型推导.md`（权威），本文描述 V1 + V2 实现与用法。**默认关闭**，按需开启。

## 开启

```bat
java -jar sublimation.jar run-script demo.subs --global-inference
```

- 只作用于 `run-script`（脚本模式）；`compile`/`check`（编译模式）**不受影响**——标注必须显式（设计文档 §十二）。
- 解释器 API：`Interpreter(globalInference = true)`（`stdlibDir` 之外的第二个构造参数）。

```subl
// 开启后，下面整段脚本合法（全部省略标注）
struct Account(owner, balance, country = "中国")      // 字段：调用点/默认值推断

fun add(a, b) = a + b                                // 参数：调用点 + 算术使用点推断
fun greet(name) { print(name) }                      // 效应：调用 print → 自动 @impure
fun rec() = rec()                                    // 停机失败 → 自动 @impure（不再报错）

var acc = Account("张三", 1000)
acc.balance = acc.balance + 500                      // 可变性：赋值链 → @mut
print(toStr(add(1, 2)))
```

## 优先级

缺失标注时按以下顺序选择（设计文档 §三）：

```
1. 显式标注        ← 最高，用户写了就算数（已填类型/注解一律保留）
2. 构造点          ← 具体类型（"张三" → Str；调用点实参、默认值、结构体构造）
3. 使用点          ← 算术运算偏好（n + 1、n <= 1 → Nat）；成员偏好（n.length() → Str）
4. 默认            ← 兜底泛型化：无信息 → [T: Any]（每次调用独立实例化），算术无构造点 → [T: Num]
```

构造点 > 使用点的理由：构造点是「值的确切形状」，使用点是「对值的要求」——`1000` 是 Nat（构造点），
`balance >= 1000` 只要求 Num。Nat 更具体，优先（设计文档 §三 决策依据）。

## 各位置的规则

| 位置 | 规则 | 示例 |
|---|---|---|
| 变量 | 初值类型（Checker 本地推断，早已支持） | `var n = 0` → Nat |
| 函数参数 | 调用点实参字面量的唯一共同类型 → 算术/成员使用点 → 泛型化兜底 | `fun add(a, b)` 调 `add(1,2)` → a,b: Nat；`doubleIt(n) = n*2` → `[T: Num]` |
| 函数返回 | 体类型（字面量/参数/算术/已知返回；递归走 SCC 迭代，见下） | `fun f() = 1` → Nat |
| struct 字段 | 构造点（位置实参/命名构造 `A(x = 1)`/默认值）→ 字段级 `[T: Any]` | `struct Acc(owner, balance = 0)` 调 `Acc("z", 42)` → owner: Str, balance: Nat |
| 可变性 | 赋值目标链上的变量 + 字段 → @mut | `acc.balance = ...` → @mut var acc / @mut balance / @mut struct |
| 效应 | 体内调用非纯符号（内建 print 等 / stdlib @impure / 已推 impure 的脚本函数）→ @impure，向上冒泡 | `fun g() { print(...) }` → g @impure |
| 停机 | 自递归且实参不递减 → @impure（降级而非报错） | `fun rec() = rec()` 通过 |

动态语言风格的「赋值即声明」：推断是**承诺反推**（设计文档 §八）——脚本模式专属退化，编译模式不变。

## 显式标注与幂等

- **显式最高优先**：任何位置已写的标注（类型、@mut、@impure）都不被推断覆盖——推断只填空。
- **幂等**：重复运行同一脚本结果稳定（同输入推导出同输出）。
- **冲突**：约束无法同时满足时，V2 报专用诊断 `E-TYPE-CONFLICT` 并指出两个来源——
  构造点与使用点冲突（`fun bad(n) = n + 1` 被传 `1` 又传 `"s"`）、struct 字段多构造点不一致（`Mixed(1)` 又 `Mixed("s")`）、
  或参数同时被算术与 `.length()` 使用（§7.4）。纯多构造点但参数**无使用点**时**不报错**——每次调用独立实例化（见「泛型化」）。

## 递归函数

设计文档 §7.2/7.3：Tarjan SCC 分组（被调者先解）+ 3 轮「假设签名 → 验证」迭代，**未标注返回类型的
递归/互递归函数直接推出返回类型**，不再需要逃生舱：

```subl
fun myfact(n) = if (n <= 1) { 1 } else { n * myfact(n - 1) }        // 返回类型推出 Nat；myfact(5) = 120
fun myeven(n) = if (n == 0) { true } else { myodd(n - 1) }          // 互递归推出 Bool
fun myodd(n)  = if (n == 0) { false } else { myeven(n - 1) }
```

仍可显式标注（§7.6 逃生舱始终有效——标注永远优先）：`fun myfact(n): Nat = ...`。

## V2 新增

V2 实现了设计文档的剩余特性：

- **泛型化（§六）**：函数参数无构造点只有算术使用点 → `[T: Num]`，按调用点实例化
  （`fun doubleIt(n) = n * 2` 调 Nat/Rat 都行）；纯无使用点 → `[T: Any]`；多构造点无使用点同样 `[T: Any]`
  各自实例化；struct 字段无构造点 → 结构体级 `[T: Any]`。约束解析经由 stdlib 的 `class Num`（core/number）。
- **成员使用点（§四）**：`n.length()` 的接收者填具体 `Str`（返回 `Nat`）。
- **递归 SCC（§7.2/7.3）**：见上节。
- **上下文自动填充（§十）**：体内调用 `witness[@q]<q>` 这类带前件的函数 → 调用者自动提升预备式 `<q>`
  与命题参数 `@q`（合并不覆盖）；所有 `return` 处可证命题的交集提升为后件。
- **`D-AUTO-IMPURE`（§7.5）**：停机失败自动 `@impure` 时，以 `[提示]` 打印补充级诊断说明降级理由。
- **`E-TYPE-CONFLICT`（§十一）**：专用冲突诊断（见「显式标注与幂等」）。

**所有产物仍整体经 Checker**：推断只填不绕——如果推断出的标注让代码不合法，检查器照常报错
（这是设计文档的核心安全契约）。
