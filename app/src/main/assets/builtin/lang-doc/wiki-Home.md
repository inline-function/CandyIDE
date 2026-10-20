# Sublimation 快速上手

**Sublimation（凝华）**是一门带自动证明的静态类型语言：类型里的命题可以交给编译器自动证明，证明通过才给编译，失败即编译错误。语法像 Kotlin，编译到 JavaScript，node 直接跑；也自带解释器脚本模式，顶层语句即程序。

> 当前版本 **v4 终版**（编译器/解释器测试 733 项全绿）。本文所有片段均已用 `build/libs/sublimation.jar` 实测通过。

## 安装

```bat
gradlew.bat fatJar                              # 编译出 build/libs/sublimation.jar
java -jar build/libs/sublimation.jar compile MY_PROJECT -o out.js
node out.js
```

仅依赖 java（JRE 17+）；编译模式另需 node。

## Hello World

```subl
@impure                            // main 有副作用，标出来
fun main() {
    print("Hello, Sublimation!")
}
```

## 变量与基本类型

```subl
var n = 42                          // Nat（自然数，n≥0）
var x: Int = -3                     // Int（整数）
var s = "hello"                     // Str
var b = true                        // Bool
```

基本类型：`Nat` `Int` `Rat` `Str` `Bool` `Null`，以及容器 `List[T]` `Set[T]` `Map[K,V]` `Optional[T]` `Result[T,E]`、元组 `(A,B)`（容器详见《容器》，都在标准库 `core/` 里）。

## 函数

```subl
fun add(a: Nat, b: Nat): Nat = a + b            // 纯函数（默认，可以写表达式体）
fun greet(name: Str): Str {                       // 块体
    return "Hello, " + name
}
```

## 脚本省标注：全局类型推导

脚本（`run-script`）加 `--global-inference` 可省掉类型/可变性/效应标注——编译器按
「显式 > 构造点 > 使用点 > 默认」填充，详见 [全局类型推导](全局类型推导)：

```bat
java -jar sublimation.jar run-script demo.subs --global-inference
```

```subl
struct Account(owner, balance = 0)      // 字段类型自动推断
fun add(a, b) = a + b                    // 参数类型自动推断
var acc = Account("张三", 1000)
acc.balance = acc.balance + 500          // @mut 自动推断
```

## 激进证明模式：--prover=aggressive

默认的自动证明有预算上限（轮数/深度封顶）——快，但长链和经典逻辑推演证不动。`compile`/`check` 加 `--prover=aggressive` 拆掉上限：饱和到不动点、排中律/反证/∨ 消去（需 `@classic`）、∀ 三维实例化；健全性底线不变（不猜、不降公理），Ctrl-C 中断并生成诊断报告。详见 [激进模式](激进模式)：

```bat
java -jar sublimation.jar compile MY_PROJECT --prover=aggressive
```

## 类型别名

给类型起个短名（决策 30），泛型别名还能带类型参数头：

```subl
type MyNat = Nat                        // 简单别名
type NatOpt = Optional[Nat]             // 容器别名
type Box[a] = Optional[a]               // 泛型头：Box[Nat] 展开为 Optional[Nat]
type Dict[k, v] = Map[k, v]             // 多参数泛型头
```

## 自动证明（上下文系统）

Sublimation 的与众不同之处：函数可以**携带命题**，编译器帮你自动证明。

```subl
fun capped(score: Nat)<score ≤ 100>: Nat = score   // <score ≤ 100> 是"上文"：调用前必须满足
```

调用 `capped(50)` 自动证明 `50 ≤ 100`（常量折叠）——通过。
调用 `capped(200)` 证明 `200 ≤ 100` 失败 → **编译错误**（`E-PRE-UNPROVEN`）。

命题算符用 Unicode 符号：`≤` `≥` `≠` `∈` `∨` `∧`（写 `<=` 不会被当作命题比较符）。

### 因为/所以

```subl
∵ 3 >= 0                // "因为"：求证后把事实注入上下文
∴ 3 >= 0                // "所以"：求证，证不出即编译错误
```

### 区间穷尽检查

```subl
fun grade(score: Nat)<score ≤ 30>: Str =
    when(score in _) {          // 区间模板匹配——编译器证明三个分支覆盖了 (0..30)
        (0 .. 10)  => "低"
        (11 .. 20) => "中"
        (21 .. 30) => "高"
    }
```

## 模式匹配（when）

