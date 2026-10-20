# 模式匹配

凝华的 `when` 是真模式匹配——支持构造子解构、嵌套、守卫、区间模板，且编译器能**自动证明穷尽性**。

> v4 终版起，when 分支分隔符固定为 `=>`（旧 `->` 已废除，解析器不再接受）。

## 基本用法

```subl
when (n) {
    0       => print("零")           // 字面量
    m if m > 10 => print("大数")     // 带守卫的变量绑定
    else    => print("其他")          // 兜底分支
}
```

分支体有两种写法：单表达式（`=> expr`），或语句块（`=> { … }`，块内可提前 `return`，见《穷尽性》一节末）。

> `when` 应尽量穷尽所有可能值；判不出穷尽会发 `W-NON-EXHAUSTIVE` 警告，作为表达式的值类型退化为 `Null`。

## 构造子解构

```subl
enum Color { Red, Green, Blue }
when (color) {
    Red   => print("红")
    Green => print("绿")
    Blue  => print("蓝")        // 枚举必然穷尽，不需要 else
}
```

对 `Optional[T]` 解构：

```subl
when (maybe) {
    is Some(v) => print(v)       // 绑定 v，作用域在分支内
    None       => print("缺值")
}
```

> 规则：**解构（带绑定/嵌套）必须显式 `is`**——写 `Some(v)` 而不带 `is` 只允许无参值比较（`A()`）。枚举成员路径写法 `Color::Red` 同样可用。

嵌套构造子：

```subl
when (result) {
    is Ok(is Some(v)) => print(v)        // 两层构造子同时解构
    is Ok(None)      => print("空")
    is Err(e)        => print("错: " + e)
}
```

元组模式：

```subl
fun firstOf(p: (Nat, Nat)): Nat = when(p) {
    (a, b) => a
    else => 0
}
```

## 区间穷尽（when(n in _)）

这是凝华的杀手特性：把区间分支交给编译器证明覆盖。

```subl
fun grade(score: Nat)<score ≤ 100>: Str =
    when(score in _) {                        // _ = 从上下文推断区间
        (0 .. 59)  => "不及格"
        (60 .. 79) => "及格"
        (80 .. 100) => "优秀"
    }
```

编译器从上下文的 `score ≤ 100` 与 `Nat` 自带的 `score ≥ 0` 组合出全域 `(0..100)`，再收集每个分支的端点（0, 59, 60, 79, …），排序后检查相邻衔接。分支覆盖了全域，**无需 else 也不需要自己写边界检查**。

```subl
// 等价写法：无主题 when + 显式 in 条件
fun grade2(score: Nat)<score ≤ 100>: Str = when {
    score in (0 .. 59)  => "不及格"
    score in (60 .. 79) => "及格"
    score in (80 .. 100) => "优秀"
}
```

> 两点提醒：
> - 全域上界要写进**命题**（`<score ≤ 100>`），用 Unicode `≤`；命题算符不接受 `<=`。
> - 无主题 when 的区间穷尽判定与之同源，前提同样是 Γ 里有全域上下文（函数上文或结构体伴生命题）。

## 分类讨论命题语句 when[p ∨ q]

值层 `when` 之外还有一条**命题层**语句：已知一个析取式 `p ∨ q` 时，按各支分类讨论，提取公共结论。

```subl
unchecked.axiom[n ≤ 10 ∨ n > 10]        // 先把析取式注入 Γ
when[n ≤ 10 ∨ n > 10] {
    n ≤ 10 => { ∴ n ≥ 0 }
    n > 10 => { ∴ n ≥ 0 }
}
∴ n ≥ 0                                  // 两支共同结论，注入 Γ
```

- 分支 = 条件 + 语句块（`p => { … }`），**没有 else 臂**。
- 析取前提不在 Γ 里发 `W-PROP-UNBOUND`；分支没有公共结论发 `W-WHEN-NO-COMMON`。
- 这是手动证明（已封存专题）的产物：脚本模式（`run-script`）里可用，值层程序一般用不到。

## ifis 智能解构（if 条件里的模式匹配）

只关心**一种**形状、只取**一层**值时，不必写完整 `when`——像 Rust 的 `if let` 一样，把模式条件直接写进 `if`：

```subl
fun describe(o: Optional[Nat]): Nat =
    if (o is Some(e)) e else 0      // 匹配 Some 则进入分支，e 绑定内部值

fun first(o: Optional[Nat]): Nat {
    if (o is Some(e)) { return e }  // 提前 return 照常退出函数
    return 0
}
```

- 模式与 `when` 分支**完全同构**：构造子解构（`is Some(e)` / 结构体 `is Point(x, y)` / 路径 `is E::A(x)`）、嵌套 `is Some(is Some(v))`、通配 `_`、`as` 绑定都能用。
- 语义是脱糖成 `when`：`if (o is Some(e)) A else B` ≡ `when (o) { is Some(e) => A; else => B }`，因此穷尽性提示、分支命题与智能转换规则全部复用。
- 不带 `else` 时与普通 `if` 无 else 同义（值退化）；枚举主题会按既有规则给 `W-NON-EXHAUSTIVE` 提示——补 `else` 或按需忽略。
- 条件必须是**单独的模式判断**：`if (o is Some(e) && e > 0)` 不被接受（绑定的 e 也没有作用域），组合条件请改用 `when` 分支守卫。
- 普通布尔条件 `if (c)`、类型测 `if (x is Nat)`（分支内智能转换）不受影响。

## 无主题 when（条件分支）

没有 subject 的 `when` 按条件链执行，不需要 else：

```subl
when {
    n < 0  => print("负")
    n == 0 => print("零")
    n > 0  => print("正")
}
```

## 守卫（if 条件）

```subl
when (n) {
    x if x > 100 => print("大")        // 守卫是布尔表达式
    x if x > 0   => print("正")
    else         => print("非正")
}
```

守卫位是**值层**的，不要用命题算符 `∧`/`∨`。多条件组合用 `&`（代码生成为 JS 的 &&），比较写成前缀调用：

```subl
when (n) {
    0 => print("zero")
    m if !=(m, 0) & !=(m, 1) => print("many")     // 前缀调用 + &
    else => print("small")
}
```

## 穷尽性

凝华的穷尽性是**提示不是要求**：`when` 判不出穷尽时按默认值 `Null` 处理，只有主题有覆盖律（名义枚举）却覆盖不全才发 `W-NON-EXHAUSTIVE`。枚举、构造子族、区间模板——编译器有专门的穷尽策略，大多数场景不需要手动写 `else`。

但 **`when` 作为表达式的值流向非 Null 上下文时会报错**（`E-NON-SEALED-MATCH`）：

```subl
// ❌ 编译错误：返回值来自非穷尽 when（值可能为 Null）
fun name(c: Color): Str = when(c) {
    Red => "红"
}

// ✅ 穷尽：枚举三构造子补齐
fun name(c: Color): Str = when(c) {
    Red   => "红"
    Green => "绿"
    Blue  => "蓝"
}
```

分支块里提前 `return` 要求 `when` 处于**语句位**（不能是 var 初值、实参等值位）。作为块尾表达式时还需补 `else` 兜底且全分支 return——`when` 判作发散（diverges）才按语句流处理；位于语句中间则无此限制：

```subl
fun classify(n: Nat): Nat {
    when {
        n < 0  => { return 0 }
        else   => { return n }
    }
}
```

```subl
// 诊断码含义随手查：
// $ java -jar sublimation.jar explain W-NON-EXHAUSTIVE
```