```subl
when (n) {
    0       => print("零")
    m if m > 10 => print("大")
    else    => print("其他")
}

// 无主题条件 when（没有 subject）：
when {
    n < 0  => print("负")
    n == 0 => print("零")
    n > 0  => print("正")
}

// ifis 智能解构（if 条件里的模式匹配，等同 Rust if let）：
fun first(o: Optional[Nat]): Nat {
    if (o is Some(v)) { return v }   // 匹配则解构 v 并提前返回
    return 0
}
```

> v4 起 when 分支分隔符固定为 `=>`（旧 `->` 已废除）；lambda 的 `->`/`=>` 都接受，**文档统一用 `=>`**。

## Optional 容器

没有 `null` 裸奔——可能缺失的值用 `Optional[T]` 显式表示，解构取值：

```subl
var m: Optional[Nat] = Some(42)
var v = when (m) { is Some(x) => x; None => 0 }   // 解构取值
var w = if (m is Some(x)) x else 0                // 只取一种形状时用 ifis
```

## 结构体与类型类

```subl
struct User(name: Str, age: Nat)

class Show {
    fun show(): Str
}
impl Show for User {
    fun show(): Str = "{ name=" + self.name + ", age=" + toStr(self.age) + " }"
}
var u = User("Alice", 25)
print(u.show())                          // { name=Alice, age=25 }
```

## 纯度控制

函数默认**纯**（无副作用）。有副作用标 `@impure`；就地改参数/变量标 `@mut`；拿不准的逃逸用 `unchecked`：

```subl
fun pure(x: Nat): Nat = x + 1           // 默认纯

@impure
fun log(msg: Str) { print(msg) }        // 副作用

@impure
fun main() {
    @mut var counter = 0                // 可变变量需标 @mut
    counter = counter + 1
}
```

## 容器

```subl
var xs = listOf(1, 2, 3)
xs.map({ x => x + 1 })          // [2, 3, 4]
xs.filter({ x => x > 1 })       // [2, 3]
xs.fold(0) { acc, x => acc + x }   // 6（尾随 lambda）

var m = toMap(listOf(("a", 1), ("b", 2)))   // 不可变 Map
m.get("a")                              // Some(1)
```

## 目录即模块

每个目录 = 一个模块，同目录所有 `.subl` 共享符号表；`core/` 标准库包**隐式挂载**（裸名直接用），其他模块在 `<模块名>.settings` 里用 `mount` 声明：

```
MY_PROJECT/
  main.subl          ← 根模块（入口）
  core/              ← 标准库包（自动挂载：math/collection/string/logic/io… 子包）
  users/
    users.subl       ← 子模块
    users.settings   ← mount 其他模块
```

## 命令行

```
parse <file.subl>        词法+语法分析，打印语法树
tokens <file.subl>       打印 token 流（含邻接标记）
check <file.subl>        语义分析（弥散/证明义务/纯度/可变性/impl/when 穷尽）
check <file.subl> -v     同时输出补充级诊断（类型推导结果等）
compile <file.subl> [-o out.js] [-p proc.subs|dir]   语义通过后生成 JS
run-script <file|dir>    脚本模式解释执行：顶层语句直接运行（无需 main）
explain <CODE>           打印诊断码的含义与修复建议（如 W-NON-EXHAUSTIVE）
```

- `compile . -p apt`：编译整个目录，并附加注解处理器（`.subs` 脚本，目录装载其全部文件）。
- 想直接看效果？仓库自带两个自包含演示：`show/编译器`（compile → node）与 `show/解释器`（run-script），各执行目录里的 `run.bat` 即可。
- 单文件 `check` 不挂标准库——用到 List/Set/Map/Result 的片段要么放进带 `core/` 副本的目录，要么用 `run-script` 脚本模式跑。

## 下一步

→ [语言样式规范](语言样式规范) — 写出可读的 Sublimation 代码
→ [上下文系统](上下文系统) — 自动证明深入
→ [模式匹配](模式匹配) — when、when-in、穷尽性
→ [结构体与类型类](结构体与类型类) — struct / class / impl
→ [副作用与可变性控制](副作用与可变性控制) — 纯度纪律
→ [空安全](空安全) — Optional 使用指南
→ [容器](容器) — List / Set / Map / Optional / Result
→ [模块系统](模块系统) — 目录即模块、mount 与 core
→ [异步](异步) — Task / Channel / Flow / Mutex
